package com.khumam.gbaduo.emu

import android.content.res.AssetManager
import android.opengl.GLES30 as GL
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Small GLES3 runner for the bundled, unmodified Libretro algorithms.
 * All methods run on GameView's GL thread. Intermediate textures retain source
 * orientation; the final blit flips once to match the emulator's top-first rows.
 */
internal class LibretroShaderRenderer(private val assets: AssetManager, private val preset: ShaderPreset) {
    private val programs = mutableListOf<Int>()
    private var blit = 0
    private var textures = IntArray(0)
    private var fbos = IntArray(0)
    private var sourceWidth = 0
    private var sourceHeight = 0
    private val vertices = buffer(floatArrayOf(-1f,-1f, 1f,-1f, -1f,1f, 1f,1f))
    private val straight = buffer(floatArrayOf(0f,0f, 1f,0f, 0f,1f, 1f,1f))
    private val flipped = buffer(floatArrayOf(0f,1f, 1f,1f, 0f,0f, 1f,0f))
    private val identity = floatArrayOf(1f,0f,0f,0f, 0f,1f,0f,0f, 0f,0f,1f,0f, 0f,0f,0f,1f)

    init {
        try {
            val names = when(preset) {
                ShaderPreset.SCALEFX -> (0..4).map { "scalefx_shaders_scalefx-pass$it.glsl" }
                ShaderPreset.HQ2X -> listOf("hq2x.glsl")
                else -> listOf("xbrz.glsl")
            }
            for (name in names) {
                val source = assets.open("shaders/libretro/$name").bufferedReader().use { it.readText() }
                    .replace(Regex("(?m)^\\s*#version[^\\n]*"), "")
                    .replace(Regex("(?m)^\\s*#pragma[^\\n]*"), "")
                programs.add(link(stage(source,"VERTEX"), stage(source,"FRAGMENT")))
            }
            blit = link("""#version 300 es
                in vec4 VertexCoord; in vec2 TexCoord; out vec2 uv;
                void main(){gl_Position=VertexCoord;uv=TexCoord;}
            """.trimIndent(), """#version 300 es
                precision highp float;
                in vec2 uv; uniform sampler2D Texture; out vec4 color;
                void main(){color=texture(Texture,uv);}
            """.trimIndent())
        } catch (e: Exception) { close(); throw e }
    }

    private fun stage(source:String, kind:String) = "#version 300 es\n#define $kind\nprecision highp float;\nprecision highp int;\n$source"

