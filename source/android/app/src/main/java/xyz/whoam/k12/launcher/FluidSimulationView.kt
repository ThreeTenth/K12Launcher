package xyz.whoam.k12.launcher

import android.content.Context
import android.graphics.Color
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

const val SIM_RESOLUTION = "SIM_RESOLUTION"
const val DYE_RESOLUTION = "DYE_RESOLUTION"
const val CAPTURE_RESOLUTION = "CAPTURE_RESOLUTION"
const val DENSITY_DISSIPATION = "DENSITY_DISSIPATION"
const val VELOCITY_DISSIPATION = "VELOCITY_DISSIPATION"
const val PRESSURE = "PRESSURE"
const val PRESSURE_ITERATIONS = "PRESSURE_ITERATIONS"
const val CURL = "CURL"
const val SPLAT_RADIUS = "SPLAT_RADIUS"
const val SPLAT_FORCE = "SPLAT_FORCE"
const val SHADING = "SHADING"
const val COLORFUL = "COLORFUL"
const val COLOR_UPDATE_SPEED = "COLOR_UPDATE_SPEED"
const val PAUSED = "PAUSED"
const val BACK_COLOR = "BACK_COLOR"
const val TRANSPARENT = "TRANSPARENT"
const val BLOOM = "BLOOM"
const val BLOOM_ITERATIONS = "BLOOM_ITERATIONS"
const val BLOOM_RESOLUTION = "BLOOM_RESOLUTION"
const val BLOOM_INTENSITY = "BLOOM_INTENSITY"
const val BLOOM_THRESHOLD = "BLOOM_THRESHOLD"
const val BLOOM_SOFT_KNEE = "BLOOM_SOFT_KNEE"
const val SUNRAYS = "SUNRAYS"
const val SUNRAYS_RESOLUTION = "SUNRAYS_RESOLUTION"
const val SUNRAYS_WEIGHT = "SUNRAYS_WEIGHT"

class FluidSimulationView(context: Context?, attrs: AttributeSet?) : GLSurfaceView(context, attrs) {
    private var renderer: FluidSimulationRenderer

    private val fluidSimulationLog = "Fluid_Simulation"

    private fun e(log: String) {
        Log.e(fluidSimulationLog, log)
    }

    init {
        renderer = FluidSimulationRenderer()
        setEGLContextClientVersion(2)
        setRenderer(renderer)
    }

    data class SupportFormat(val internalFormat: Int, val format: Int)

    data class GLContextExt(
        val formatRGBA: SupportFormat?,
        val formatRG: SupportFormat?,
        val formatR: SupportFormat?,
        val halfFloatTexType: Int,
        val supportLinearFiltering: Boolean
    )

