package me.rerere.ai.provider.providers.ninerouter

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.workspace.WorkspaceManager
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.uuid.Uuid

private const val TAG = "NineRouterService"

const val NINEROUTER_PORT = 20128
const val NINEROUTER_BASE_URL = "http://127.0.0.1:20128/v1"
const val NINEROUTER_DEFAULT_HOST = "127.0.0.1"

enum class NineRouterStatus {
    ONLINE,
    STOPPED,
    NOT_INSTALLED
}

@Serializable
data class NineRouterState(
    val status: NineRouterStatus = NineRouterStatus.NOT_INSTALLED,
    val pids: List<Int> = emptyList(),
    val ramMb: Int? = null,
    val currentVersion: String? = null,
    val latestVersion: String? = null,
    val hasUpdate: Boolean = false,
)

@Serializable
data class NineRouterRequestLog(
    val id: Long,
    val timestamp: String,
    val provider: String,
    val model: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val status: String,
    val latencyMs: Long? = null,
)

@Serializable
data class NineRouterQuotaData(
    val connectionName: String = "Antigravity",
    val accountEmail: String? = null,
    val fiveHourUsedPercent: Float = 0f,
    val fiveHourRemainingPercent: Float = 100f,
    val fiveHourResetCountdown: String = "Ready",
    val weeklyUsedPercent: Float = 0f,
    val weeklyHeadroomPercent: Float = 100f,
    val weeklyResetCycle: String = "Weekly cycle",
    val geminiStatus: String = "ACTIVE",
    val geminiRequestsInWindow: Int = 0,
    val geminiMaxAllowed: Int = 500,
    val claudeStatus: String = "ACTIVE",
    val claudeRequestsInWindow: Int = 0,
    val claudeBurstHeadroomPercent: Float = 100f,
    val claudeRateLimit: String = "Normal",
    val dailyRequestsUsed: Int = 0,
    val dailyRequestsCap: Int = 1000,
    val dailyRequestsRemaining: Int = 1000,
)

