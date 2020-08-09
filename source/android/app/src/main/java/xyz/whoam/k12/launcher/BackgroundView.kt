package xyz.whoam.k12.launcher

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class BackgroundView(c: Context, attribs: AttributeSet) : GLSurfaceView(c, attribs) {

    init {
        setEGLContextClientVersion(2)
        setRenderer(FluidSimulationRenderer())
    }

    private class FluidSimulationRenderer : Renderer {
        override fun onDrawFrame(gl: GL10?) {
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        }

    }
}