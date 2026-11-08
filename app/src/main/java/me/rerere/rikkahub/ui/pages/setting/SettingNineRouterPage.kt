package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import me.rerere.ai.provider.providers.ninerouter.NINEROUTER_BASE_URL
import me.rerere.ai.provider.providers.ninerouter.NineRouterRequestLog
import me.rerere.ai.provider.providers.ninerouter.NineRouterService
import me.rerere.ai.provider.providers.ninerouter.NineRouterState
import me.rerere.ai.provider.providers.ninerouter.NineRouterStatus
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AlertCircle
import me.rerere.hugeicons.stroke.Camera01
import me.rerere.hugeicons.stroke.CheckmarkCircle01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Download01
import me.rerere.hugeicons.stroke.Internet
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.ServerStack01
import me.rerere.hugeicons.stroke.Stop
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.QRCode
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject
import java.net.NetworkInterface

@Composable
fun SettingNineRouterPage(
    service: NineRouterService = koinInject()
) {
    val navController = LocalNavController.current
    val toaster = LocalToaster.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var state by remember { mutableStateOf(NineRouterState()) }
    var requests by remember { mutableStateOf<List<NineRouterRequestLog>>(emptyList()) }
    var isOperating by remember { mutableStateOf(false) }
    var qrDialogUrl by remember { mutableStateOf<String?>(null) }

    val lanIp = remember { getLanIpAddress() }
    val localUrl = NINEROUTER_BASE_URL
    val lanUrl = if (lanIp != null) "http://$lanIp:20128/v1" else localUrl

    fun refresh() {
        coroutineScope.launch {
            state = service.checkStatus()
            requests = service.fetchRecentRequests(limit = 10)
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("9Router Command Station") },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(
                        onClick = { refresh() },
                        enabled = !isOperating
                    ) {
                        Icon(HugeIcons.Refresh01, contentDescription = "Refresh status")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .imePadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Engine Controls & Live Status Banner
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = CustomColors.listItemColors.containerColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (state.status == NineRouterStatus.ONLINE) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.errorContainer
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (state.status == NineRouterStatus.ONLINE) {
                                            HugeIcons.CheckmarkCircle01
                                        } else {
                                            HugeIcons.AlertCircle
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = if (state.status == NineRouterStatus.ONLINE) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        }
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when (state.status) {
                                        NineRouterStatus.ONLINE -> "Gateway is Online (:20128)"
                                        NineRouterStatus.STOPPED -> "Gateway is Stopped"
                                        NineRouterStatus.NOT_INSTALLED -> "Gateway Not Installed"
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                val subtext = buildString {
                                    if (state.status == NineRouterStatus.ONLINE) {
                                        if (state.pids.isNotEmpty()) {
                                            append("PID: ${state.pids.joinToString()} ")
                                        }
                                        if (state.ramMb != null) {
                                            append("| RAM: ${state.ramMb} MB")
                                        }
                                    } else if (state.status == NineRouterStatus.STOPPED) {
                                        append("Ready to auto-wake or manually start")
                                    } else {
                                        append("Run install in workspace to configure")
                                    }
                                }
                                Text(
                                    text = subtext,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Engine Control Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (state.status == NineRouterStatus.ONLINE) {
                                Button(
                                    onClick = {
                                        isOperating = true
                                        coroutineScope.launch {
                                            runCatching { service.stop() }
                                                .onSuccess {
                                                    toaster.show("9Router stopped", type = ToastType.Success)
                                                }
                                                .onFailure {
                                                    toaster.show("Failed to stop: ${it.message}", type = ToastType.Error)
                                                }
                                            refresh()
                                            isOperating = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    enabled = !isOperating,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (isOperating) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = MaterialTheme.colorScheme.onError,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(HugeIcons.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Stop Gateway")
                                    }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        isOperating = true
                                        coroutineScope.launch {
                                            runCatching { service.start() }
                                                .onSuccess {
                                                    toaster.show("9Router online", type = ToastType.Success)
                                                }
                                                .onFailure {
                                                    toaster.show("Failed to start: ${it.message}", type = ToastType.Error)
                                                }
                                            refresh()
                                            isOperating = false
                                        }
                                    },
                                    enabled = !isOperating,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (isOperating) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(HugeIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Start Gateway")
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    navController.navigate(Screen.WebView("http://127.0.0.1:20128/dashboard"))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(HugeIcons.Internet, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Web Dashboard")
                            }
                        }
                    }
                }
            }

            // 2. Update Notifier Card
            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Package Version",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Current: ${state.currentVersion ?: "unknown"} · Latest: ${state.latestVersion ?: "unknown"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (state.hasUpdate) {
                            Button(
                                onClick = {
                                    isOperating = true
                                    coroutineScope.launch {
                                        toaster.show("Updating 9Router...", type = ToastType.Info)
                                        runCatching { service.update() }
                                            .onSuccess {
                                                toaster.show("9Router updated successfully", type = ToastType.Success)
                                            }
                                            .onFailure {
                                                toaster.show("Update failed: ${it.message}", type = ToastType.Error)
                                            }
                                        refresh()
                                        isOperating = false
                                    }
                                },
                                enabled = !isOperating
                            ) {
                                Icon(HugeIcons.Download01, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Update")
                            }
                        } else {
                            Tag(type = TagType.SUCCESS) {
                                Text("Up to date")
                            }
                        }
                    }
                }
            }

            // 3. LAN Exposure & Pairing
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = CustomColors.listItemColors.containerColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "LAN Exposure & Pairing",
                            style = MaterialTheme.typography.titleMedium
                        )

                        // Local URL Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Local Endpoint",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = localUrl,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(localUrl))
                                    toaster.show("Copied local URL", type = ToastType.Success)
                                }
                            ) {
                                Icon(HugeIcons.Copy01, contentDescription = "Copy Local URL")
                            }
                        }

                        // LAN URL Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "LAN Endpoint",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = lanUrl,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(lanUrl))
                                    toaster.show("Copied LAN URL", type = ToastType.Success)
                                }
                            ) {
                                Icon(HugeIcons.Copy01, contentDescription = "Copy LAN URL")
                            }
                            IconButton(
                                onClick = {
                                    qrDialogUrl = lanUrl
                                }
                            ) {
                                Icon(HugeIcons.Camera01, contentDescription = "Show Pairing QR Code")
                            }
                        }
                    }
                }
            }

            // 4. Mini Request Inspector
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mini Request Inspector",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Last 10 requests from /root/.9router/db/data.sqlite",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (requests.isEmpty()) {
                item {
                    Text(
                        text = "No recent requests recorded in SQLite.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(requests, key = { it.id }) { req ->
                    RequestLogItem(req = req)
                }
            }
        }
    }

    // QR Code Pairing Dialog
    qrDialogUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { qrDialogUrl = null },
            title = { Text("Pairing QR Code") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Scan from external devices on the same Wi-Fi to connect to 9Router:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    QRCode(
                        value = url,
                        modifier = Modifier.size(220.dp)
                    )
                    Text(
                        text = url,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { qrDialogUrl = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun RequestLogItem(req: NineRouterRequestLog) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = CustomColors.listItemColors.containerColor
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = req.model,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Tag(
                    type = if (req.status.lowercase() in listOf("ok", "success", "200")) TagType.SUCCESS else TagType.ERROR
                ) {
                    Text(req.status.ifBlank { "unknown" })
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Tag(type = TagType.INFO) {
                    Text(req.provider.ifBlank { "gateway" })
                }
                Text(
                    text = "In: ${req.promptTokens} · Out: ${req.completionTokens}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                val timeStr = req.timestamp.substringAfter("T").substringBefore(".")
                Text(
                    text = timeStr.ifBlank { req.timestamp },
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun getLanIpAddress(): String? {
    return runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }.getOrNull()
}