    data class FBO(
        val texture: Int,
        val fbo: Int,
        val width: Int,
        val height: Int,
        val texelSizeX: Float,
        val texelSizeY: Float
    ) {
        fun attach(id: Int): Int {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + id)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
            return id
        }
    }

    inner class Program(vertexShader: Int, fragmentShader: Int) {
        private val program: Int = createProgram(vertexShader, fragmentShader)

        fun bind() {
            GLES30.glUseProgram(program)
        }
    }

    private fun createProgram(vertexShader: Int, fragmentShader: Int) : Int {
        val program: Int = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)

        return program
    }

    private fun getUniforms(program: Int) {
//        var uniforms = listOf(0)
//        var count: Int
//        val uniformCout = GLES30.glGetProgramiv(program, GLES30.GL_ACTIVE_UNIFORMS, count)
    }

    inner class FluidSimulationRenderer() : Renderer {

        private val baseVertexShader = compileShader(
            GLES30.GL_VERTEX_SHADER, "" +
                    "    precision highp float;\n" +
                    "    attribute vec2 aPosition;\n" +
                    "    varying vec2 vUv;\n" +
                    "    varying vec2 vL;\n" +
                    "    varying vec2 vR;\n" +
                    "    varying vec2 vT;\n" +
                    "    varying vec2 vB;\n" +
                    "    uniform vec2 texelSize;\n" +
                    "    void main () {\n" +
                    "        vUv = aPosition * 0.5 + 0.5;\n" +
                    "        vL = vUv - vec2(texelSize.x, 0.0);\n" +
                    "        vR = vUv + vec2(texelSize.x, 0.0);\n" +
                    "        vT = vUv + vec2(0.0, texelSize.y);\n" +
                    "        vB = vUv - vec2(0.0, texelSize.y);\n" +
                    "        gl_Position = vec4(aPosition, 0.0, 1.0);\n" +
                    "    }"
        )

        private val blurVertexShader = compileShader(
            GLES30.GL_VERTEX_SHADER, "" +
                    "    precision highp float;\n" +
                    "    attribute vec2 aPosition;\n" +
                    "    varying vec2 vUv;\n" +
                    "    varying vec2 vL;\n" +
                    "    varying vec2 vR;\n" +
                    "    uniform vec2 texelSize;\n" +
                    "    void main () {\n" +
                    "        vUv = aPosition * 0.5 + 0.5;\n" +
                    "        float offset = 1.33333333;\n" +
                    "        vL = vUv - texelSize * offset;\n" +
                    "        vR = vUv + texelSize * offset;\n" +
                    "        gl_Position = vec4(aPosition, 0.0, 1.0);\n" +
                    "    }"
        )

        private val blurShader = compileShader(
            GLES30.GL_FRAGMENT_SHADER, "" +
                    "    precision mediump float;\n" +
                    "    precision mediump sampler2D;\n" +
                    "    varying vec2 vUv;\n" +
                    "    varying vec2 vL;\n" +
                    "    varying vec2 vR;\n" +
                    "    uniform sampler2D uTexture;\n" +
                    "    void main () {\n" +
                    "        vec4 sum = texture2D(uTexture, vUv) * 0.29411764;\n" +
                    "        sum += texture2D(uTexture, vL) * 0.35294117;\n" +
                    "        sum += texture2D(uTexture, vR) * 0.35294117;\n" +
                    "        gl_FragColor = sum;\n" +
                    "    }"
        )

        private val copyShader = compileShader(
            GLES30.GL_FRAGMENT_SHADER, "" +
                    "    precision mediump float;\n" +
                    "    precision mediump sampler2D;\n" +
                    "    varying highp vec2 vUv;\n" +
                    "    uniform sampler2D uTexture;\n" +
                    "    void main () {\n" +
                    "        gl_FragColor = texture2D(uTexture, vUv);\n" +
                    "    }"
        )

        private val clearShader = compileShader(
            GLES30.GL_FRAGMENT_SHADER, "" +
                    "    precision mediump float;\n" +
                    "    precision mediump sampler2D;\n" +
                    "    varying highp vec2 vUv;\n" +
                    "    uniform sampler2D uTexture;\n" +
                    "    uniform float value;\n" +
                    "    void main () {\n" +
                    "        gl_FragColor = value * texture2D(uTexture, vUv);\n" +
                    "    }"
        )

        private val colorShader = compileShader(
            GLES30.GL_FRAGMENT_SHADER, "" +
                    "    precision mediump float;\n" +
                    "    uniform vec4 color;\n" +
                    "    void main () {\n" +
                    "        gl_FragColor = color;\n" +
                    "    }"
        )

        private val checkerboardShader = compileShader(
            GLES30.GL_FRAGMENT_SHADER, "" +
                    "    precision highp float;\n" +
                    "    precision highp sampler2D;\n" +
                    "    varying vec2 vUv;\n" +
                    "    uniform sampler2D uTexture;\n" +
                    "    uniform float aspectRatio;\n" +
                    "    #define SCALE 25.0\n" +
                    "    void main () {\n" +
                    "        vec2 uv = floor(vUv * SCALE * vec2(aspectRatio, 1.0));\n" +
                    "        float v = mod(uv.x + uv.y, 2.0);\n" +
                    "        v = v * 0.1 + 0.8;\n" +
                    "        gl_FragColor = vec4(vec3(v), 1.0);\n" +
                    "    }"
        )

//        private val displayShaderSource;

        private val bloomPrefilterShader = compileShader(
            GLES30.GL_FRAGMENT_SHADER, "" +
                    "    precision mediump float;\n" +
                    "    precision mediump sampler2D;\n" +
                    "    varying vec2 vUv;\n" +
                    "    uniform sampler2D uTexture;\n" +
                    "    uniform vec3 curve;\n" +
                    "    uniform float threshold;\n" +
                    "    void main () {\n" +
                    "        vec3 c = texture2D(uTexture, vUv).rgb;\n" +
                    "        float br = max(c.r, max(c.g, c.b));\n" +
                    "        float rq = clamp(br - curve.x, 0.0, curve.y);\n" +
                    "        rq = curve.z * rq * rq;\n" +
                    "        c *= max(rq, br - threshold) / max(br, 0.0001);\n" +
                    "        gl_FragColor = vec4(c, 0.0);\n" +
                    "    }"
        )

        private val bloomBlurShader = compileShader(
            GLES30.GL_FRAGMENT_SHADER, "" +
                    "    precision mediump float;\n" +
                    "    precision mediump sampler2D;\n" +
                    "    varying vec2 vL;\n" +
                    "    varying vec2 vR;\n" +
                    "    varying vec2 vT;\n" +
                    "    varying vec2 vB;\n" +
                    "    uniform sampler2D uTexture;\n" +
                    "    void main () {\n" +
                    "        vec4 sum = vec4(0.0);\n" +
                    "        sum += texture2D(uTexture, vL);\n" +
                    "        sum += texture2D(uTexture, vR);\n" +
                    "        sum += texture2D(uTexture, vT);\n" +
                    "        sum += texture2D(uTexture, vB);\n" +
                    "        sum *= 0.25;\n" +
                    "        gl_FragColor = sum;\n" +
                    "    }"
        )

        private val bloomFinalShader = compileShader(
            GLES30.GL_FRAGMENT_SHADER, "" +
                    "    precision mediump float;\n" +
                    "    precision mediump sampler2D;\n" +
                    "    varying vec2 vL;\n" +
                    "    varying vec2 vR;\n" +
                    "    varying vec2 vT;\n" +
                    "    varying vec2 vB;\n" +
                    "    uniform sampler2D uTexture;\n" +
                    "    uniform float intensity;\n" +
                    "    void main () {\n" +
                    "        vec4 sum = vec4(0.0);\n" +
                    "        sum += texture2D(uTexture, vL);\n" +
                    "        sum += texture2D(uTexture, vR);\n" +
                    "        sum += texture2D(uTexture, vT);\n" +
                    "        sum += texture2D(uTexture, vB);\n" +
                    "        sum *= 0.25;\n" +
                    "        gl_FragColor = sum * intensity;\n" +
                    "    }"
        )

        private val sunraysMaskShader = compileShader(
            GLES30.GL_FRAGMENT_SHADER, "" +
                    "    precision highp float;\n" +
                    "    precision highp sampler2D;\n" +
                    "    varying vec2 vUv;\n" +
                    "    uniform sampler2D uTexture;\n" +
                    "    void main () {\n" +
                    "        vec4 c = texture2D(uTexture, vUv);\n" +
                    "        float br = max(c.r, max(c.g, c.b));\n" +
                    "        c.a = 1.0 - min(max(br * 20.0, 0.0), 0.8);\n" +
                    "        gl_FragColor = c;\n" +
                    "    }"
        )

        private val sunraysShader = compileShader(
            GLES30.GL_FRAGMENT_SHADER, "" +
                    "    precision highp float;\n" +
                    "    precision highp sampler2D;\n" +
                    "    varying vec2 vUv;\n" +
                    "    uniform sampler2D uTexture;\n" +
                    "    uniform float weight;\n" +
                    "    #define ITERATIONS 16\n" +
                    "    void main () {\n" +
                    "        float Density = 0.3;\n" +
                    "        float Decay = 0.95;\n" +
                    "        float Exposure = 0.7;\n" +
                    "        vec2 coord = vUv;\n" +
                    "        vec2 dir = vUv - 0.5;\n" +
                    "        dir *= 1.0 / float(ITERATIONS) * Density;\n" +
                    "        float illuminationDecay = 1.0;\n" +
                    "        float color = texture2D(uTexture, vUv).a;\n" +
                    "        for (int i = 0; i < ITERATIONS; i++)\n" +
                    "        {\n" +
                    "            coord -= dir;\n" +
                    "            float col = texture2D(uTexture, coord).a;\n" +
                    "            color += col * illuminationDecay * weight;\n" +
                    "            illuminationDecay *= Decay;\n" +
                    "        }\n" +
                    "        gl_FragColor = vec4(color * Exposure, 0.0, 0.0, 1.0);\n" +
                    "    }"
        )

        private val splatShader = compileShader(
            GLES30.GL_FRAGMENT_SHADER, "" +
                    "    precision highp float;\n" +
                    "    precision highp sampler2D;\n" +
                    "    varying vec2 vUv;\n" +
                    "    uniform sampler2D uTarget;\n" +
                    "    uniform float aspectRatio;\n" +
                    "    uniform vec3 color;\n" +
                    "    uniform vec2 point;\n" +
                    "    uniform float radius;\n" +
                    "    void main () {\n" +
                    "        vec2 p = vUv - point.xy;\n" +
                    "        p.x *= aspectRatio;\n" +
                    "        vec3 splat = exp(-dot(p, p) / radius) * color;\n" +
                    "        vec3 base = texture2D(uTarget, vUv).xyz;\n" +
                    "        gl_FragColor = vec4(base + splat, 1.0);\n" +
                    "    }"
        )

        private val advectionShader = compileShader(
            GLES30.GL_FRAGMENT_SHADER, "" +
                    "    precision highp float;\n" +
                    "    precision highp sampler2D;\n" +
                    "    varying vec2 vUv;\n" +
                    "    uniform sampler2D uVelocity;\n" +
                    "    uniform sampler2D uSource;\n" +
                    "    uniform vec2 texelSize;\n" +
                    "    uniform vec2 dyeTexelSize;\n" +
                    "    uniform float dt;\n" +
                    "    uniform float dissipation;\n" +
                    "    vec4 bilerp (sampler2D sam, vec2 uv, vec2 tsize) {\n" +
                    "        vec2 st = uv / tsize - 0.5;\n" +
                    "        vec2 iuv = floor(st);\n" +
                    "        vec2 fuv = fract(st);\n" +
                    "        vec4 a = texture2D(sam, (iuv + vec2(0.5, 0.5)) * tsize);\n" +
                    "        vec4 b = texture2D(sam, (iuv + vec2(1.5, 0.5)) * tsize);\n" +
                    "        vec4 c = texture2D(sam, (iuv + vec2(0.5, 1.5)) * tsize);\n" +
                    "        vec4 d = texture2D(sam, (iuv + vec2(1.5, 1.5)) * tsize);\n" +
                    "        return mix(mix(a, b, fuv.x), mix(c, d, fuv.x), fuv.y);\n" +
                    "    }\n" +
                    "    void main () {\n" +
                    "    #ifdef MANUAL_FILTERING\n" +
                    "        vec2 coord = vUv - dt * bilerp(uVelocity, vUv, texelSize).xy * texelSize;\n" +
                    "        vec4 result = bilerp(uSource, coord, dyeTexelSize);\n" +
                    "    #else\n" +
                    "        vec2 coord = vUv - dt * texture2D(uVelocity, vUv).xy * texelSize;\n" +
                    "        vec4 result = texture2D(uSource, coord);\n" +
                    "    #endif\n" +
                    "        float decay = 1.0 + dissipation * dt;\n" +
                    "        gl_FragColor = result / decay;\n" +
                    "    }"
        )

        private val divergenceShader = compileShader(
            GLES30.GL_FRAGMENT_SHADER, "" +
                    "    precision mediump float;\n" +
                    "    precision mediump sampler2D;\n" +
                    "    varying highp vec2 vUv;\n" +
                    "    varying highp vec2 vL;\n" +
                    "    varying highp vec2 vR;\n" +
                    "    varying highp vec2 vT;\n" +
                    "    varying highp vec2 vB;\n" +
                    "    uniform sampler2D uVelocity;\n" +
                    "    void main () {\n" +
                    "        float L = texture2D(uVelocity, vL).x;\n" +
                    "        float R = texture2D(uVelocity, vR).x;\n" +
                    "        float T = texture2D(uVelocity, vT).y;\n" +
                    "        float B = texture2D(uVelocity, vB).y;\n" +
                    "        vec2 C = texture2D(uVelocity, vUv).xy;\n" +
                    "        if (vL.x < 0.0) { L = -C.x; }\n" +
                    "        if (vR.x > 1.0) { R = -C.x; }\n" +
                    "        if (vT.y > 1.0) { T = -C.y; }\n" +
                    "        if (vB.y < 0.0) { B = -C.y; }\n" +
                    "        float div = 0.5 * (R - L + T - B);\n" +
                    "        gl_FragColor = vec4(div, 0.0, 0.0, 1.0);\n" +
                    "    }"
        )

        private val curlShader = compileShader(
            GLES30.GL_FRAGMENT_SHADER, "" +
                    "    precision mediump float;\n" +
                    "    precision mediump sampler2D;\n" +
                    "    varying highp vec2 vUv;\n" +
                    "    varying highp vec2 vL;\n" +
                    "    varying highp vec2 vR;\n" +
                    "    varying highp vec2 vT;\n" +
                    "    varying highp vec2 vB;\n" +
                    "    uniform sampler2D uVelocity;\n" +
                    "    void main () {\n" +
                    "        float L = texture2D(uVelocity, vL).y;\n" +
                    "        float R = texture2D(uVelocity, vR).y;\n" +
                    "        float T = texture2D(uVelocity, vT).x;\n" +
                    "        float B = texture2D(uVelocity, vB).x;\n" +
                    "        float vorticity = R - L - T + B;\n" +
                    "        gl_FragColor = vec4(0.5 * vorticity, 0.0, 0.0, 1.0);\n" +
                    "    }"
        )

        private val vorticityShader = compileShader(
            GLES30.GL_FRAGMENT_SHADER, "" +
                    "    precision highp float;\n" +
                    "    precision highp sampler2D;\n" +
                    "    varying vec2 vUv;\n" +
                    "    varying vec2 vL;\n" +
                    "    varying vec2 vR;\n" +
                    "    varying vec2 vT;\n" +
                    "    varying vec2 vB;\n" +
                    "    uniform sampler2D uVelocity;\n" +
                    "    uniform sampler2D uCurl;\n" +
                    "    uniform float curl;\n" +
                    "    uniform float dt;\n" +
                    "    void main () {\n" +
                    "        float L = texture2D(uCurl, vL).x;\n" +
                    "        float R = texture2D(uCurl, vR).x;\n" +
                    "        float T = texture2D(uCurl, vT).x;\n" +
                    "        float B = texture2D(uCurl, vB).x;\n" +
                    "        float C = texture2D(uCurl, vUv).x;\n" +
                    "        vec2 force = 0.5 * vec2(abs(T) - abs(B), abs(R) - abs(L));\n" +
                    "        force /= length(force) + 0.0001;\n" +
                    "        force *= curl * C;\n" +
                    "        force.y *= -1.0;\n" +
                    "        vec2 velocity = texture2D(uVelocity, vUv).xy;\n" +
                    "        velocity += force * dt;\n" +
                    "        velocity = min(max(velocity, -1000.0), 1000.0);\n" +
                    "        gl_FragColor = vec4(velocity, 0.0, 1.0);\n" +
                    "    }"
        )

        private val pressureShader = compileShader(
            GLES30.GL_FRAGMENT_SHADER, "" +
                    "    precision mediump float;\n" +
                    "    precision mediump sampler2D;\n" +
                    "    varying highp vec2 vUv;\n" +
                    "    varying highp vec2 vL;\n" +
                    "    varying highp vec2 vR;\n" +
                    "    varying highp vec2 vT;\n" +
                    "    varying highp vec2 vB;\n" +
                    "    uniform sampler2D uPressure;\n" +
                    "    uniform sampler2D uDivergence;\n" +
                    "    void main () {\n" +
                    "        float L = texture2D(uPressure, vL).x;\n" +
                    "        float R = texture2D(uPressure, vR).x;\n" +
                    "        float T = texture2D(uPressure, vT).x;\n" +
                    "        float B = texture2D(uPressure, vB).x;\n" +
                    "        float C = texture2D(uPressure, vUv).x;\n" +
                    "        float divergence = texture2D(uDivergence, vUv).x;\n" +
                    "        float pressure = (L + R + B + T - divergence) * 0.25;\n" +
                    "        gl_FragColor = vec4(pressure, 0.0, 0.0, 1.0);\n" +
                    "    }"
        )

        private val gradientSubtractShader = compileShader(
            GLES30.GL_FRAGMENT_SHADER, "" +
                    "    precision mediump float;\n" +
                    "    precision mediump sampler2D;\n" +
                    "    varying highp vec2 vUv;\n" +
                    "    varying highp vec2 vL;\n" +
                    "    varying highp vec2 vR;\n" +
                    "    varying highp vec2 vT;\n" +
                    "    varying highp vec2 vB;\n" +
                    "    uniform sampler2D uPressure;\n" +
                    "    uniform sampler2D uVelocity;\n" +
                    "    void main () {\n" +
                    "        float L = texture2D(uPressure, vL).x;\n" +
                    "        float R = texture2D(uPressure, vR).x;\n" +
                    "        float T = texture2D(uPressure, vT).x;\n" +
                    "        float B = texture2D(uPressure, vB).x;\n" +
                    "        vec2 velocity = texture2D(uVelocity, vUv).xy;\n" +
                    "        velocity.xy -= vec2(R - L, T - B);\n" +
                    "        gl_FragColor = vec4(velocity, 0.0, 1.0);\n" +
                    "    }"
        )

        private val config = mapOf(
            SIM_RESOLUTION to 128,
            DYE_RESOLUTION to 2014,
            CAPTURE_RESOLUTION to 512,
            DENSITY_DISSIPATION to 1,
            VELOCITY_DISSIPATION to 0.2f,
            PRESSURE to 0.8f,
            PRESSURE_ITERATIONS to 20,
            CURL to 30,
            SPLAT_RADIUS to 0.25f,
            SPLAT_FORCE to 6000,
            SHADING to true,
            COLORFUL to true,
            COLOR_UPDATE_SPEED to 10,
            PAUSED to false,
            BACK_COLOR to Color(),
            TRANSPARENT to false,
            BLOOM to true,
            BLOOM_ITERATIONS to 8,
            BLOOM_RESOLUTION to 256,
            BLOOM_INTENSITY to 0.8f,
            BLOOM_THRESHOLD to 0.6f,
            BLOOM_SOFT_KNEE to 0.7f,
            SUNRAYS to true,
            SUNRAYS_RESOLUTION to 196,
            SUNRAYS_WEIGHT to 1.0f
        )

        private lateinit var ext: GLContextExt

        private lateinit var blurProgram: Program
        private lateinit var copyProgram: Program
        private lateinit var clearProgram: Program
        private lateinit var colorProgram: Program
        private lateinit var checkerboardProgram: Program
        private lateinit var bloomPrefilterProgram: Program
        private lateinit var bloomBlurProgram: Program
        private lateinit var bloomFinalProgram: Program
        private lateinit var sunraysMaskProgram: Program
        private lateinit var sunraysProgram: Program
        private lateinit var splatProgram: Program
        private lateinit var advectionProgram: Program
        private lateinit var divergenceProgram: Program
        private lateinit var curlProgram: Program
        private lateinit var vorticityProgram: Program
        private lateinit var pressureProgram: Program
        private lateinit var gradienSubtractProgram: Program

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES30.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES30.glClearColor(0f, 0f, 0f, 1f)

            if (!::ext.isInitialized) {
                ext = getGLContextExt()
            }
            initFrameBuffers()
        }

        private fun getGLContextExt(): GLContextExt {
            val halfFloatTexType = GLES30.GL_HALF_FLOAT
            val formatRGBA =
                getSupportedFormat(GLES30.GL_RGBA16F, GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT)
            val formatRG =
                getSupportedFormat(GLES30.GL_RGB16F, GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT)
            val formatR =
                getSupportedFormat(GLES30.GL_R16F, GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT)

            val supportLinearFiltering = true

            return GLContextExt(
                formatRGBA,
                formatRG,
                formatR,
                halfFloatTexType,
                supportLinearFiltering
            )
        }

        private fun initFrameBuffers() {
            val simRes = getResolution(config[SIM_RESOLUTION] as Int)
            val dyeRes = getResolution(config[DYE_RESOLUTION] as Int)

            val texType = ext.halfFloatTexType
            val rgba = ext.formatRGBA
            val rg = ext.formatRG
            val r = ext.formatR
            val filtering = if (ext.supportLinearFiltering) GLES30.GL_LINEAR else GLES30.GL_NEAREST

            GLES30.glDisable(GLES30.GL_BLEND)
            TODO("initFrameBuffers")
        }

        private fun getResolution(resolution: Int) {
            TODO("getResolution")
        }

        private fun createFBO(
            w: Int,
            h: Int,
            internalFormat: Int,
            format: Int,
            type: Int,
            param: Int
        ): FBO {
            val textures: IntBuffer = IntBuffer.allocate(1)
            GLES30.glGenTextures(1, textures)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[0])
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, param)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, param)
            GLES30.glTexParameteri(
                GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_WRAP_S,
                GLES30.GL_CLAMP_TO_EDGE
            )
            GLES30.glTexParameteri(
                GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_WRAP_T,
                GLES30.GL_CLAMP_TO_EDGE
            )
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D,
                0,
                internalFormat,
                w,
                h,
                0,
                format,
                type,
                null
            )

            val fbo = IntBuffer.allocate(1)
            GLES30.glGenFramebuffers(1, fbo)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER,
                GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D,
                textures[0],
                0
            )
            GLES30.glViewport(0, 0, w, h)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

            val texelSizeX = 1.0f / w
            val texelSizeY = 1.0f / h

            return FBO(textures[0], fbo[0], w, h, texelSizeX, texelSizeY)
        }

        private fun getSupportedFormat(
            internalFormat: Int,
            format: Int,
            type: Int
        ): SupportFormat? {
            if (!supportRenderTextureFormat(internalFormat, format, type)) {
                return when (internalFormat) {
                    GLES30.GL_R16F -> {
                        getSupportedFormat(GLES30.GL_RG16F, GLES30.GL_RG, type)
                    }
                    GLES30.GL_RG16F -> {
                        getSupportedFormat(GLES30.GL_RGBA16F, GLES30.GL_RGBA, type)
                    }
                    else -> {
                        null
                    }
                }
            }

            return SupportFormat(internalFormat, format)
        }

        private fun supportRenderTextureFormat(
            internalFormat: Int,
            format: Int,
            type: Int
        ): Boolean {
            val textures = IntBuffer.allocate(1)
            GLES30.glGenTextures(1, textures)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[0])
            GLES30.glTexParameteri(
                GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_MIN_FILTER,
                GLES30.GL_NEAREST
            )
            GLES30.glTexParameteri(
                GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_MAG_FILTER,
                GLES30.GL_NEAREST
            )
            GLES30.glTexParameteri(
                GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_WRAP_S,
                GLES30.GL_CLAMP_TO_EDGE
            )
            GLES30.glTexParameteri(
                GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_WRAP_T,
                GLES30.GL_CLAMP_TO_EDGE
            )
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D,
                0,
                internalFormat,
                4,
                4,
                0,
                format,
                type,
                null
            )

            // init: https://stackoverflow.com/questions/42455918/opengl-does-not-render-to-screen-by-calling-glbindframebuffergl-framebuffer-0
            val fbo = IntBuffer.allocate(1)
            GLES30.glGetIntegerv(GLES30.GL_FRAMEBUFFER_BINDING, fbo)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER,
                GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D,
                textures[0],
                0
            )

            val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
            return status == GLES30.GL_FRAMEBUFFER_COMPLETE
        }

        private fun compileShader(type: Int, source: String): Int {
            val shader = GLES30.glCreateShader(type);
            GLES30.glShaderSource(shader, source);
            GLES30.glCompileShader(shader);

            val success: IntBuffer = IntBuffer.allocate(1)
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, success)

            if (0 == success[0]) {
                e(GLES30.glGetShaderInfoLog(shader))
            }

            return shader;
        };
    }
}