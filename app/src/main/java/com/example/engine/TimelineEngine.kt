package com.example.engine

import com.example.data.EditingOperation
import kotlin.math.max
import kotlin.math.min

/**
 * Core engine for calculating mapped timeline based on destructive and non-destructive operations.
 */
class TimelineEngine(
    val sourceDurationMs: Long,
    val operations: List<EditingOperation>
) {
    data class TimeMapping(val sourceTimeMs: Long, val outputTimeMs: Long)

    // Calculates the total duration after removing "DELETE" operations.
    fun calculateOutputDuration(): Long {
        val deleteOps = operations.filter { it.type == "DELETE" && it.isEnabled }
            .sortedBy { it.startTimeMs }
        
        var effectiveDuration = sourceDurationMs
        var overlapResolved = resolveOverlaps(deleteOps)
        
        for (op in overlapResolved) {
            val start = max(0, op.startTimeMs)
            val end = min(sourceDurationMs, op.endTimeMs)
            if (start < end) {
                effectiveDuration -= (end - start)
            }
        }
        return max(0, effectiveDuration)
    }

    // Maps a source time to output time (e.g., after deletes).
    // Returns -1 if the source time falls within a deleted segment.
    fun mapSourceToOutputTime(sourceTimeMs: Long): Long {
        if (sourceTimeMs < 0 || sourceTimeMs > sourceDurationMs) return -1
        
        val deleteOps = operations.filter { it.type == "DELETE" && it.isEnabled }
            .sortedBy { it.startTimeMs }
        val overlapsResolved = resolveOverlaps(deleteOps)
        
        var outputTime = sourceTimeMs
        
        for (op in overlapsResolved) {
            val start = max(0, op.startTimeMs)
            val end = min(sourceDurationMs, op.endTimeMs)
            
            if (sourceTimeMs in start until end) {
                return -1 // Time is deleted
            } else if (sourceTimeMs >= end) {
                outputTime -= (end - start)
            }
        }
        
        return max(0, outputTime)
    }
    
    // Convert time to frames based on frame rate
    fun timeToFrames(timeMs: Long, fps: Double): Long {
        return (timeMs * fps / 1000.0).toLong()
    }
    
    fun framesToTime(frames: Long, fps: Double): Long {
        return (frames * 1000.0 / fps).toLong()
    }

    private fun resolveOverlaps(ops: List<EditingOperation>): List<EditingOperation> {
        if (ops.isEmpty()) return emptyList()
        val sorted = ops.sortedBy { it.startTimeMs }
        val merged = mutableListOf<EditingOperation>()
        
        var current = sorted[0]
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (current.endTimeMs >= next.startTimeMs) {
                current = current.copy(endTimeMs = max(current.endTimeMs, next.endTimeMs))
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        return merged
    }
}
