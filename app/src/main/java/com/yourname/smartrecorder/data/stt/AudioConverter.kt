package com.yourname.smartrecorder.data.stt

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_TRANSCRIPT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AudioConverter: Converts audio files to WAV PCM 16kHz mono format
 * 
 * Supported Formats: MP3, M4A, WAV, OGG, FLAC → WAV PCM 16kHz mono 16-bit
 * 
 * Output Format:
 * - Sample Rate: 16kHz (Whisper requirement)
 * - Channels: Mono (Whisper requirement)
 * - Bit Depth: 16-bit PCM
 * - Format: WAV with proper RIFF header
 */
@Singleton
class AudioConverter @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AudioConverter"
        private const val SAMPLE_RATE = 16000 // Whisper requirement: 16kHz
        private const val CHANNELS = 1 // Whisper requirement: Mono
        private const val BIT_DEPTH = 16 // Whisper requirement: 16-bit
    }
    
    /**
     * Convert audio file to WAV format (16kHz, mono, 16-bit PCM)
     * Uses Android MediaCodec for real conversion
     * 
     * Supports both Uri and File path
     */
    suspend fun convertToWav(
        uri: Uri,
        onProgress: (Int) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val outputFile = File(context.cacheDir, "input_converted_${System.currentTimeMillis()}.wav")
        var extractor: MediaExtractor? = null
        
        try {
            onProgress(5)
            
            // Step 1: Create MediaExtractor and set data source
            extractor = MediaExtractor()
            // Try to use file path if it's a file:// URI, otherwise use context
            if (uri.scheme == "file") {
                extractor.setDataSource(uri.path ?: "")
            } else {
                extractor.setDataSource(context, uri, null)
            }
            
            // Step 2: Find audio track
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }
            
            if (audioTrackIndex == -1 || audioFormat == null) {
                throw IllegalStateException("No audio track found in file")
            }
            
            onProgress(15)
            
            // Step 3: Get audio properties
            val inputSampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val inputChannels = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mimeType = audioFormat.getString(MediaFormat.KEY_MIME) ?: ""
            
            // Step 4: Select track
            extractor.selectTrack(audioTrackIndex)
            
            // Step 5: Create MediaCodec decoder
            var codec: MediaCodec? = null
            var pcmOutputStream: FileOutputStream? = null
            var tempPcmFile: File? = null
            
            try {
                codec = MediaCodec.createDecoderByType(mimeType)
                codec.configure(audioFormat, null, null, 0)
                codec.start()
                
                // Create temp file for PCM data
                tempPcmFile = File(context.cacheDir, "temp_pcm_${System.currentTimeMillis()}.raw")
                pcmOutputStream = FileOutputStream(tempPcmFile)
                
                var totalBytes = 0L
                var sawInputEOS = false
                var sawOutputEOS = false
                val timeoutUs = 10000L // 10ms timeout
                
                onProgress(20)
                
                // Step 6: Decode audio using MediaCodec
                while (!sawOutputEOS) {
                    // Feed encoded data to decoder
                    if (!sawInputEOS) {
                        val inputBufferIndex = codec.dequeueInputBuffer(timeoutUs)
                        if (inputBufferIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                            if (inputBuffer != null) {
                                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                                
                                if (sampleSize < 0) {
                                    codec.queueInputBuffer(
                                        inputBufferIndex, 0, 0, 0,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    sawInputEOS = true
                                } else {
                                    val presentationTimeUs = extractor.sampleTime
                                    codec.queueInputBuffer(
                                        inputBufferIndex, 0, sampleSize,
                                        presentationTimeUs, 0
                                    )
                                    extractor.advance()
                                }
                            }
                        }
                    }
                    
                    // Get decoded PCM data
                    val bufferInfo = MediaCodec.BufferInfo()
                    val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                    
                    if (outputBufferIndex >= 0) {
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEOS = true
                        }
                        
                        if (bufferInfo.size > 0) {
                            val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                            if (outputBuffer != null) {
                                val pcmChunk = ByteArray(bufferInfo.size)
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                outputBuffer.get(pcmChunk)
                                
                                pcmOutputStream?.write(pcmChunk)
                                totalBytes += bufferInfo.size
                                
                                // Progress calculation (simplified - needs improvement)
                                val estimatedTotal = totalBytes * 2 // Rough estimate
                                val progress = 20 + ((totalBytes * 60) / (estimatedTotal + 1024 * 1024)).toInt()
                                onProgress(progress.coerceAtMost(80))
                            }
                        }
                        
                        codec.releaseOutputBuffer(outputBufferIndex, false)
                    }
                }
                
                pcmOutputStream?.close()
                pcmOutputStream = null
                
                // Cleanup MediaCodec
                codec?.stop()
                codec?.release()
                codec = null
                
                // tempPcmFile is guaranteed to be non-null at this point (assigned on line 107)
                val pcmFile = tempPcmFile!!
                
                if (totalBytes == 0L || !pcmFile.exists() || pcmFile.length() == 0L) {
                    pcmFile.delete()
                    throw IllegalStateException("No PCM data decoded from audio file")
                }
                
                onProgress(85)
                
                // Step 7: Read PCM data from file
                val rawPcmData = pcmFile.readBytes()
                pcmFile.delete()
                
                // Step 8: Convert to mono if needed
                val monoPcmData = if (inputChannels > 1) {
                    convertToMono(rawPcmData, inputChannels)
                } else {
                    rawPcmData
                }
                
                // Step 9: Resample to 16kHz if needed
                val convertedPcm = if (inputSampleRate != SAMPLE_RATE) {
                    resampleAudio(monoPcmData, inputSampleRate, SAMPLE_RATE)
                } else {
                    monoPcmData
                }
                
                // Step 10: Write WAV file with proper header
                writeWavFile(outputFile, convertedPcm, SAMPLE_RATE, CHANNELS, BIT_DEPTH)
                
                onProgress(100)
                outputFile
                
            } finally {
                pcmOutputStream?.close()
                codec?.stop()
                codec?.release()
                tempPcmFile?.delete()
            }
        } catch (e: Exception) {
            outputFile.delete()
            AppLogger.e(TAG_TRANSCRIPT, "Failed to convert audio file", e)
            throw IllegalStateException("Failed to convert audio file: ${e.message}", e)
        } finally {
            extractor?.release()
        }
    }
    
    /**
     * Convert multi-channel audio to mono by averaging channels
     * Uses ByteBuffer with Little Endian for proper byte order
     */
    private fun convertToMono(pcmData: ByteArray, inputChannels: Int): ByteArray {
        if (inputChannels == 1) {
            return pcmData
        }
        
        val byteBuffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN)
        val totalSamples = pcmData.size / 2
        val samplesPerChannel = totalSamples / inputChannels
        val monoData = ByteArray(samplesPerChannel * 2)
        val monoBuffer = ByteBuffer.wrap(monoData).order(ByteOrder.LITTLE_ENDIAN)
        
        for (i in 0 until samplesPerChannel) {
            var sum = 0
            for (ch in 0 until inputChannels) {
                val sampleIndex = (i * inputChannels + ch) * 2
                if (sampleIndex + 1 < pcmData.size) {
                    byteBuffer.position(sampleIndex)
                    val sample = byteBuffer.short.toInt()
                    sum += sample
                }
            }
            val avgSample = (sum / inputChannels).coerceIn(-32768, 32767)
            monoBuffer.putShort(i * 2, avgSample.toShort())
        }
        
        return monoData
    }
    
    /**
     * Resample audio to target sample rate
     * Note: This is a simplified version. For production, use a proper resampling library
     * Uses ByteBuffer with Little Endian for proper byte order
     */
    private fun resampleAudio(pcmData: ByteArray, fromRate: Int, toRate: Int): ByteArray {
        if (fromRate == toRate) {
            return pcmData
        }
        
        // Simple linear interpolation resampling
        val ratio = fromRate.toDouble() / toRate.toDouble()
        val inputSamples = pcmData.size / 2
        val outputSamples = (inputSamples / ratio).toInt()
        val outputData = ByteArray(outputSamples * 2)
        
        val inputBuffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN)
        val outputBuffer = ByteBuffer.wrap(outputData).order(ByteOrder.LITTLE_ENDIAN)
        
        for (i in 0 until outputSamples) {
            val srcIndex = (i * ratio).toInt()
            if (srcIndex * 2 + 1 < pcmData.size) {
                inputBuffer.position(srcIndex * 2)
                val sample = inputBuffer.short.toInt()
                outputBuffer.putShort(i * 2, sample.toShort())
            }
        }
        
        return outputData
    }
    
    /**
     * Write PCM data to WAV file with proper RIFF header
     */
    private fun writeWavFile(file: File, pcmData: ByteArray, sampleRate: Int, channels: Int, bitDepth: Int) {
        FileOutputStream(file).use { output ->
            val dataSize = pcmData.size
            val fileSize = 36 + dataSize
            
            // WAV header
            output.write("RIFF".toByteArray())
            output.write(intToByteArray(fileSize))
            output.write("WAVE".toByteArray())
            
            output.write("fmt ".toByteArray())
            output.write(intToByteArray(16))
            output.write(shortToByteArray(1)) // PCM
            output.write(shortToByteArray(channels))
            output.write(intToByteArray(sampleRate))
            output.write(intToByteArray(sampleRate * channels * bitDepth / 8))
            output.write(shortToByteArray(channels * bitDepth / 8))
            output.write(shortToByteArray(bitDepth))
            
            output.write("data".toByteArray())
            output.write(intToByteArray(dataSize))
            output.write(pcmData)
        }
    }
    
    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }
    
    private fun shortToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte()
        )
    }
}

