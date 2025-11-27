package com.yourname.smartrecorder.domain.usecase

import com.yourname.smartrecorder.core.logging.AppLogger
import com.yourname.smartrecorder.core.logging.AppLogger.TAG_TRANSCRIPT
import com.yourname.smartrecorder.domain.model.TranscriptSegment

/**
 * Helper class for speaker segmentation based on "Speaker one/two/three..." markers in audio.
 * This replaces heuristic-based detection (question marks, time gaps) with marker-based detection.
 */
object SpeakerSegmentationHelper {
    
    /**
     * Regex pattern to detect "Speaker one", "Speaker two", "Speaker 1", "Speaker 2", etc.
     * Case-insensitive, supports both word and numeric formats.
     */
    private val speakerRegex = Regex(
        pattern = """\bspeaker\s+(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|[0-9]+)\b""",
        option = RegexOption.IGNORE_CASE
    )
    
    /**
     * Convert word token to number (e.g., "one" -> 1, "two" -> 2)
     */
    private fun wordToNumber(token: String): Int? {
        return when (token.lowercase()) {
            "one" -> 1
            "two" -> 2
            "three" -> 3
            "four" -> 4
            "five" -> 5
            "six" -> 6
            "seven" -> 7
            "eight" -> 8
            "nine" -> 9
            "ten" -> 10
            "eleven" -> 11
            "twelve" -> 12
            else -> token.toIntOrNull()
        }
    }
    
    /**
     * Represents a detected "Speaker X" marker in the transcript.
     */
    data class SpeakerMarker(
        val segmentIndex: Int,
        val speakerIndex: Int,      // 1, 2, 3, ...
        val matchText: String,      // "Speaker one", "Speaker 1", etc.
        val startTimeMs: Long,     // Timestamp of marker in audio
        val charOffset: Int         // Character offset within segment text
    )
    
    /**
     * Represents a speaker block (continuous speech from one speaker).
     */
    data class SpeakerBlock(
        val speakerIndex: Int,
        val speakerLabel: String,  // "Speaker 1", "Speaker 2", etc.
        val startTimeMs: Long,
        val endTimeMs: Long,
        val text: String,            // Full text of the block (without "Speaker X" prefix)
        val segmentIndices: List<Int> // Original segment indices included in this block
    )
    
    /**
     * Detect all "Speaker X" markers in the transcript segments.
     */
    fun detectSpeakerMarkers(segments: List<TranscriptSegment>): List<SpeakerMarker> {
        val markers = mutableListOf<SpeakerMarker>()
        
        segments.forEachIndexed { segIndex, segment ->
            val textLower = segment.text.lowercase()
            speakerRegex.findAll(textLower).forEach { match ->
                val speakerToken = match.groupValues[1] // "one", "two", "1", "2", etc.
                val speakerIndex = wordToNumber(speakerToken) ?: return@forEach
                
                // Calculate marker timestamp within segment
                val charOffset = match.range.first.coerceAtLeast(0)
                val textLength = segment.text.length
                val ratio = if (textLength > 0) {
                    charOffset.toDouble() / textLength.toDouble()
                } else 0.0
                
                val segmentDuration = segment.endTimeMs - segment.startTimeMs
                val markerTimeMs = segment.startTimeMs + (segmentDuration * ratio).toLong()
                
                markers.add(
                    SpeakerMarker(
                        segmentIndex = segIndex,
                        speakerIndex = speakerIndex,
                        matchText = match.value,  // "speaker one", "speaker 1", etc.
                        startTimeMs = markerTimeMs,
                        charOffset = charOffset
                    )
                )
            }
        }
        
        return markers.sortedBy { it.startTimeMs }
    }
    
