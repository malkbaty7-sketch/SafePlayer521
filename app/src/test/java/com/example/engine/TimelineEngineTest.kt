package com.example.engine

import com.example.data.EditingOperation
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineEngineTest {

    @Test
    fun `test output duration without operations`() {
        val engine = TimelineEngine(10000L, emptyList())
        assertEquals(10000L, engine.calculateOutputDuration())
    }

    @Test
    fun `test output duration with single delete`() {
        val ops = listOf(
            EditingOperation(projectId = 1, type = "DELETE", startTimeMs = 2000L, endTimeMs = 4000L)
        )
        val engine = TimelineEngine(10000L, ops)
        assertEquals(8000L, engine.calculateOutputDuration())
    }

    @Test
    fun `test output duration with overlapping deletes`() {
        val ops = listOf(
            EditingOperation(projectId = 1, type = "DELETE", startTimeMs = 2000L, endTimeMs = 5000L),
            EditingOperation(projectId = 1, type = "DELETE", startTimeMs = 4000L, endTimeMs = 7000L)
        )
        val engine = TimelineEngine(10000L, ops)
        // Overlap: 2000 to 7000 -> 5000 deleted. 10000 - 5000 = 5000L
        assertEquals(5000L, engine.calculateOutputDuration())
    }

    @Test
    fun `test map source to output time before delete`() {
        val ops = listOf(EditingOperation(projectId = 1, type = "DELETE", startTimeMs = 5000L, endTimeMs = 7000L))
        val engine = TimelineEngine(10000L, ops)
        assertEquals(2000L, engine.mapSourceToOutputTime(2000L))
    }

    @Test
    fun `test map source to output time during delete`() {
        val ops = listOf(EditingOperation(projectId = 1, type = "DELETE", startTimeMs = 5000L, endTimeMs = 7000L))
        val engine = TimelineEngine(10000L, ops)
        assertEquals(-1L, engine.mapSourceToOutputTime(6000L))
    }

    @Test
    fun `test map source to output time after delete`() {
        val ops = listOf(EditingOperation(projectId = 1, type = "DELETE", startTimeMs = 5000L, endTimeMs = 7000L))
        val engine = TimelineEngine(10000L, ops)
        // 8000L -> Shifted back by 2000L -> 6000L
        assertEquals(6000L, engine.mapSourceToOutputTime(8000L))
    }
    
    @Test
    fun `test frame accuracy conversion`() {
        val engine = TimelineEngine(10000L, emptyList())
        val fps = 30.0
        val frames = engine.timeToFrames(2000L, fps)
        assertEquals(60L, frames)
        
        val time = engine.framesToTime(60L, fps)
        assertEquals(2000L, time)
    }

    @Test
    fun `test boundary delete at start`() {
        val ops = listOf(EditingOperation(projectId = 1, type = "DELETE", startTimeMs = 0L, endTimeMs = 2000L))
        val engine = TimelineEngine(10000L, ops)
        assertEquals(8000L, engine.calculateOutputDuration())
        assertEquals(-1L, engine.mapSourceToOutputTime(1000L))
        assertEquals(0L, engine.mapSourceToOutputTime(2000L))
    }

    @Test
    fun `test boundary delete at end`() {
        val ops = listOf(EditingOperation(projectId = 1, type = "DELETE", startTimeMs = 8000L, endTimeMs = 12000L))
        val engine = TimelineEngine(10000L, ops)
        assertEquals(8000L, engine.calculateOutputDuration())
        assertEquals(-1L, engine.mapSourceToOutputTime(9000L))
    }
}