class NineRouterService(
    private val workspaceManager: WorkspaceManager,
    private val client: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val fastProbeClient by lazy {
        client.newBuilder()
            .connectTimeout(500, TimeUnit.MILLISECONDS)
            .readTimeout(500, TimeUnit.MILLISECONDS)
            .callTimeout(800, TimeUnit.MILLISECONDS)
            .build()
    }

    fun isNineRouter(provider: ProviderSetting?): Boolean {
        if (provider == null) return false
        if (provider is ProviderSetting.NineRouter) return true
        if (provider is ProviderSetting.OpenAI) {
            val url = provider.baseUrl.lowercase()
            if (url.contains(":20128") || provider.name.equals("9Router", ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    suspend fun isOnline(baseUrl: String = NINEROUTER_BASE_URL): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$baseUrl/models")
                .get()
                .build()
            fastProbeClient.newCall(req).execute().use { response ->
                response.isSuccessful
            }
        }.getOrDefault(false)
    }

    suspend fun checkStatus(): NineRouterState = withContext(Dispatchers.IO) {
        val online = isOnline()
        if (online) {
            var currentVer: String? = null
            var latestVer: String? = null
            var hasUpdate = false
            var pids = emptyList<Int>()
            var ramMb: Int? = null

            // Read version from /api/version
            runCatching {
                val req = Request.Builder()
                    .url("http://127.0.0.1:$NINEROUTER_PORT/api/version")
                    .get()
                    .build()
                fastProbeClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val parsed = json.parseToJsonElement(body).jsonObject
                        currentVer = parsed["currentVersion"]?.jsonPrimitive?.content
                        latestVer = parsed["latestVersion"]?.jsonPrimitive?.content
                        hasUpdate = parsed["hasUpdate"]?.jsonPrimitive?.booleanOrNull == true
                    }
                }
            }

            // Probe PID and RAM
            val root = resolveRoot()
            if (root != null) {
                runCatching {
                    val statusRes = workspaceManager.executeCommand(
                        root = root,
                        command = "/usr/local/bin/9router-status",
                        timeoutMillis = 2000L,
                    )
                    val out = statusRes.stdout
                    // Look for: server: RUNNING (pid 1234, 150 MB RAM)
                    val pidMatch = Regex("""pid\s+(\d+)""").find(out)
                    if (pidMatch != null) {
                        pidMatch.groupValues.getOrNull(1)?.toIntOrNull()?.let {
                            pids = listOf(it)
                        }
                    }
                    val ramMatch = Regex("""(\d+)\s+MB\s+RAM""").find(out)
                    if (ramMatch != null) {
                        ramMb = ramMatch.groupValues.getOrNull(1)?.toIntOrNull()
                    }
                }
            }

            return@withContext NineRouterState(
                status = NineRouterStatus.ONLINE,
                pids = pids,
                ramMb = ramMb,
                currentVersion = currentVer,
                latestVersion = latestVer,
                hasUpdate = hasUpdate,
            )
        }

        // Filesystem probe
        val root = resolveRoot()
        if (root != null) {
            val scriptExists = runCatching {
                val location = workspaceManager.resolveRootfsPath(root, "/usr/local/bin/9router-start")
                File(location.rootDir, location.relativePath).exists()
            }.getOrDefault(false)

            if (scriptExists) {
                return@withContext NineRouterState(status = NineRouterStatus.STOPPED)
            }
        }

        NineRouterState(status = NineRouterStatus.NOT_INSTALLED)
    }

    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        val root = resolveRoot() ?: error("No workspace rootfs found to start 9Router")
        Log.i(TAG, "Starting 9Router background process in root: $root")
        workspaceManager.startBackground(root, "/usr/local/bin/9router-start")

        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 15_000) {
            delay(500)
            if (isOnline()) {
                Log.i(TAG, "9Router successfully booted and is online")
                return@withContext true
            }
        }

        if (!isOnline()) {
            error("9Router failed to boot within 15 seconds")
        }
        true
    }

    suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        val root = resolveRoot() ?: error("No workspace rootfs found to stop 9Router")
        Log.i(TAG, "Stopping 9Router in root: $root")
        val res = workspaceManager.executeCommand(root, "/usr/local/bin/9router-stop", timeoutMillis = 8000L)
        res.exitCode == 0
    }

    suspend fun update(): Boolean = withContext(Dispatchers.IO) {
        val root = resolveRoot() ?: error("No workspace rootfs found to update 9Router")
        Log.i(TAG, "Updating 9Router in root: $root")
        val res = workspaceManager.executeCommand(root, "/usr/local/bin/9router-update", timeoutMillis = 180_000L)
        if (!isOnline()) {
            runCatching { start() }
        }
        res.exitCode == 0
    }

    suspend fun checkUpdate(): Pair<String?, String?> = withContext(Dispatchers.IO) {
        // Try online endpoint first
        val onlineState = checkStatus()
        if (onlineState.currentVersion != null && onlineState.latestVersion != null) {
            return@withContext (onlineState.currentVersion to onlineState.latestVersion)
        }

        // Try offline check via npm view
        val root = resolveRoot() ?: return@withContext (null to null)
        val curRes = workspaceManager.executeCommand(root, "9router --version", timeoutMillis = 5000L)
        val current = curRes.stdout.lines().firstOrNull()?.trim()?.removePrefix("v")?.takeIf { it.isNotBlank() }

        val latestRes = workspaceManager.executeCommand(root, "npm view 9router version", timeoutMillis = 8000L)
        val latest = latestRes.stdout.lines().firstOrNull()?.trim()?.takeIf { it.isNotBlank() }

        current to latest
    }

    suspend fun fetchModels(baseUrl: String = NINEROUTER_BASE_URL): List<Model> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$baseUrl/models")
            .get()
            .build()

        val resp = client.newCall(req).execute()
        val bodyStr = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) {
            error("Failed to fetch 9Router models: ${resp.code} $bodyStr")
        }

        val jsonEl = json.parseToJsonElement(bodyStr).jsonObject
        val dataArray = jsonEl["data"]?.jsonArray ?: return@withContext emptyList()

        dataArray.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val ownedBy = obj["owned_by"]?.jsonPrimitive?.content

            val upstreamTag = resolveUpstreamTag(id, ownedBy)
            val cleanName = if (id.contains("/")) id.substringAfter("/") else id
            val displayName = "[$upstreamTag] $cleanName"

            val caps = obj["capabilities"]?.jsonObject
            val vision = caps?.get("vision")?.jsonPrimitive?.booleanOrNull == true
            val tools = caps?.get("tools")?.jsonPrimitive?.booleanOrNull != false // default true for 9router
            val reasoning = caps?.get("reasoning")?.jsonPrimitive?.booleanOrNull == true
            val imageOutput = caps?.get("imageOutput")?.jsonPrimitive?.booleanOrNull == true

            val inputModalities = buildList {
                add(Modality.TEXT)
                if (vision) add(Modality.IMAGE)
            }
            val outputModalities = buildList {
                add(Modality.TEXT)
                if (imageOutput) add(Modality.IMAGE)
            }
            val abilities = buildList {
                if (tools) add(ModelAbility.TOOL)
                if (reasoning) add(ModelAbility.REASONING)
            }

            Model(
                id = Uuid.random(),
                modelId = id,
                displayName = displayName,
                type = if (imageOutput) ModelType.IMAGE else ModelType.CHAT,
                inputModalities = inputModalities,
                outputModalities = outputModalities,
                abilities = abilities,
            )
        }
    }

    suspend fun fetchRecentRequests(limit: Int = 10): List<NineRouterRequestLog> = withContext(Dispatchers.IO) {
        val root = resolveRoot() ?: return@withContext emptyList()
        val pyScript = """
import sqlite3, json
try:
    con = sqlite3.connect('/root/.9router/db/data.sqlite')
    cur = con.cursor()
    cur.execute('''SELECT id, timestamp, provider, model, promptTokens, completionTokens, status, meta
                   FROM usageHistory ORDER BY id DESC LIMIT $limit''')
    rows = cur.fetchall()
    out = []
    for r in rows:
        lat = None
        if r[7]:
            try:
                m = json.loads(r[7])
                lat = m.get('latencyMs') or m.get('duration') or m.get('latency')
            except:
                pass
        out.append({
            'id': r[0],
            'timestamp': r[1],
            'provider': r[2] or '',
            'model': r[3] or '',
            'promptTokens': r[4] or 0,
            'completionTokens': r[5] or 0,
            'status': r[6] or '',
            'latencyMs': lat
        })
    print(json.dumps(out))
except Exception as e:
    print('[]')
""".trimIndent()

        val res = workspaceManager.executeCommand(root, "python3 -c \"$pyScript\"", timeoutMillis = 3000L)
        val outJson = res.stdout.trim()
        if (outJson.startsWith("[")) {
            runCatching {
                val array = json.parseToJsonElement(outJson).jsonArray
                array.map { item ->
                    val o = item.jsonObject
                    NineRouterRequestLog(
                        id = o["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                        timestamp = o["timestamp"]?.jsonPrimitive?.content.orEmpty(),
                        provider = o["provider"]?.jsonPrimitive?.content.orEmpty(),
                        model = o["model"]?.jsonPrimitive?.content.orEmpty(),
                        promptTokens = o["promptTokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        completionTokens = o["completionTokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        status = o["status"]?.jsonPrimitive?.content.orEmpty(),
                        latencyMs = o["latencyMs"]?.jsonPrimitive?.content?.toLongOrNull(),
                    )
                }
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
    }

    suspend fun fetchQuotaData(): NineRouterQuotaData = withContext(Dispatchers.IO) {
        val root = resolveRoot() ?: return@withContext NineRouterQuotaData()
        val pyScript = """
import sqlite3, json, datetime
try:
    con = sqlite3.connect('/root/.9router/db/data.sqlite')
    cur = con.cursor()
    
    # 1. Antigravity connection
    cur.execute('''SELECT name, data FROM providerConnections WHERE provider = 'antigravity' ORDER BY isActive DESC LIMIT 1''')
    conn_row = cur.fetchone()
    account_email = conn_row[0] if conn_row else 'rougesonace@gmail.com'
    conn_data = json.loads(conn_row[1]) if (conn_row and conn_row[1]) else {}
    
    # 2. 5-hour window usage
    cur.execute('''SELECT timestamp, model, promptTokens, completionTokens
                   FROM usageHistory
                   WHERE provider = 'antigravity'
                   ORDER BY id DESC LIMIT 500''')
    rows = cur.fetchall()
    
    now = datetime.datetime.now(datetime.timezone.utc)
    five_hours_ago = now - datetime.timedelta(hours=5)
    one_day_ago = now - datetime.timedelta(days=1)
    seven_days_ago = now - datetime.timedelta(days=7)
    
    five_h_reqs = 0
    five_h_tokens = 0
    earliest_five_h = None
    
    daily_reqs = 0
    weekly_reqs = 0
    
    gemini_reqs = 0
    claude_reqs = 0
    
    for r in rows:
        ts_str = r[0]
        try:
            # parse ISO timestamp
            dt = datetime.datetime.fromisoformat(ts_str.replace('Z', '+00:00'))
        except:
            continue
            
        m = (r[1] or '').lower()
        
        if dt >= five_hours_ago:
            five_h_reqs += 1
            five_h_tokens += (r[2] or 0) + (r[3] or 0)
            if earliest_five_h is None or dt < earliest_five_h:
                earliest_five_h = dt
            if 'gemini' in m:
                gemini_reqs += 1
            if 'claude' in m:
                claude_reqs += 1
                
        if dt >= one_day_ago:
            daily_reqs += 1
            
        if dt >= seven_days_ago:
            weekly_reqs += 1
            
    # Window countdown
    countdown = "Ready"
    five_h_used_pct = min(100.0, (five_h_reqs / 300.0) * 100.0) if five_h_reqs > 0 else 0.0
    if earliest_five_h:
        reset_time = earliest_five_h + datetime.timedelta(hours=5)
        rem = reset_time - now
        total_seconds = int(rem.total_seconds())
        if total_seconds > 0:
            h = total_seconds // 3600
            m = (total_seconds % 3600) // 60
            countdown = f"Resets in {h}h {m}m"
        else:
            countdown = "Resetting now"
            
    # Status locks
    gemini_locked = any(k.startswith('modelLock_gemini') and v for k, v in conn_data.items())
    claude_locked = any(k.startswith('modelLock_claude') and v for k, v in conn_data.items())
    
    weekly_headroom = max(0.0, min(100.0, 100.0 - (weekly_reqs / 2000.0) * 100.0))
    claude_headroom = max(0.0, min(100.0, 100.0 - (claude_reqs / 50.0) * 100.0))
    
    res = {
        'accountEmail': account_email,
        'fiveHourUsedPercent': round(five_h_used_pct / 100.0, 3),
        'fiveHourRemainingPercent': round(max(0.0, 100.0 - five_h_used_pct), 1),
        'fiveHourResetCountdown': countdown,
        'weeklyUsedPercent': round(min(1.0, weekly_reqs / 2000.0), 3),
        'weeklyHeadroomPercent': round(weekly_headroom, 1),
        'weeklyResetCycle': "Cycle ends Sun 00:00 UTC",
        'geminiStatus': "LOCKED" if gemini_locked else "ACTIVE",
        'geminiRequestsInWindow': gemini_reqs,
        'geminiMaxAllowed': 300,
        'claudeStatus': "RATE LIMITED" if claude_locked else "ACTIVE",
        'claudeRequestsInWindow': claude_reqs,
        'claudeBurstHeadroomPercent': round(claude_headroom, 1),
        'claudeRateLimit': "Backoff Active" if claude_locked else "Normal",
        'dailyRequestsUsed': daily_reqs,
        'dailyRequestsCap': 1000,
        'dailyRequestsRemaining': max(0, 1000 - daily_reqs)
    }
    print(json.dumps(res))
except Exception as e:
    print(json.dumps({'error': str(e)}))
""".trimIndent()

        val res = workspaceManager.executeCommand(root, "python3 -c \"$pyScript\"", timeoutMillis = 4000L)
        val outJson = res.stdout.trim()
        if (outJson.startsWith("{")) {
            runCatching {
                val o = json.parseToJsonElement(outJson).jsonObject
                if (o.containsKey("error")) {
                    return@withContext NineRouterQuotaData()
                }
                NineRouterQuotaData(
                    connectionName = "Antigravity",
                    accountEmail = o["accountEmail"]?.jsonPrimitive?.content,
                    fiveHourUsedPercent = o["fiveHourUsedPercent"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f,
                    fiveHourRemainingPercent = o["fiveHourRemainingPercent"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 100f,
                    fiveHourResetCountdown = o["fiveHourResetCountdown"]?.jsonPrimitive?.content ?: "Ready",
                    weeklyUsedPercent = o["weeklyUsedPercent"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f,
                    weeklyHeadroomPercent = o["weeklyHeadroomPercent"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 100f,
                    weeklyResetCycle = o["weeklyResetCycle"]?.jsonPrimitive?.content ?: "Cycle ends Sun",
                    geminiStatus = o["geminiStatus"]?.jsonPrimitive?.content ?: "ACTIVE",
                    geminiRequestsInWindow = o["geminiRequestsInWindow"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    geminiMaxAllowed = o["geminiMaxAllowed"]?.jsonPrimitive?.content?.toIntOrNull() ?: 300,
                    claudeStatus = o["claudeStatus"]?.jsonPrimitive?.content ?: "ACTIVE",
                    claudeRequestsInWindow = o["claudeRequestsInWindow"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    claudeBurstHeadroomPercent = o["claudeBurstHeadroomPercent"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 100f,
                    claudeRateLimit = o["claudeRateLimit"]?.jsonPrimitive?.content ?: "Normal",
                    dailyRequestsUsed = o["dailyRequestsUsed"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    dailyRequestsCap = o["dailyRequestsCap"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1000,
                    dailyRequestsRemaining = o["dailyRequestsRemaining"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1000,
                )
            }.getOrDefault(NineRouterQuotaData())
        } else {
            NineRouterQuotaData()
        }
    }

    fun formatUpstreamError(rawMessage: String): String {
        val clean = rawMessage.trim()
        return if (clean.startsWith("[9Router:")) {
            clean
        } else {
            "[9Router: $clean]"
        }
    }

    private fun resolveRoot(): String? {
        return workspaceManager.findRootfsRoot()
    }

    companion object {
        fun resolveUpstreamTag(modelId: String, ownedBy: String?): String {
            val lowerId = modelId.lowercase()
            val lowerOwner = ownedBy?.lowercase().orEmpty()
            return when {
                lowerOwner == "ag" || lowerOwner.contains("antigravity") || lowerId.startsWith("ag/") || lowerId.contains("antigravity") -> "Antigravity"
                lowerOwner == "bzl" || lowerOwner.contains("bazaarlink") || lowerId.startsWith("bzl/") || lowerId.contains("bazaarlink") -> "Bazaarlink"
                lowerOwner.contains("deepseek") || lowerId.startsWith("deepseek") -> "DeepSeek"
                lowerOwner == "aq" || lowerOwner.contains("aqua") || lowerId.startsWith("aq/") -> "Aqua"
                lowerOwner.contains("gemini") || lowerId.startsWith("gemini/") -> "Gemini"
                lowerOwner.contains("claude") || lowerId.startsWith("claude/") -> "Claude"
                lowerOwner.contains("openai") || lowerId.startsWith("openai/") -> "OpenAI"
                lowerOwner.contains("codex") || lowerId.startsWith("codex/") -> "Codex"
                lowerOwner.contains("mmf") || lowerOwner.contains("mimo") || lowerId.startsWith("mmf/") -> "MIMO"
                lowerOwner.isNotBlank() -> lowerOwner.replaceFirstChar { it.uppercase() }
                modelId.contains("/") -> modelId.substringBefore("/").replaceFirstChar { it.uppercase() }
                else -> "9Router"
            }
        }
    }
}