    /**
     * Build speaker blocks from segments and markers.
     * Each block represents continuous speech from one speaker.
     */
    fun buildSpeakerBlocks(
        segments: List<TranscriptSegment>,
        markers: List<SpeakerMarker>
    ): List<SpeakerBlock> {
        if (markers.isEmpty()) {
            // Fallback: no markers detected → single block with all segments
            val allText = segments.joinToString(" ") { it.text.trim() }
            AppLogger.w(TAG_TRANSCRIPT, "No speaker markers detected, using single block for all segments")
            return listOf(
                SpeakerBlock(
                    speakerIndex = 1,
                    speakerLabel = "Speaker 1",
                    startTimeMs = segments.firstOrNull()?.startTimeMs ?: 0L,
                    endTimeMs = segments.lastOrNull()?.endTimeMs ?: 0L,
                    text = allText,
                    segmentIndices = segments.mapIndexed { index, _ -> index }
                )
            )
        }
        
        val blocks = mutableListOf<SpeakerBlock>()
        
        for (i in markers.indices) {
            val marker = markers[i]
            val nextMarkerStart = markers.getOrNull(i + 1)?.startTimeMs
                ?: segments.lastOrNull()?.endTimeMs
                ?: marker.startTimeMs
            
            // Find all segments that belong to this speaker block
            // A segment belongs to a block if:
            // 1. It starts after the marker (or at the marker)
            // 2. It ends before the next marker (or at the next marker)
            val includedSegments = segments.filterIndexed { segIndex, seg ->
                seg.startTimeMs >= marker.startTimeMs && seg.endTimeMs <= nextMarkerStart
            }
            
            // If no segments found with strict criteria, use overlap-based inclusion
            val finalIncludedSegments = if (includedSegments.isEmpty()) {
                segments.filterIndexed { segIndex, seg ->
                    // Include segment if it overlaps with [marker.startTimeMs, nextMarkerStart)
                    seg.endTimeMs > marker.startTimeMs && seg.startTimeMs < nextMarkerStart
                }
            } else {
                includedSegments
            }
            
            // Build text for this block
            val textBuilder = StringBuilder()
            finalIncludedSegments.forEachIndexed { idx, seg ->
                var segText = seg.text
                
                // For the first segment containing the marker, remove "Speaker X" prefix
                if (idx == 0 && seg.startTimeMs <= marker.startTimeMs && seg.endTimeMs >= marker.startTimeMs) {
                    val lower = segText.lowercase()
                    val match = speakerRegex.find(lower)
                    if (match != null) {
                        // Remove "Speaker X" and any following punctuation/spaces
                        val afterMatch = segText.substring(match.range.last + 1).trimStart()
                        segText = afterMatch
                    }
                }
                
                if (segText.isNotBlank()) {
                    if (textBuilder.isNotEmpty()) {
                        textBuilder.append(" ")
                    }
                    textBuilder.append(segText.trim())
                }
            }
            
            blocks.add(
                SpeakerBlock(
                    speakerIndex = marker.speakerIndex,
                    speakerLabel = "Speaker ${marker.speakerIndex}",
                    startTimeMs = marker.startTimeMs,
                    endTimeMs = nextMarkerStart,
                    text = textBuilder.toString(),
                    segmentIndices = finalIncludedSegments.mapIndexed { _, seg -> 
                        segments.indexOf(seg)
                    }.filter { it >= 0 }
                )
            )
        }
        
        return blocks
    }
    
    /**
     * Convert speaker blocks back to TranscriptSegments with speaker assignments.
     * This preserves the original Whisper segments but assigns speakers based on blocks.
     */
    fun assignSpeakersToSegments(
        originalSegments: List<TranscriptSegment>,
        blocks: List<SpeakerBlock>
    ): List<TranscriptSegment> {
        return originalSegments.mapIndexed { segIndex, segment ->
            // Find which block this segment belongs to
            val block = blocks.find { block ->
                block.segmentIndices.contains(segIndex)
            }
            
            segment.copy(
                speaker = block?.speakerIndex ?: 1  // Default to Speaker 1 if no block found
            )
        }
    }
    
    /**
     * Log Whisper raw segments for debugging.
     */
    fun logWhisperRaw(segments: List<TranscriptSegment>) {
        AppLogger.d(TAG_TRANSCRIPT, "=== [WHISPER_RAW_SEGMENTS] ===")
        segments.forEachIndexed { index, seg ->
            val preview = if (seg.text.length > 60) seg.text.take(57) + "..." else seg.text
            AppLogger.d(TAG_TRANSCRIPT, 
                "#%d start=%.2fs end=%.2fs text=\"%s\"",
                index,
                seg.startTimeMs / 1000.0,
                seg.endTimeMs / 1000.0,
                preview
            )
        }
    }
    
    /**
     * Log detected speaker markers.
     */
    fun logSpeakerMarkers(markers: List<SpeakerMarker>) {
        AppLogger.d(TAG_TRANSCRIPT, "=== [SPEAKER_MARKERS_DETECTED] ===")
        if (markers.isEmpty()) {
            AppLogger.w(TAG_TRANSCRIPT, "No speaker markers found in transcript")
            return
        }
        markers.forEachIndexed { i, m ->
            AppLogger.d(TAG_TRANSCRIPT,
                "marker#%d segmentIndex=%d time=%.2fs speaker=%d match=\"%s\"",
                i, m.segmentIndex, m.startTimeMs / 1000.0, m.speakerIndex, m.matchText
            )
        }
    }
    
    /**
     * Log speaker blocks (final timeline).
     */
    fun logSpeakerBlocks(blocks: List<SpeakerBlock>) {
        AppLogger.d(TAG_TRANSCRIPT, "=== [SPEAKER_BLOCKS_FINAL] ===")
        blocks.forEachIndexed { i, b ->
            val preview = if (b.text.length > 80) b.text.take(77) + "..." else b.text
            AppLogger.d(TAG_TRANSCRIPT,
                "block#%d speaker=%s start=%.2fs end=%.2fs segments=%s textPreview=\"%s\"",
                i, b.speakerLabel, b.startTimeMs / 1000.0, b.endTimeMs / 1000.0,
                b.segmentIndices.joinToString(","), preview
            )
        }
    }
    
    /**
     * Log final segments with speaker assignments.
     */
    fun logFinalSegments(segments: List<TranscriptSegment>) {
        AppLogger.d(TAG_TRANSCRIPT, "=== [FINAL_SEGMENTS_WITH_SPEAKERS] ===")
        segments.forEachIndexed { index, seg ->
            val preview = if (seg.text.length > 60) seg.text.take(57) + "..." else seg.text
            AppLogger.d(TAG_TRANSCRIPT,
                "#%d speaker=%d start=%.2fs end=%.2fs text=\"%s\"",
                index, seg.speaker ?: 0, seg.startTimeMs / 1000.0, seg.endTimeMs / 1000.0, preview
            )
        }
    }
}

