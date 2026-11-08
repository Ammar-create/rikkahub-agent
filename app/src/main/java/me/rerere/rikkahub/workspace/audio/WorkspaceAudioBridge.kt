package me.rerere.rikkahub.workspace.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Workspace Audio Bridge:
 * Provides a lightweight TCP audio server on 127.0.0.1:4713 (and 127.0.0.1:4714) connecting PRoot Linux
 * processes to Android's native AudioTrack (playback) and AudioRecord (microphone).
 *
 * Conforms to the standard PulseAudio Simple Protocol and raw PCM stream conventions.
 */
class WorkspaceAudioBridge(
    private val context: Context,
) {
    private val isRunning = AtomicBoolean(false)
    private val refCount = AtomicInteger(0)

    private var playbackServerSocket: ServerSocket? = null
    private var recordServerSocket: ServerSocket? = null

    private val activePlaybackSockets = CopyOnWriteArrayList<Socket>()
    private val activeRecordSockets = CopyOnWriteArrayList<Socket>()

    // AudioTrack for playback
    private var audioTrack: AudioTrack? = null
    private val audioTrackLock = Any()

    // AudioRecord for microphone
    private var audioRecord: AudioRecord? = null
    private var recordThread: Thread? = null
    private val isRecording = AtomicBoolean(false)
    private val recordLock = Any()

    /**
     * Start the TCP servers on 127.0.0.1:4713 and 4714.
     */
    fun start() {
        if (!isRunning.compareAndSet(false, true)) return
        Log.i(TAG, "Starting WorkspaceAudioBridge...")

        startPlaybackServer()
        startRecordServer()
    }

    /**
     * Increment active reference count and ensure bridge is running.
     */
    fun acquire() {
        refCount.incrementAndGet()
        start()
    }

    /**
     * Decrement active reference count and stop bridge when idle.
     */
    fun release() {
        val count = refCount.decrementAndGet()
        if (count <= 0) {
            refCount.set(0)
            stop()
        }
    }

    /**
     * Stop the TCP audio bridge and release all hardware resources.
     */
    fun stop() {
        if (!isRunning.compareAndSet(true, false)) return
        Log.i(TAG, "Stopping WorkspaceAudioBridge...")

        // Close server sockets
        runCatching { playbackServerSocket?.close() }
        playbackServerSocket = null
        runCatching { recordServerSocket?.close() }
        recordServerSocket = null

        // Disconnect all clients
        activePlaybackSockets.forEach { runCatching { it.close() } }
        activePlaybackSockets.clear()

        activeRecordSockets.forEach { runCatching { it.close() } }
        activeRecordSockets.clear()

        // Stop AudioRecord
        stopAudioRecord()

        // Release AudioTrack
        synchronized(audioTrackLock) {
            try {
                audioTrack?.pause()
                audioTrack?.flush()
                audioTrack?.stop()
                audioTrack?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing AudioTrack", e)
            }
            audioTrack = null
        }
    }

    private fun startPlaybackServer() {
        thread(name = "WorkspaceAudioBridge-PlaybackAcceptor", isDaemon = true) {
            try {
                val server = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(InetAddress.getByName(BIND_HOST), PORT_PLAYBACK))
                }
                playbackServerSocket = server
                Log.i(TAG, "Playback server listening on $BIND_HOST:$PORT_PLAYBACK")

                while (isRunning.get()) {
                    val socket = try {
                        server.accept()
                    } catch (_: SocketException) {
                        break
                    }
                    handleIncomingSocket(socket)
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.e(TAG, "Playback server error on port $PORT_PLAYBACK", e)
                }
            }
        }
    }

    private fun startRecordServer() {
        thread(name = "WorkspaceAudioBridge-RecordAcceptor", isDaemon = true) {
            try {
                val server = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(InetAddress.getByName(BIND_HOST), PORT_RECORD))
                }
                recordServerSocket = server
                Log.i(TAG, "Record server listening on $BIND_HOST:$PORT_RECORD")

                while (isRunning.get()) {
                    val socket = try {
                        server.accept()
                    } catch (_: SocketException) {
                        break
                    }
                    registerRecordClient(socket)
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.e(TAG, "Record server error on port $PORT_RECORD", e)
                }
            }
        }
    }

    private fun handleIncomingSocket(socket: Socket) {
        thread(name = "WorkspaceAudioBridge-Client-${socket.port}", isDaemon = true) {
            try {
                val input = socket.getInputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

                // Peek initial read to determine if this connection is a command or raw PCM
                var bytesRead = input.read(buffer)
                if (bytesRead <= 0) {
                    socket.close()
                    return@thread
                }

                // Check for text command prefix (e.g. REC, MIC, RECORD)
                val prefix = String(buffer, 0, minOf(bytesRead, 32)).trim()
                if (prefix.startsWith("REC") || prefix.startsWith("MIC")) {
                    registerRecordClient(socket)
                    return@thread
                }

                // Otherwise, this is incoming PCM audio for speaker playback
                activePlaybackSockets.add(socket)
                val track = getOrCreateAudioTrack(sampleRate = 44100, channels = 2)

                // Write the initial buffer
                if (bytesRead > 0) {
                    track.write(buffer, 0, bytesRead)
                }

                while (isRunning.get() && !socket.isClosed) {
                    bytesRead = input.read(buffer)
                    if (bytesRead < 0) break
                    if (bytesRead > 0) {
                        track.write(buffer, 0, bytesRead)
                    }
                }
            } catch (e: Exception) {
                if (e !is SocketException) {
                    Log.d(TAG, "Playback socket ended: ${e.message}")
                }
            } finally {
                activePlaybackSockets.remove(socket)
                runCatching { socket.close() }
                synchronized(audioTrackLock) {
                    if (activePlaybackSockets.isEmpty()) {
                        audioTrack?.pause()
                        audioTrack?.flush()
                    }
                }
            }
        }
    }

    private fun registerRecordClient(socket: Socket) {
        activeRecordSockets.add(socket)
        startAudioRecord()

        thread(name = "WorkspaceAudioBridge-RecordWatcher-${socket.port}", isDaemon = true) {
            try {
                // Monitor the socket: if client disconnects, input.read() returns -1
                val input = socket.getInputStream()
                val dummy = ByteArray(16)
                while (isRunning.get() && !socket.isClosed) {
                    if (input.read(dummy) < 0) break
                }
            } catch (_: Exception) {
            } finally {
                activeRecordSockets.remove(socket)
                runCatching { socket.close() }
                if (activeRecordSockets.isEmpty()) {
                    stopAudioRecord()
                }
            }
        }
    }

    private fun getOrCreateAudioTrack(sampleRate: Int, channels: Int): AudioTrack {
        synchronized(audioTrackLock) {
            audioTrack?.let { existing ->
                if (existing.sampleRate == sampleRate &&
                    existing.channelCount == channels &&
                    existing.state == AudioTrack.STATE_INITIALIZED
                ) {
                    if (existing.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        existing.play()
                    }
                    return existing
                }
                try {
                    existing.stop()
                    existing.release()
                } catch (_: Exception) {}
            }

            val channelConfig = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = (minBufferSize * 2).coerceAtLeast(DEFAULT_BUFFER_SIZE)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track.play()
            audioTrack = track
            return track
        }
    }

    private fun startAudioRecord() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "android.permission.RECORD_AUDIO not granted; cannot stream microphone packets")
            return
        }

        synchronized(recordLock) {
            if (isRecording.get()) return

            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = (minBufferSize * 2).coerceAtLeast(DEFAULT_BUFFER_SIZE)

            val record = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize AudioRecord", e)
                return
            }

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord state is not initialized")
                runCatching { record.release() }
                return
            }

            record.startRecording()
            audioRecord = record
            isRecording.set(true)
            Log.i(TAG, "AudioRecord started at ${sampleRate}Hz mono")

            recordThread = thread(name = "WorkspaceAudioBridge-RecordLoop", isDaemon = true) {
                val pcmBuffer = ByteArray(bufferSize)
                while (isRecording.get() && !Thread.currentThread().isInterrupted) {
                    val read = record.read(pcmBuffer, 0, pcmBuffer.size)
                    if (read > 0) {
                        val clients = activeRecordSockets.toList()
                        for (client in clients) {
                            try {
                                val out = client.getOutputStream()
                                out.write(pcmBuffer, 0, read)
                                out.flush()
                            } catch (e: Exception) {
                                activeRecordSockets.remove(client)
                                runCatching { client.close() }
                            }
                        }
                        if (activeRecordSockets.isEmpty()) {
                            stopAudioRecord()
                            break
                        }
                    } else if (read < 0) {
                        Log.w(TAG, "AudioRecord read returned error: $read")
                        break
                    }
                }
            }
        }
    }

    private fun stopAudioRecord() {
        synchronized(recordLock) {
            if (!isRecording.compareAndSet(true, false)) return
            Log.i(TAG, "Stopping AudioRecord...")

            recordThread?.interrupt()
            recordThread = null

            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing AudioRecord", e)
            }
            audioRecord = null
        }
    }

    companion object {
        const val BIND_HOST = "127.0.0.1"
        const val PORT_PLAYBACK = 4713
        const val PORT_RECORD = 4714
        private const val DEFAULT_BUFFER_SIZE = 4096
        private const val TAG = "WorkspaceAudioBridge"

        @Volatile
        private var instance: WorkspaceAudioBridge? = null

        fun getInstance(context: Context): WorkspaceAudioBridge {
            return instance ?: synchronized(this) {
                instance ?: WorkspaceAudioBridge(context.applicationContext).also { instance = it }
            }
        }
    }
}
