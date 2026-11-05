package com.nexus.portal.ui.screens.hub.apps.mictester

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

data class MicSource(
    val id: Int,
    val name: String,
    val info: AudioDeviceInfo?
)

object MicAudioManager {

    // Lists valid physical input microphones only (deduplicated, matching MicTest Pro style)
    fun getAvailableMicrophones(context: Context): List<MicSource> {
        val list = mutableListOf<MicSource>()
        list.add(MicSource(-1, "Default System Microphone", null))
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            
            // Filter only functional physical mic ports
            val validTypes = setOf(
                AudioDeviceInfo.TYPE_BUILTIN_MIC,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_HEADSET
            )
            
            val seenLabels = mutableSetOf<String>()
            
            devices.forEach { d ->
                if (validTypes.contains(d.type)) {
                    val productName = d.productName.toString()
                    val label = when (d.type) {
                        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "INT $productName"
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BT $productName"
                        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset ($productName)"
                        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB $productName"
                        else -> productName
                    }
                    // Deduplicate identical hardware names (e.g. multiple internal channels)
                    if (seenLabels.add(label)) {
                        list.add(MicSource(d.id, label, d))
                    }
                }
            }
        }
        return list
    }

    // Loopback Engine for realtime loopback, echo dsp, bluetooth SCO routing, and dynamic hot-switching
    class LoopbackEngine {
        private var isRecording = false
        private var thread: Thread? = null
        var echoDelayMs = 250
        var echoDecay = 0.4f // 0f to 0.8f
        
        @Volatile
        var inputDevice: AudioDeviceInfo? = null

        @Volatile
        private var audioRecordInstance: AudioRecord? = null

        // Reactive stream feeding amplitude visualizer
        val amplitudeFlow = MutableStateFlow(0f)

        // Safe hot-switching: Tear down and rebuild routing safely to reset audio streams
        fun switchDevice(context: Context, device: AudioDeviceInfo?) {
            val wasRecording = isRecording
            if (wasRecording) {
                stop()
            }
            
            inputDevice = device
            
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (device != null && device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                } else {
                    try {
                        audioManager.mode = AudioManager.MODE_NORMAL
                        audioManager.stopBluetoothSco()
                        audioManager.isBluetoothScoOn = false
                    } catch (e: Exception) {}
                }
            }
            
            if (wasRecording) {
                start(context)
            }
        }

        fun start(context: Context) {
            if (isRecording) return
            isRecording = true
            thread = Thread {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                
                val isBluetooth = inputDevice?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                
                if (isBluetooth) {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                    // Wait for Bluetooth SCO stream connection to settle
                    try { Thread.sleep(300) } catch (e: Exception) {}
                } else {
                    audioManager.mode = AudioManager.MODE_NORMAL
                    try {
                        audioManager.stopBluetoothSco()
                        audioManager.isBluetoothScoOn = false
                    } catch (e: Exception) {}
                }

                val sampleRate = 44100
                val bufferSize = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                // Dynamic AudioSource allocation: Bluetooth SCO requires VOICE_COMMUNICATION to stream sound bytes
                val audioSource = if (isBluetooth) {
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION
                } else {
                    MediaRecorder.AudioSource.MIC
                }

                val audioRecord = try {
                    AudioRecord(
                        audioSource,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                    )
                } catch (e: Exception) {
                    isRecording = false
                    return@Thread
                }

                audioRecordInstance = audioRecord

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    inputDevice?.let { device ->
                        audioRecord.preferredDevice = device
                    }
                }

                // If bluetooth, play on STREAM_VOICE_CALL to route to Bluetooth SCO transceiver correctly
                val streamType = if (isBluetooth) {
                    AudioManager.STREAM_VOICE_CALL
                } else {
                    AudioManager.STREAM_MUSIC
                }

                val audioTrack = AudioTrack(
                    streamType,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STREAM
                )
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isBluetooth) {
                    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    val btOutput = devices.firstOrNull { 
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP 
                    }
                    if (btOutput != null) {
                        audioTrack.preferredDevice = btOutput
                    }
                }

                // Echo circular buffer
                val maxHistorySize = sampleRate * 1
                val history = ShortArray(maxHistorySize)
                var historyIndex = 0

                val buffer = ShortArray(1024)
                
                try {
                    audioRecord.startRecording()
                    audioTrack.play()
                } catch (e: Exception) {
                    isRecording = false
                    audioRecordInstance = null
                    return@Thread
                }

                while (isRecording) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        // Calculate real-time RMS
                        var sum = 0.0
                        for (i in 0 until read) {
                            sum += buffer[i] * buffer[i]
                        }
                        val rms = Math.sqrt(sum / read)
                        val normalized = (rms / 12000.0).toFloat().coerceIn(0f, 1f)
                        amplitudeFlow.value = normalized

                        // Apply in-flight Echo DSP
                        val delaySamples = (sampleRate * (echoDelayMs / 1000.0)).toInt().coerceIn(0, maxHistorySize - 1)
                        if (delaySamples > 0 && echoDecay > 0f) {
                            for (i in 0 until read) {
                                val pastIndex = (historyIndex - delaySamples + maxHistorySize) % maxHistorySize
                                val originalVal = buffer[i].toInt()
                                val echoVal = history[pastIndex].toInt()
                                
                                val blended = (originalVal + echoVal * echoDecay).toInt().coerceIn(-32768, 32767)
                                buffer[i] = blended.toShort()
                                
                                history[historyIndex] = buffer[i]
                                historyIndex = (historyIndex + 1) % maxHistorySize
                            }
                        } else {
                            // Copy to delay history
                            for (i in 0 until read) {
                                history[historyIndex] = buffer[i]
                                historyIndex = (historyIndex + 1) % maxHistorySize
                            }
                        }

                        audioTrack.write(buffer, 0, read)
                    }
                }

                try {
                    audioRecord.stop()
                    audioRecord.release()
                    audioTrack.stop()
                    audioTrack.release()
                } catch (e: Exception) {}

                // Clean up Bluetooth SCO routing
                try {
                    audioManager.mode = AudioManager.MODE_NORMAL
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                } catch (e: Exception) {}

                audioRecordInstance = null
            }
            thread?.start()
        }

        fun stop() {
            isRecording = false
            thread = null
            amplitudeFlow.value = 0f
        }
    }

    // Voice testing recorder with dynamic Bluetooth-friendly AudioSource routing
    class VoiceClipRecorder {
        private var mediaRecorder: MediaRecorder? = null
        private var mediaPlayer: MediaPlayer? = null
        var isRecording = false
        var isPlaying = false

        fun startRecording(context: Context, filePath: String, selectedMic: MicSource?) {
            if (isRecording) return
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val isBluetooth = selectedMic?.info?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                
                if (isBluetooth) {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                } else {
                    audioManager.mode = AudioManager.MODE_NORMAL
                    try {
                        audioManager.stopBluetoothSco()
                        audioManager.isBluetoothScoOn = false
                    } catch (e: Exception) {}
                }

                val audioSource = if (isBluetooth) {
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION
                } else {
                    MediaRecorder.AudioSource.MIC
                }

                mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }.apply {
                    setAudioSource(audioSource)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(filePath)
                    prepare()
                    // Give a small delay for SCO routing to apply
                    if (isBluetooth) {
                        try { Thread.sleep(300) } catch (e: Exception) {}
                    }
                    start()
                }
                isRecording = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stopRecording(context: Context) {
            if (!isRecording) return
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            mediaRecorder = null
            isRecording = false

            // Clean up Bluetooth SCO routing
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            try {
                audioManager.mode = AudioManager.MODE_NORMAL
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            } catch (e: Exception) {}
        }

        fun startPlaying(filePath: String, onComplete: () -> Unit) {
            if (isPlaying) return
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(filePath)
                    prepare()
                    start()
                    setOnCompletionListener {
                        stopPlaying()
                        onComplete()
                    }
                }
                isPlaying = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stopPlaying() {
            if (!isPlaying) return
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            mediaPlayer = null
            isPlaying = false
        }
    }

    // Synthesizer backing loops arpeggiator
    class SynthBackingTrackPlayer {
        private var isPlaying = false
        private var thread: Thread? = null

        fun start() {
            if (isPlaying) return
            isPlaying = true
            thread = Thread {
                val sampleRate = 44100
                val bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                val audioTrack = try {
                    AudioTrack(
                        AudioManager.STREAM_MUSIC, sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize, AudioTrack.MODE_STREAM
                    )
                } catch (e: Exception) {
                    isPlaying = false
                    return@Thread
                }
                
                try {
                    audioTrack.play()
                } catch (e: Exception) {
                    isPlaying = false
                    return@Thread
                }

                val notes = doubleArrayOf(
                    130.81, 164.81, 196.00, 246.94,
                    146.83, 174.61, 220.00, 261.63,
                    220.00, 261.63, 329.63, 440.00
                )
                var noteIndex = 0
                var phase = 0.0

                val sampleBuffer = ShortArray(1024)

                while (isPlaying) {
                    val freq = notes[noteIndex]
                    val noteDurationSamples = sampleRate / 2
                    
                    for (s in 0 until noteDurationSamples step sampleBuffer.size) {
                        if (!isPlaying) break
                        val count = Math.min(sampleBuffer.size, noteDurationSamples - s)
                        for (i in 0 until count) {
                            val wave = Math.sin(phase) + 0.4 * Math.sin(2.0 * phase) + 0.15 * Math.sin(3.0 * phase)
                            val volumeDecay = (noteDurationSamples - (s + i)).toDouble() / noteDurationSamples
                            sampleBuffer[i] = (wave * 5500.0 * volumeDecay).toInt().toShort()
                            
                            phase += 2.0 * Math.PI * freq / sampleRate
                            if (phase > 2.0 * Math.PI) {
                                phase -= 2.0 * Math.PI
                            }
                        }
                        audioTrack.write(sampleBuffer, 0, count)
                    }
                    noteIndex = (noteIndex + 1) % notes.size
                }

                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (e: Exception) {}
            }
            thread?.start()
        }

        fun stop() {
            isPlaying = false
            thread = null
        }
    }
}
