package com.example.ui.editing

import org.json.JSONArray
import org.json.JSONObject

data class Keyframe(
    val timeMs: Long,
    val x: Float? = null,
    val y: Float? = null,
    val width: Float? = null,
    val height: Float? = null
)

data class OperationProperties(
    val x: Float = 0.25f,
    val y: Float = 0.25f,
    val width: Float = 0.5f,
    val height: Float = 0.5f,
    val blur: Int = 20,
    val pixelSize: Int = 16,
    val zOrder: Int = 0,
    val isLocked: Boolean = false,
    val isVisible: Boolean = true,
    val keyframes: List<Keyframe> = emptyList()
) {
    companion object {
        fun fromJson(jsonStr: String): OperationProperties {
            return try {
                val obj = JSONObject(jsonStr)
                val kfArray = obj.optJSONArray("keyframes")
                val keyframes = mutableListOf<Keyframe>()
                if (kfArray != null) {
                    for (i in 0 until kfArray.length()) {
                        val kfObj = kfArray.getJSONObject(i)
                        keyframes.add(
                            Keyframe(
                                timeMs = kfObj.getLong("timeMs"),
                                x = if (kfObj.has("x")) kfObj.getDouble("x").toFloat() else null,
                                y = if (kfObj.has("y")) kfObj.getDouble("y").toFloat() else null,
                                width = if (kfObj.has("width")) kfObj.getDouble("width").toFloat() else null,
                                height = if (kfObj.has("height")) kfObj.getDouble("height").toFloat() else null
                            )
                        )
                    }
                }
                
                OperationProperties(
                    x = obj.optDouble("x", 0.25).toFloat(),
                    y = obj.optDouble("y", 0.25).toFloat(),
                    width = obj.optDouble("width", 0.5).toFloat(),
                    height = obj.optDouble("height", 0.5).toFloat(),
                    blur = obj.optInt("blur", 20),
                    pixelSize = obj.optInt("pixelSize", 16),
                    zOrder = obj.optInt("zOrder", 0),
                    isLocked = obj.optBoolean("isLocked", false),
                    isVisible = obj.optBoolean("isVisible", true),
                    keyframes = keyframes.sortedBy { it.timeMs }
                )
            } catch (e: Exception) {
                OperationProperties()
            }
        }
    }
    
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("x", x.toDouble())
        obj.put("y", y.toDouble())
        obj.put("width", width.toDouble())
        obj.put("height", height.toDouble())
        obj.put("blur", blur)
        obj.put("pixelSize", pixelSize)
        obj.put("zOrder", zOrder)
        obj.put("isLocked", isLocked)
        obj.put("isVisible", isVisible)
        
        if (keyframes.isNotEmpty()) {
            val arr = JSONArray()
            keyframes.forEach { kf ->
                val kfObj = JSONObject()
                kfObj.put("timeMs", kf.timeMs)
                kf.x?.let { kfObj.put("x", it.toDouble()) }
                kf.y?.let { kfObj.put("y", it.toDouble()) }
                kf.width?.let { kfObj.put("width", it.toDouble()) }
                kf.height?.let { kfObj.put("height", it.toDouble()) }
                arr.put(kfObj)
            }
            obj.put("keyframes", arr)
        }
        
        return obj.toString()
    }
    
    fun getInterpolated(currentTimeMs: Long): OperationProperties {
        if (keyframes.isEmpty()) return this
        
        val prevKf = keyframes.lastOrNull { it.timeMs <= currentTimeMs }
        val nextKf = keyframes.firstOrNull { it.timeMs > currentTimeMs }
        
        if (prevKf == null && nextKf != null) {
            return this.copy(
                x = nextKf.x ?: x,
                y = nextKf.y ?: y,
                width = nextKf.width ?: width,
                height = nextKf.height ?: height
            )
        }
        if (nextKf == null && prevKf != null) {
            return this.copy(
                x = prevKf.x ?: x,
                y = prevKf.y ?: y,
                width = prevKf.width ?: width,
                height = prevKf.height ?: height
            )
        }
        if (prevKf != null && nextKf != null) {
            val totalTime = (nextKf.timeMs - prevKf.timeMs).coerceAtLeast(1)
            val elapsed = (currentTimeMs - prevKf.timeMs).coerceAtLeast(0)
            val fraction = (elapsed.toFloat() / totalTime).coerceIn(0f, 1f)
            
            fun lerp(start: Float?, end: Float?, defaultStart: Float, defaultEnd: Float): Float {
                val s = start ?: defaultStart
                val e = end ?: defaultEnd
                return s + (e - s) * fraction
            }
            
            return this.copy(
                x = lerp(prevKf.x, nextKf.x, prevKf.x ?: x, nextKf.x ?: x),
                y = lerp(prevKf.y, nextKf.y, prevKf.y ?: y, nextKf.y ?: y),
                width = lerp(prevKf.width, nextKf.width, prevKf.width ?: width, nextKf.width ?: width),
                height = lerp(prevKf.height, nextKf.height, prevKf.height ?: height, nextKf.height ?: height)
            )
        }
        
        return this
    }
}
