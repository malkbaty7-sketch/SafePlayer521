package com.example.export

import android.content.Context
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.example.data.EditingOperation
import com.example.ui.editing.OperationProperties

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CensorEffect(private val operations: List<EditingOperation>) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return CensorShaderProgram(context, operations, useHdr)
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CensorShaderProgram(
    context: Context,
    private val operations: List<EditingOperation>,
    useHdr: Boolean
) : BaseGlShaderProgram(useHdr, 1) {

    private val glProgram: GlProgram
    
    init {
        // Vertex shader: pass-through
        val vertexShaderCode = """
            #version 300 es
            in vec4 aFramePosition;
            out vec2 vTexSamplingCoord;
            void main() {
              gl_Position = aFramePosition;
              vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5;
            }
        """.trimIndent()

        // Fragment shader
        val fragmentShaderCode = """
            #version 300 es
            precision mediump float;
            uniform sampler2D texSampler;
            uniform int uOpCount;
            // Up to 10 operations
            uniform int uType0; uniform float uX0; uniform float uY0; uniform float uW0; uniform float uH0;
            uniform int uType1; uniform float uX1; uniform float uY1; uniform float uW1; uniform float uH1;
            uniform int uType2; uniform float uX2; uniform float uY2; uniform float uW2; uniform float uH2;
            uniform int uType3; uniform float uX3; uniform float uY3; uniform float uW3; uniform float uH3;
            uniform int uType4; uniform float uX4; uniform float uY4; uniform float uW4; uniform float uH4;
            uniform int uType5; uniform float uX5; uniform float uY5; uniform float uW5; uniform float uH5;
            uniform int uType6; uniform float uX6; uniform float uY6; uniform float uW6; uniform float uH6;
            uniform int uType7; uniform float uX7; uniform float uY7; uniform float uW7; uniform float uH7;
            uniform int uType8; uniform float uX8; uniform float uY8; uniform float uW8; uniform float uH8;
            uniform int uType9; uniform float uX9; uniform float uY9; uniform float uW9; uniform float uH9;
            
            in vec2 vTexSamplingCoord;
            out vec4 outColor;
            
            void applyOp(int type, float x, float y, float w, float h, vec2 coord, inout vec4 color) {
                if (coord.x >= x && coord.x <= x + w && coord.y >= y && coord.y <= y + h) {
                    if (type == 0 || type == 1) {
                        color = vec4(0.0, 0.0, 0.0, 1.0);
                    } else if (type == 2 || type == 3) {
                        float pixels = 20.0;
                        vec2 d = vec2(1.0/pixels, 1.0/pixels);
                        vec2 pixelatedCoord = floor(coord / d) * d + d * 0.5;
                        color = texture(texSampler, pixelatedCoord);
                    }
                }
            }
            
            void main() {
                vec4 color = texture(texSampler, vTexSamplingCoord);
                vec2 coord = vTexSamplingCoord;
                
                if (uOpCount > 0) applyOp(uType0, uX0, 1.0-uY0-uH0, uW0, uH0, coord, color);
                if (uOpCount > 1) applyOp(uType1, uX1, 1.0-uY1-uH1, uW1, uH1, coord, color);
                if (uOpCount > 2) applyOp(uType2, uX2, 1.0-uY2-uH2, uW2, uH2, coord, color);
                if (uOpCount > 3) applyOp(uType3, uX3, 1.0-uY3-uH3, uW3, uH3, coord, color);
                if (uOpCount > 4) applyOp(uType4, uX4, 1.0-uY4-uH4, uW4, uH4, coord, color);
                if (uOpCount > 5) applyOp(uType5, uX5, 1.0-uY5-uH5, uW5, uH5, coord, color);
                if (uOpCount > 6) applyOp(uType6, uX6, 1.0-uY6-uH6, uW6, uH6, coord, color);
                if (uOpCount > 7) applyOp(uType7, uX7, 1.0-uY7-uH7, uW7, uH7, coord, color);
                if (uOpCount > 8) applyOp(uType8, uX8, 1.0-uY8-uH8, uW8, uH8, coord, color);
                if (uOpCount > 9) applyOp(uType9, uX9, 1.0-uY9-uH9, uW9, uH9, coord, color);
                
                outColor = color;
            }
        """.trimIndent()
        
        glProgram = GlProgram(context, vertexShaderCode, fragmentShaderCode)
    }
    
    private var inputSize: Size = Size.ZERO
    
    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        inputSize = Size(inputWidth, inputHeight)
        return inputSize
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        glProgram.use()
        glProgram.setSamplerTexIdUniform("texSampler", inputTexId, 0)
        
        // Find active operations at this exact presentationTimeUs
        val presentationTimeMs = presentationTimeUs / 1000
        val activeOps = operations.filter { op ->
            op.isEnabled && presentationTimeMs in op.startTimeMs..op.endTimeMs
        }.sortedBy { 
            OperationProperties.fromJson(it.propertiesJson).zOrder 
        }.take(10) // Limit to 10 for shader array limit
        
        glProgram.setIntUniform("uOpCount", activeOps.size)
        
        if (activeOps.isNotEmpty()) {
            for (i in activeOps.indices) {
                val op = activeOps[i]
                val baseProps = OperationProperties.fromJson(op.propertiesJson)
                val props = baseProps.getInterpolated(presentationTimeMs)
                
                val type = when(op.type) {
                    "CENSOR_BLACK" -> 0
                    "CENSOR_COVER" -> 1
                    "CENSOR_BLUR" -> 2
                    "CENSOR_PIXELATE" -> 3
                    else -> 0
                }
                
                var x = props.x
                var y = props.y
                var w = props.width
                var h = props.height
                
                if (type == 1) { // Cover is full screen
                    x = 0f
                    y = 0f
                    w = 1f
                    h = 1f
                }
                
                glProgram.setIntUniform("uType$i", type)
                glProgram.setFloatUniform("uX$i", x)
                glProgram.setFloatUniform("uY$i", y)
                glProgram.setFloatUniform("uW$i", w)
                glProgram.setFloatUniform("uH$i", h)
            }
        }
        
        glProgram.bindAttributesAndUniforms()
        
        // Draw quad
        android.opengl.GLES20.glDrawArrays(android.opengl.GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GlUtil.checkGlError()
    }
}
