package me.rerere.workspace

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object WorkspaceArchive {
    private const val TAR_BLOCK_SIZE = 512
    private const val BUFFER_SIZE = 64 * 1024

    /**
     * Streams files from [sourceDir] into a .tar.gz [outputStream],
     * preserving permissions, modification times, and symlinks.
     */
    fun writeArchive(sourceDir: File, outputStream: OutputStream) {
        if (!sourceDir.exists()) sourceDir.mkdirs()
        GZIPOutputStream(BufferedOutputStream(outputStream)).use { gzipOut ->
            writeDirectory(sourceDir, sourceDir, gzipOut)
            // Two 512-byte zero blocks to terminate tar archive
            val zeroBlock = ByteArray(TAR_BLOCK_SIZE * 2)
            gzipOut.write(zeroBlock)
            gzipOut.flush()
        }
    }

    /**
     * Extracts a .tar.gz [inputStream] into [targetDir],
     * recreating files, directories, and symlinks while preserving modes.
     */
    fun extractArchive(inputStream: InputStream, targetDir: File) {
        targetDir.mkdirs()
        GZIPInputStream(BufferedInputStream(inputStream)).use { input ->
            var pendingName: String? = null
            var pendingLinkName: String? = null
            while (true) {
                checkInterrupted()
                val rawHeader = input.readTarHeader() ?: break
                val header = rawHeader.copy(
                    name = pendingName ?: rawHeader.name,
                    linkName = pendingLinkName ?: rawHeader.linkName,
                )
                pendingName = null
                pendingLinkName = null
                if (header.name.isBlank()) {
                    input.skipFully(header.size.paddedTarSize())
                    continue
                }
                if (header.type == TarEntryType.LONG_NAME) {
                    pendingName = input.readExactly(header.size).toString(Charsets.UTF_8).trimEnd('\u0000', '\n')
                    input.skipFully(header.size.paddingSize())
                    continue
                }
                if (header.type == TarEntryType.LONG_LINK) {
                    pendingLinkName = input.readExactly(header.size).toString(Charsets.UTF_8).trimEnd('\u0000', '\n')
                    input.skipFully(header.size.paddingSize())
                    continue
                }
                if (header.type == TarEntryType.PAX) {
                    val pax = parsePax(input.readExactly(header.size).toString(Charsets.UTF_8))
                    pendingName = pax["path"]
                    pendingLinkName = pax["linkpath"]
                    input.skipFully(header.size.paddingSize())
                    continue
                }

                // Strip leading workspace/ or files/ if present in imported archive
                val normalizedName = normalizeWorkspaceEntryPath(header.name)
                if (normalizedName.isBlank()) {
                    if (header.type != TarEntryType.FILE) {
                        input.skipFully(header.size)
                    }
                    input.skipFully(header.size.paddingSize())
                    continue
                }

                val target = targetDir.safeResolve(normalizedName)
                target.parentFile?.mkdirs()
                when (header.type) {
                    TarEntryType.DIRECTORY -> target.mkdirs()
                    TarEntryType.SYMLINK -> createSymlink(targetDir, target, header.linkName)
                    TarEntryType.HARDLINK -> createHardLink(targetDir, target, header.linkName)
                    TarEntryType.FILE -> {
                        target.outputStream().use { output ->
                            input.copyExactly(output, header.size)
                        }
                        target.applyMode(header.mode)
                    }

                    TarEntryType.LONG_NAME,
                    TarEntryType.LONG_LINK,
                    TarEntryType.PAX,
                    TarEntryType.OTHER -> Unit
                }
                if (header.type != TarEntryType.FILE) {
                    input.skipFully(header.size)
                }
                input.skipFully(header.size.paddingSize())
                if (header.modTime > 0 && header.type != TarEntryType.SYMLINK) {
                    target.setLastModified(header.modTime * 1000)
                }
            }
        }
    }

    private fun writeDirectory(rootDir: File, currentDir: File, out: OutputStream) {
        val files = currentDir.listFiles() ?: return
        for (file in files.sortedBy { it.name }) {
            checkInterrupted()
            val path = file.toPath()
            val isSymlink = Files.isSymbolicLink(path)
            val relativePath = file.relativeTo(rootDir).path.replace('\\', '/')

            if (isSymlink) {
                val linkTarget = Files.readSymbolicLink(path).toString().replace('\\', '/')
                writeTarEntry(
                    out = out,
                    name = relativePath,
                    size = 0L,
                    mode = 0b111_111_111,
                    mtime = file.lastModified() / 1000L,
                    type = TarEntryType.SYMLINK,
                    linkName = linkTarget,
                )
            } else if (file.isDirectory) {
                writeTarEntry(
                    out = out,
                    name = "$relativePath/",
                    size = 0L,
                    mode = getFileMode(file, false),
                    mtime = file.lastModified() / 1000L,
                    type = TarEntryType.DIRECTORY,
                    linkName = "",
                )
                writeDirectory(rootDir, file, out)
            } else if (file.isFile) {
                val size = file.length()
                writeTarEntry(
                    out = out,
                    name = relativePath,
                    size = size,
                    mode = getFileMode(file, false),
                    mtime = file.lastModified() / 1000L,
                    type = TarEntryType.FILE,
                    linkName = "",
                )
                file.inputStream().buffered().use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var remaining = size
                    while (remaining > 0) {
                        checkInterrupted()
                        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        remaining -= read
                    }
                }
                val pad = size.paddingSize().toInt()
                if (pad > 0) {
                    out.write(ByteArray(pad))
                }
            }
        }
    }

    private fun writeTarEntry(
        out: OutputStream,
        name: String,
        size: Long,
        mode: Int,
        mtime: Long,
        type: TarEntryType,
        linkName: String,
    ) {
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        if (nameBytes.size > 100) {
            val longNameBytes = nameBytes + byteArrayOf(0)
            val longHeader = createTarHeader(
                name = "././@LongLink",
                size = longNameBytes.size.toLong(),
                mode = 0,
                mtime = 0L,
                type = TarEntryType.LONG_NAME,
                linkName = "",
            )
            out.write(longHeader)
            out.write(longNameBytes)
            val pad = longNameBytes.size.toLong().paddingSize().toInt()
            if (pad > 0) out.write(ByteArray(pad))
        }

        val linkBytes = linkName.toByteArray(Charsets.UTF_8)
        if (linkBytes.size > 100) {
            val longLinkBytes = linkBytes + byteArrayOf(0)
            val longHeader = createTarHeader(
                name = "././@LongLink",
                size = longLinkBytes.size.toLong(),
                mode = 0,
                mtime = 0L,
                type = TarEntryType.LONG_LINK,
                linkName = "",
            )
            out.write(longHeader)
            out.write(longLinkBytes)
            val pad = longLinkBytes.size.toLong().paddingSize().toInt()
            if (pad > 0) out.write(ByteArray(pad))
        }

        val header = createTarHeader(
            name = name,
            size = size,
            mode = mode,
            mtime = mtime,
            type = type,
            linkName = linkName,
        )
        out.write(header)
    }

    private fun createTarHeader(
        name: String,
        size: Long,
        mode: Int,
        mtime: Long,
        type: TarEntryType,
        linkName: String,
    ): ByteArray {
        val block = ByteArray(TAR_BLOCK_SIZE)
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        System.arraycopy(nameBytes, 0, block, 0, minOf(nameBytes.size, 100))

        writeOctal(block, 100, 8, mode.toLong())
        writeOctal(block, 108, 8, 0L)
        writeOctal(block, 116, 8, 0L)
        writeOctal(block, 124, 12, size)
        writeOctal(block, 136, 12, mtime)

        for (i in 148 until 156) {
            block[i] = ' '.code.toByte()
        }

        block[156] = when (type) {
            TarEntryType.FILE -> '0'.code.toByte()
            TarEntryType.HARDLINK -> '1'.code.toByte()
            TarEntryType.SYMLINK -> '2'.code.toByte()
            TarEntryType.DIRECTORY -> '5'.code.toByte()
            TarEntryType.LONG_NAME -> 'L'.code.toByte()
            TarEntryType.LONG_LINK -> 'K'.code.toByte()
            TarEntryType.PAX -> 'x'.code.toByte()
            TarEntryType.OTHER -> '0'.code.toByte()
        }

        val linkBytes = linkName.toByteArray(Charsets.UTF_8)
        System.arraycopy(linkBytes, 0, block, 157, minOf(linkBytes.size, 100))

        val magic = "ustar\u0000".toByteArray(Charsets.US_ASCII)
        System.arraycopy(magic, 0, block, 257, magic.size)

        val version = "00".toByteArray(Charsets.US_ASCII)
        System.arraycopy(version, 0, block, 263, version.size)

        val uname = "root".toByteArray(Charsets.US_ASCII)
        System.arraycopy(uname, 0, block, 265, uname.size)

        val gname = "root".toByteArray(Charsets.US_ASCII)
        System.arraycopy(gname, 0, block, 297, gname.size)

        var chksum = 0
        for (b in block) {
            chksum += (b.toInt() and 0xFF)
        }
        val chkStr = String.format(Locale.US, "%06o\u0000 ", chksum)
        val chkBytes = chkStr.toByteArray(Charsets.US_ASCII)
        System.arraycopy(chkBytes, 0, block, 148, minOf(chkBytes.size, 8))

        return block
    }

    private fun writeOctal(block: ByteArray, offset: Int, length: Int, value: Long) {
        val s = String.format(Locale.US, "%0${length - 1}o\u0000", value)
        val b = s.toByteArray(Charsets.US_ASCII)
        System.arraycopy(b, 0, block, offset, minOf(b.size, length))
    }

    private fun getFileMode(file: File, isSymlink: Boolean): Int {
        if (isSymlink) return 0b111_111_111
        val posix = runCatching {
            var mode = 0
            val perms = Files.getPosixFilePermissions(file.toPath())
            if (perms.contains(PosixFilePermission.OWNER_READ)) mode = mode or 0b100_000_000
            if (perms.contains(PosixFilePermission.OWNER_WRITE)) mode = mode or 0b010_000_000
            if (perms.contains(PosixFilePermission.OWNER_EXECUTE)) mode = mode or 0b001_000_000
            if (perms.contains(PosixFilePermission.GROUP_READ)) mode = mode or 0b000_100_000
            if (perms.contains(PosixFilePermission.GROUP_WRITE)) mode = mode or 0b000_010_000
            if (perms.contains(PosixFilePermission.GROUP_EXECUTE)) mode = mode or 0b000_001_000
            if (perms.contains(PosixFilePermission.OTHERS_READ)) mode = mode or 0b000_000_100
            if (perms.contains(PosixFilePermission.OTHERS_WRITE)) mode = mode or 0b000_000_010
            if (perms.contains(PosixFilePermission.OTHERS_EXECUTE)) mode = mode or 0b000_000_001
            mode
        }.getOrNull()
        if (posix != null && posix != 0) return posix
        return if (file.isDirectory) 0b111_101_101
        else if (file.canExecute()) 0b111_101_101
        else 0b110_100_100
    }

    private fun createSymlink(root: File, target: File, linkName: String) {
        if (linkName.isBlank()) return
        val linkTarget = if (File(linkName).isAbsolute) {
            File(linkName)
        } else {
            val resolved = File(target.parentFile ?: root, linkName).canonicalFile
            val rootFile = root.canonicalFile
            require(resolved.path == rootFile.path || resolved.path.startsWith(rootFile.path + File.separator)) {
                "Symlink escapes workspace root: ${target.name}"
            }
            (target.parentFile ?: root).toPath().relativize(resolved.toPath()).toFile()
        }
        target.delete()
        Files.createSymbolicLink(target.toPath(), linkTarget.toPath())
    }

    private fun createHardLink(root: File, target: File, linkName: String) {
        if (linkName.isBlank()) return
        val source = root.safeResolve(linkName)
        if (!source.exists()) return
        target.delete()
        runCatching {
            Files.createLink(target.toPath(), source.toPath())
        }.recoverCatching { error ->
            if (error !is IOException &&
                error !is UnsupportedOperationException &&
                error !is SecurityException
            ) {
                throw error
            }
            source.copyTo(target, overwrite = true)
            target.setReadable(source.canRead(), false)
            target.setWritable(source.canWrite(), true)
            target.setExecutable(source.canExecute(), false)
        }.getOrThrow()
    }

    private fun InputStream.readTarHeader(): TarHeader? {
        val header = ByteArray(TAR_BLOCK_SIZE)
        val read = readFullyOrEnd(header)
        if (read == 0) return null
        if (read < TAR_BLOCK_SIZE) throw EOFException("Unexpected EOF while reading tar header")
        if (header.all { it == 0.toByte() }) return null

        val name = header.string(0, 100)
        val prefix = header.string(345, 155)
        val fullName = listOf(prefix, name)
            .filter { it.isNotBlank() }
            .joinToString("/")
        return TarHeader(
            name = normalizeTarPath(fullName),
            mode = header.octal(100, 8).toInt(),
            size = header.octal(124, 12),
            modTime = header.octal(136, 12),
            type = when (header[156].toInt().toChar()) {
                '0', '\u0000' -> TarEntryType.FILE
                '5' -> TarEntryType.DIRECTORY
                '2' -> TarEntryType.SYMLINK
                '1' -> TarEntryType.HARDLINK
                'L' -> TarEntryType.LONG_NAME
                'K' -> TarEntryType.LONG_LINK
                'x' -> TarEntryType.PAX
                else -> TarEntryType.OTHER
            },
            linkName = header.string(157, 100),
        )
    }

    private fun parsePax(text: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var index = 0
        while (index < text.length) {
            val space = text.indexOf(' ', index)
            if (space < 0) break
            val length = text.substring(index, space).toIntOrNull() ?: break
            val end = (index + length).coerceAtMost(text.length)
            val record = text.substring(space + 1, end).trimEnd('\n')
            val equals = record.indexOf('=')
            if (equals > 0) {
                result[record.substring(0, equals)] = record.substring(equals + 1)
            }
            index += length
        }
        return result
    }

    private fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted) {
            throw InterruptedException("Archive operation cancelled")
        }
    }

    private fun InputStream.copyExactly(output: OutputStream, bytes: Long) {
        val buffer = ByteArray(BUFFER_SIZE)
        var remaining = bytes
        while (remaining > 0) {
            checkInterrupted()
            val read = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) throw EOFException("Unexpected EOF while extracting tar entry")
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun InputStream.readExactly(bytes: Long): ByteArray {
        require(bytes <= Int.MAX_VALUE) { "Tar entry is too large to buffer: $bytes" }
        val buffer = ByteArray(bytes.toInt())
        val read = readFullyOrEnd(buffer)
        if (read != buffer.size) throw EOFException("Unexpected EOF while reading tar entry")
        return buffer
    }

    private fun InputStream.skipFully(bytes: Long) {
        var remaining = bytes
        while (remaining > 0) {
            checkInterrupted()
            val skipped = skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else if (read() >= 0) {
                remaining--
            } else {
                throw EOFException("Unexpected EOF while skipping tar data")
            }
        }
    }

    private fun InputStream.readFullyOrEnd(buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val read = read(buffer, offset, buffer.size - offset)
            if (read < 0) break
            offset += read
        }
        return offset
    }

    private fun File.safeResolve(path: String): File {
        val normalized = normalizeTarPath(path)
        val root = canonicalFile
        val target = File(root, normalized).canonicalFile
        require(target.path == root.path || target.path.startsWith(root.path + File.separator)) {
            "Workspace entry escapes target directory: $path"
        }
        return target
    }

    private fun File.applyMode(mode: Int) {
        setReadable(mode and 0b100_000_000 != 0, false)
        setWritable(mode and 0b010_000_000 != 0, true)
        setExecutable(mode and 0b001_000_000 != 0, false)
    }

    private fun normalizeWorkspaceEntryPath(rawPath: String): String {
        var p = rawPath.replace('\\', '/').trim().trimStart('/').removePrefix("./")
        if (p.startsWith("workspace/")) {
            p = p.removePrefix("workspace/").trimStart('/')
        } else if (p.startsWith("files/")) {
            p = p.removePrefix("files/").trimStart('/')
        }
        return p
    }

    private fun normalizeTarPath(path: String): String {
        val normalized = path
            .replace('\\', '/')
            .trim()
            .trimStart('/')
            .removePrefix("./")
        require(normalized.isNotBlank()) { "Tar entry path is blank" }
        require(!normalized.contains('\u0000')) { "Tar entry path contains invalid character" }
        require(normalized.split('/').none { it == ".." }) { "Tar entry escapes target directory: $path" }
        return normalized
    }

    private fun ByteArray.string(offset: Int, length: Int): String {
        val end = (offset until offset + length)
            .firstOrNull { this[it] == 0.toByte() }
            ?: (offset + length)
        return copyOfRange(offset, end).toString(Charsets.UTF_8).trim()
    }

    private fun ByteArray.octal(offset: Int, length: Int): Long {
        val value = string(offset, length)
            .trim()
            .lowercase(Locale.US)
            .trimEnd('\u0000')
        return if (value.isBlank()) 0L else value.toLong(8)
    }

    private fun Long.paddingSize(): Long = (TAR_BLOCK_SIZE - (this % TAR_BLOCK_SIZE)).let {
        if (it == TAR_BLOCK_SIZE.toLong()) 0L else it
    }

    private fun Long.paddedTarSize(): Long = this + paddingSize()

    private data class TarHeader(
        val name: String,
        val mode: Int,
        val size: Long,
        val modTime: Long,
        val type: TarEntryType,
        val linkName: String,
    )

    private enum class TarEntryType {
        FILE,
        DIRECTORY,
        SYMLINK,
        HARDLINK,
        LONG_NAME,
        LONG_LINK,
        PAX,
        OTHER,
    }
}