    private fun allocate(width:Int,height:Int) {
        if(width==sourceWidth && height==sourceHeight) return
        deleteTargets()
        val count = if(preset==ShaderPreset.SCALEFX) 5 else if(preset==ShaderPreset.HQ2X) 1 else 0
        textures = IntArray(count); fbos = IntArray(count)
        GL.glGenFramebuffers(count,fbos,0)
        try {
            for(i in 0 until count) {
                textures[i] = newTexture()
                val scale = if(preset==ShaderPreset.HQ2X) 2 else if(i==4) 3 else 1
                val floatTarget = preset==ShaderPreset.SCALEFX && i<2
                GL.glTexImage2D(GL.GL_TEXTURE_2D,0,if(floatTarget) GL.GL_RGBA32F else GL.GL_RGBA8,width*scale,height*scale,0,GL.GL_RGBA,if(floatTarget) GL.GL_FLOAT else GL.GL_UNSIGNED_BYTE,null)
                GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,fbos[i])
                GL.glFramebufferTexture2D(GL.GL_FRAMEBUFFER,GL.GL_COLOR_ATTACHMENT0,GL.GL_TEXTURE_2D,textures[i],0)
                check(GL.glCheckFramebufferStatus(GL.GL_FRAMEBUFFER)==GL.GL_FRAMEBUFFER_COMPLETE) { "${preset.displayName}: unsupported framebuffer format" }
            }
            sourceWidth=width; sourceHeight=height
        } finally { GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,0) }
    }

    fun draw(input:Int,width:Int,height:Int,surfaceWidth:Int,surfaceHeight:Int) {
        allocate(width,height)
        GL.glDisable(GL.GL_BLEND)
        GL.glDisable(GL.GL_DITHER)
        val fit = minOf(surfaceWidth.toFloat()/width,surfaceHeight.toFloat()/height)
        val outWidth=(width*fit).toInt(); val outHeight=(height*fit).toInt()
        if(preset==ShaderPreset.XBRZ) {
            GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,0)
            GL.glViewport((surfaceWidth-outWidth)/2,(surfaceHeight-outHeight)/2,outWidth,outHeight)
            render(programs[0],input,input,0,width,height,outWidth,outHeight,true)
        }else {
            var previous=input
            for(i in programs.indices) {
                val scale=if(preset==ShaderPreset.HQ2X) 2 else if(i==4) 3 else 1
                GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,fbos[i]); GL.glViewport(0,0,width*scale,height*scale)
                render(programs[i],previous,input,if(i>=2) textures[0] else 0,width,height,width*scale,height*scale,false)
                previous=textures[i]
            }
            GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,0)
            GL.glViewport((surfaceWidth-outWidth)/2,(surfaceHeight-outHeight)/2,outWidth,outHeight)
            render(blit,previous,input,0,width,height,outWidth,outHeight,true)
        }
        GL.glActiveTexture(GL.GL_TEXTURE0)
    }

    private fun render(program:Int,source:Int,original:Int,firstPass:Int,w:Int,h:Int,ow:Int,oh:Int,flip:Boolean) {
        GL.glUseProgram(program)
        fun texture(name:String,unit:Int,id:Int) {
            val loc=GL.glGetUniformLocation(program,name)
            if(loc>=0){ GL.glActiveTexture(GL.GL_TEXTURE0+unit);GL.glBindTexture(GL.GL_TEXTURE_2D,id);GL.glUniform1i(loc,unit) }
        }
        texture("Texture",0,source);texture("PassPrev5Texture",1,original);texture("PassPrev2Texture",2,firstPass)
        for(name in listOf("TextureSize","InputSize","PassPrev5TextureSize")) GL.glUniform2f(GL.glGetUniformLocation(program,name),w.toFloat(),h.toFloat())
        GL.glUniform2f(GL.glGetUniformLocation(program,"OutputSize"),ow.toFloat(),oh.toFloat())
        GL.glUniformMatrix4fv(GL.glGetUniformLocation(program,"MVPMatrix"),1,false,identity,0)
        val color=GL.glGetAttribLocation(program,"COLOR");if(color>=0){GL.glDisableVertexAttribArray(color);GL.glVertexAttrib4f(color,1f,1f,1f,1f)}
        val pos=GL.glGetAttribLocation(program,"VertexCoord")
        val uv=GL.glGetAttribLocation(program,"TexCoord")
        GL.glEnableVertexAttribArray(pos);GL.glVertexAttribPointer(pos,2,GL.GL_FLOAT,false,0,vertices)
        if(uv>=0){GL.glEnableVertexAttribArray(uv);GL.glVertexAttribPointer(uv,2,GL.GL_FLOAT,false,0,if(flip) flipped else straight)}
        GL.glDrawArrays(GL.GL_TRIANGLE_STRIP,0,4)
        GL.glDisableVertexAttribArray(pos);if(uv>=0)GL.glDisableVertexAttribArray(uv)
    }

    private fun newTexture():Int {
        val ids=IntArray(1);GL.glGenTextures(1,ids,0);GL.glActiveTexture(GL.GL_TEXTURE0);GL.glBindTexture(GL.GL_TEXTURE_2D,ids[0])
        GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MIN_FILTER,GL.GL_NEAREST);GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MAG_FILTER,GL.GL_NEAREST)
        GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_WRAP_S,GL.GL_CLAMP_TO_EDGE);GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_WRAP_T,GL.GL_CLAMP_TO_EDGE)
        return ids[0]
    }
    private fun deleteTargets(){GL.glDeleteTextures(textures.size,textures,0);GL.glDeleteFramebuffers(fbos.size,fbos,0);textures=IntArray(0);fbos=IntArray(0);sourceWidth=0;sourceHeight=0}
    fun close(){deleteTargets();programs.forEach { GL.glDeleteProgram(it) };programs.clear();if(blit!=0)GL.glDeleteProgram(blit);blit=0}

    private fun link(v:String,f:String):Int {
        val vertex=compile(GL.GL_VERTEX_SHADER,v)
        val fragment=try{compile(GL.GL_FRAGMENT_SHADER,f)}catch(e:Exception){GL.glDeleteShader(vertex);throw e}
        val program=GL.glCreateProgram();GL.glAttachShader(program,vertex);GL.glAttachShader(program,fragment);GL.glLinkProgram(program)
        GL.glDeleteShader(vertex);GL.glDeleteShader(fragment)
        val ok=IntArray(1);GL.glGetProgramiv(program,GL.GL_LINK_STATUS,ok,0)
        if(ok[0]==0){val log=GL.glGetProgramInfoLog(program);GL.glDeleteProgram(program);error(log)}
        return program
    }
    private fun compile(type:Int,source:String):Int {
        val shader=GL.glCreateShader(type);GL.glShaderSource(shader,source);GL.glCompileShader(shader)
        val ok=IntArray(1);GL.glGetShaderiv(shader,GL.GL_COMPILE_STATUS,ok,0)
        if(ok[0]==0){val log=GL.glGetShaderInfoLog(shader);GL.glDeleteShader(shader);error(log)}
        return shader
    }
    private fun buffer(data:FloatArray)=ByteBuffer.allocateDirect(data.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(data);position(0)}
}
