package me.rerere.workspace

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class WorkspaceManager(
    private val baseDir: File,
    private val config: WorkspaceConfig = WorkspaceConfig(),
    private val shellRunner: WorkspaceShellRunner = HostShellRunner(),
    private val bindMounts: List<WorkspaceBindMount> = emptyList(),
) {
    private val fileSystem = WorkspaceFileSystem(config)
    private val background = WorkspaceBackgroundProcesses()

    // 让 startBackground 的启动+注册 与 deleteWorkspace 的 killAll+删除 互斥:
    // 要么启动先完成(随后被 killAll 杀掉), 要么删除先完成(随后 shellRunner.start 因 rootfs
    // 缺失而失败并抛出), 不会出现"进程活着但 workspace 目录已删"的孤儿进程
    private val backgroundLifecycleLock = Any()

    // 按 target 长度降序, 保证 /a/b 优先于 /a 匹配
    private val sortedBindMounts = bindMounts.sortedByDescending { it.target.trimEnd('/').length }

    init {
        baseDir.mkdirs()
    }

    fun ensureWorkspace(root: String): File {
        val dir = workspaceDir(root)
        filesDir(root).mkdirs()
        linuxDir(root).mkdirs()
        tempDir(root).mkdirs()
        return dir
    }

    fun workspaceDir(root: String): File {
        requireValidRoot(root)
        return File(baseDir, root)
    }

    fun filesDir(root: String): File = File(workspaceDir(root), FILES_DIR)

    fun linuxDir(root: String): File = File(workspaceDir(root), LINUX_DIR)

    fun tempDir(root: String): File = File(workspaceDir(root), TEMP_DIR)

    fun hasRootfs(root: String): Boolean = File(linuxDir(root), "bin/sh").isFile

    fun findRootfsRoot(): String? {
        val dirs = baseDir.listFiles()?.filter { it.isDirectory } ?: return null
        return dirs.firstOrNull { hasRootfs(it.name) }?.name ?: dirs.firstOrNull()?.name
    }

    fun deleteWorkspace(root: String): Boolean = synchronized(backgroundLifecycleLock) {
        // 先杀掉该 workspace 所有后台进程, 再删目录, 避免进程仍持有已删除目录下的 fd
        killAllBackground(root)
        workspaceDir(root).deleteRecursively()
    }

    fun listFiles(
        root: String,
        path: String = "",
        area: WorkspaceStorageArea = WorkspaceStorageArea.FILES,
        limit: Int? = null,
    ): List<WorkspaceFileEntry> =
        if (limit == null) {
            fileSystem.list(areaDir(root, area), path)
        } else {
            fileSystem.list(areaDir(root, area), path, limit)
        }

    fun readText(
        root: String,
        path: String,
        charset: Charset = StandardCharsets.UTF_8,
    ): String = fileSystem.readText(filesDir(root), path, charset)

    fun writeText(
        root: String,
        path: String,
        text: String,
        overwrite: Boolean = true,
        charset: Charset = StandardCharsets.UTF_8,
    ): WorkspaceFileEntry = fileSystem.writeText(filesDir(root), path, text, overwrite, charset)

    fun importFile(
        root: String,
        destinationPath: String,
        area: WorkspaceStorageArea = WorkspaceStorageArea.FILES,
        fileName: String,
        inputStream: InputStream,
    ): WorkspaceFileEntry {
        val areaRoot = areaDir(root, area)
        val targetPath = if (destinationPath.isBlank()) fileName else "$destinationPath/$fileName"
        return fileSystem.importBytes(areaRoot, targetPath, inputStream)
    }

    fun fileSize(
        root: String,
        path: String,
        area: WorkspaceStorageArea = WorkspaceStorageArea.FILES,
    ): Long {
        val file = fileSystem.resolve(areaDir(root, area), path)
        require(file.exists()) { "File does not exist: $path" }
        require(file.isFile) { "Path is not a file: $path" }
        return file.length()
    }

    fun resolveFile(
        root: String,
        path: String,
        area: WorkspaceStorageArea = WorkspaceStorageArea.FILES,
    ): File {
        val file = fileSystem.resolve(areaDir(root, area), path)
        require(file.exists()) { "File does not exist: $path" }
        require(file.isFile) { "Path is not a file: $path" }
        return file
    }

    fun exportFile(
        root: String,
        path: String,
        area: WorkspaceStorageArea = WorkspaceStorageArea.FILES,
        outputStream: OutputStream,
    ) {
        val file = fileSystem.resolve(areaDir(root, area), path)
        require(file.exists()) { "File does not exist: $path" }
        require(file.isFile) { "Path is not a file: $path" }
        outputStream.use { out -> file.inputStream().use { it.copyTo(out) } }
    }

    /**
     * Streams files from the workspace file area (/workspace) into a .tar.gz [outputStream],
     * preserving permissions, modification times, and symlinks.
     */
    fun exportWorkspaceArchive(root: String, outputStream: OutputStream) {
        val dir = filesDir(root)
        dir.mkdirs()
        WorkspaceArchive.writeArchive(dir, outputStream)
    }

    /**
     * Extracts a .tar.gz [inputStream] into the workspace file area (/workspace).
     */
    fun importWorkspaceArchive(root: String, inputStream: InputStream) {
        val dir = filesDir(root)
        dir.mkdirs()
        WorkspaceArchive.extractArchive(inputStream, dir)
    }

    /**
     * 把 Rootfs 内的绝对路径映射到宿主机上的真实文件。
     *
     * bind mount 的 source 本身就是 Android 侧的普通目录, 因此 /skills 这类挂载路径
     * 可以直接用文件 IO 访问, 无需经过 PRoot; 只是 Rootfs 目录里对应位置是个空挂载点,
     * 按 [WorkspaceStorageArea.LINUX] 解析必然落空。
     */
    fun resolveRootfsPath(root: String, path: String): RootfsLocation {
        val trimmed = path.trim().trimEnd('/').ifBlank { "/" }
        require(trimmed.startsWith("/")) { "Rootfs path must be absolute: $path" }

        sortedBindMounts.forEach { mount ->
            val target = mount.target.trimEnd('/')
            if (trimmed == target) return RootfsLocation(mount.source, "")
            if (trimmed.startsWith("$target/")) {
                return RootfsLocation(mount.source, trimmed.removePrefix("$target/"))
            }
        }

        if (trimmed == ROOTFS_WORKSPACE_DIR || trimmed.startsWith("$ROOTFS_WORKSPACE_DIR/")) {
            return RootfsLocation(
                rootDir = filesDir(root),
                relativePath = trimmed.removePrefix(ROOTFS_WORKSPACE_DIR).trimStart('/'),
            )
        }

        // 内核伪文件系统: 显式拒绝, 而不是回落到一个必然读不到的物理路径
        KERNEL_FS_MOUNTS.firstOrNull { trimmed == it || trimmed.startsWith("$it/") }?.let {
            error("$it is a kernel filesystem and cannot be read as a file, use workspace_shell instead")
        }

        return RootfsLocation(linuxDir(root), trimmed.trimStart('/'))
    }

    fun rootfsFileSize(root: String, path: String): Long =
        resolveRootfsFile(root, path).also { it.requireReadableFile(path) }.length()

    fun exportRootfsFile(root: String, path: String, outputStream: OutputStream) {
        val file = resolveRootfsFile(root, path)
        file.requireReadableFile(path)
        outputStream.use { out -> file.inputStream().use { it.copyTo(out) } }
    }

    /** 按 Rootfs 内绝对路径递归列出目录树, 支持 /workspace、bind mount 与 Rootfs 内部路径 */
    fun rootfsTree(root: String, path: String, maxDepth: Int = 10): WorkspaceTreeResult {
        val location = resolveRootfsPath(root, path)
        return fileSystem.tree(location.rootDir, location.relativePath, maxDepth)
    }

    fun resolveRootfsFile(root: String, path: String): File {
        val location = resolveRootfsPath(root, path)
        return fileSystem.resolve(location.rootDir, location.relativePath)
    }

    private fun File.requireReadableFile(path: String) {
        require(exists()) { "File does not exist: $path" }
        require(isFile) { "Path is not a file: $path" }
    }

    fun deleteFile(
        root: String,
        path: String,
        recursive: Boolean = false,
        area: WorkspaceStorageArea = WorkspaceStorageArea.FILES,
    ): Boolean =
        fileSystem.delete(areaDir(root, area), path, recursive)

    fun moveFile(root: String, source: String, target: String, overwrite: Boolean = false): WorkspaceFileEntry =
        fileSystem.move(filesDir(root), source, target, overwrite)

    fun glob(root: String, pattern: String, path: String = ""): List<WorkspaceFileEntry> =
        fileSystem.glob(filesDir(root), pattern, path)

    fun grep(
        root: String,
        query: String,
        path: String = "",
        regex: Boolean = false,
        ignoreCase: Boolean = true,
        includeGlob: String? = null,
    ): List<WorkspaceSearchMatch> =
        fileSystem.grep(filesDir(root), query, path, regex, ignoreCase, includeGlob)

    fun executeCommand(
        root: String,
        command: String,
        cwd: String = "",
        timeoutMillis: Long = DEFAULT_COMMAND_TIMEOUT_MS,
        stdin: ByteArray? = null,
        shellCompatibilityMode: Boolean = false,
    ): WorkspaceCommandResult {
        require(command.isNotBlank()) { "Command is required" }
        val workingDir = resolveCommandWorkingDir(root, cwd)

        return shellRunner.execute(
            WorkspaceShellContext(
                root = root,
                command = command,
                cwd = cwd,
                filesDir = filesDir(root),
                linuxDir = linuxDir(root),
                tempDir = tempDir(root),
                workingDir = workingDir,
                timeoutMillis = timeoutMillis,
                stdin = stdin,
                bindMounts = bindMounts,
                shellCompatibilityMode = shellCompatibilityMode,
            )
        )
    }

    var onProcessStarted: (() -> Unit)? = null
    var onProcessStopped: (() -> Unit)? = null

    /**
     * Starts [command] as a long-lived background process for [root]. The process runs
     * in the foreground of its own proot invocation; the caller polls/kills it via
     * [backgroundStatus]/[killBackground]. Throws IllegalStateException if [root] is
     * already at the running-process cap.
     */
    fun startBackground(root: String, command: String, cwd: String = ""): BackgroundStatus =
        synchronized(backgroundLifecycleLock) {
            require(command.isNotBlank()) { "Command is required" }
            val workingDir = resolveCommandWorkingDir(root, cwd)

            val process = shellRunner.start(
                WorkspaceShellContext(
                    root = root,
                    command = command,
                    cwd = cwd,
                    filesDir = filesDir(root),
                    linuxDir = linuxDir(root),
                    tempDir = tempDir(root),
                    workingDir = workingDir,
                    timeoutMillis = 0L,
                )
            )
            onProcessStarted?.invoke()
            background.start(root, process, command, cwd)
        }

    fun backgroundStatus(root: String, id: String): BackgroundStatus? = background.status(root, id)

    fun listBackground(root: String): List<BackgroundStatus> = background.list(root)

    fun killBackground(root: String, id: String): Boolean {
        val result = background.kill(root, id)
        if (result) {
            onProcessStopped?.invoke()
        }
        return result
    }

    fun killAllBackground(root: String) {
        background.killAll(root)
        onProcessStopped?.invoke()
    }

    /**
     * Checks if the virtual X11 display stack (noVNC websockify or VNC server) is listening.
     */
    fun isX11DisplayRunning(root: String): Boolean {
        fun isPortOpen(port: Int): Boolean = try {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress("127.0.0.1", port), 300)
                true
            }
        } catch (_: Exception) {
            false
        }
        return isPortOpen(6080) || isPortOpen(5900)
    }

    /**
     * Starts the virtual X11 display stack:
     * 1. Xvfb on display :0 (${width}x${height}x24)
     * 2. x11vnc RFB server on 127.0.0.1:5900
     * 3. websockify forwarding HTML5 noVNC client on 127.0.0.1:6080 to 5900
     */
    fun startX11Display(root: String, width: Int = 1280, height: Int = 800): Boolean {
        if (isX11DisplayRunning(root)) return true

        stopX11Display(root)

        val script = """
            rm -f /tmp/.X0-lock /tmp/.X11-unix/X0
            Xvfb :0 -screen 0 ${width}x${height}x24 &
            sleep 1
            x11vnc -display :0 -rfbport 5900 -nopw -listen 127.0.0.1 -forever -bg
            sleep 1
            websockify --web /usr/share/novnc 6080 127.0.0.1:5900
        """.trimIndent()

        startBackground(root = root, command = script, cwd = "")
        return true
    }

    /**
     * Cleanly kills Xvfb, x11vnc, websockify and removes lock files.
     */
    fun stopX11Display(root: String) {
        listBackground(root).forEach { bg ->
            if (bg.command.contains("websockify") || bg.command.contains("Xvfb") || bg.command.contains("x11vnc")) {
                killBackground(root, bg.id)
            }
        }
        runCatching {
            executeCommand(
                root = root,
                command = "pkill -f websockify || true; pkill -f x11vnc || true; pkill -f Xvfb || true; rm -f /tmp/.X0-lock /tmp/.X11-unix/X0",
                timeoutMillis = 5_000,
            )
        }
    }

    private fun resolveCommandWorkingDir(root: String, cwd: String): File {
        val workingDir = fileSystem.resolve(filesDir(root), cwd)
        require(workingDir.exists()) { "Working directory does not exist: $cwd" }
        require(workingDir.isDirectory) { "Working path is not a directory: $cwd" }
        return workingDir
    }

    private fun requireValidRoot(root: String) {
        require(root.matches(ROOT_NAME_REGEX)) {
            "Invalid workspace root name: $root"
        }
    }

    private fun areaDir(root: String, area: WorkspaceStorageArea): File = when (area) {
        WorkspaceStorageArea.FILES -> filesDir(root)
        WorkspaceStorageArea.LINUX -> linuxDir(root)
    }

    fun cleanupAllTempDirs() {
        val roots = baseDir.listFiles()?.filter { it.isDirectory } ?: return
        for (dir in roots) {
            val root = dir.name
            if (!root.matches(ROOT_NAME_REGEX)) continue
            // PRoot temp files
            tempDir(root).let { if (it.exists()) it.deleteRecursively() }
            // Rootfs /tmp and /var/tmp
            File(linuxDir(root), "tmp").let { if (it.exists()) it.deleteRecursively() }
            File(linuxDir(root), "var/tmp").let { if (it.exists()) it.deleteRecursively() }
        }
    }

    companion object {
        private const val FILES_DIR = "files"
        private const val LINUX_DIR = "linux"
        private const val TEMP_DIR = "tmp"
        const val DEFAULT_COMMAND_TIMEOUT_MS = 30_000L

        /** Rootfs 内工作区文件区的挂载点 */
        const val ROOTFS_WORKSPACE_DIR = "/workspace"

        /** 由宿主机透传的内核伪文件系统, 只能通过 shell 访问 */
        val KERNEL_FS_MOUNTS = listOf("/dev", "/proc", "/sys")

        private val ROOT_NAME_REGEX = Regex("[A-Za-z0-9._-]+")
    }
}

/** Rootfs 内绝对路径在宿主机上的落点 */
data class RootfsLocation(
    val rootDir: File,
    val relativePath: String,
)
