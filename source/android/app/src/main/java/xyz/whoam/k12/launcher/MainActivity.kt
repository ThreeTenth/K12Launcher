package xyz.whoam.k12.launcher

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.widget.Button
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.liulishuo.okdownload.DownloadListener
import com.liulishuo.okdownload.DownloadTask
import com.liulishuo.okdownload.OkDownloadProvider.context
import com.liulishuo.okdownload.core.breakpoint.BreakpointInfo
import com.liulishuo.okdownload.core.cause.EndCause
import com.liulishuo.okdownload.core.cause.ResumeFailedCause
import java.io.File


class MainActivity : BaseActivity(), DownloadListener, RequestPermissionResultListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.button_first).setOnClickListener {
            run {
                hideSystemUI()

                val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setDisplayCutoutEdges()
                    arrayOf(
                        Manifest.permission.INTERNET,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.REQUEST_INSTALL_PACKAGES
                    )
                } else {
                    arrayOf(
                        Manifest.permission.INTERNET,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                }

                requestPermissions(
                    this,
                    *permissions
                )
            }
        }

//        val webviewBackground = findViewById<WebView>(R.id.webview_background)
//        webviewBackground.settings.javaScriptEnabled = true
//        webviewBackground.loadUrl("file:///android_asset/index.html")
//        getPackageList(this)
    }

    override fun onBackPressed() {
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    private fun hideSystemUI() {
        // Enables regular immersive mode.
        // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
        // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE
                // Set the content to appear under the system bars so that the
                // content doesn't resize when the system bars hide and show.
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                // Hide the nav bar and status bar
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    // Shows the system bars by removing all the flags
    // except for the ones that make the content appear under the system bars.
    private fun showSystemUI() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun setDisplayCutoutEdges() {
        val layoutParams = window.attributes
        layoutParams.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        window.attributes = layoutParams
    }

    private fun update() {
        val parentFile = getExternalFilesDir(Environment.MEDIA_MOUNTED)
        val packageName = application.packageName
        val versionCode = BuildConfig.VERSION_CODE
        val updateURL = "https://cloud.whoam.xyz/v1/apk/$packageName/$versionCode"
        Log.i("Download", updateURL)
        val task = DownloadTask.Builder(updateURL, parentFile!!)
            .setFilename("update.apk")
            .setMinIntervalMillisCallbackProcess(30)
            .setPassIfAlreadyCompleted(false)
            .build()

        task.enqueue(this)
    }

    private fun installUpdateFile() {
        val install = Intent(Intent.ACTION_VIEW)
        install.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        val apkFile = File(getExternalFilesDir(Environment.MEDIA_MOUNTED), "update.apk")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val authority = "${application.packageName}.fileProvider"
            val contentUri = FileProvider.getUriForFile(context, authority, apkFile)
            Log.i("Download", "$authority: $contentUri")
            install.setDataAndType(contentUri, "application/vnd.android.package-archive")
        } else {
            install.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
        }

        startActivity(install)
    }

    private fun getPackageList(ctx: Context) {
        Log.d("TAG", "无需权限获取应用列表")
        val v9: PackageManager = ctx.packageManager
        var v2: Array<String?>?
        var uid = 1000
        while (uid <= 19999) {
            v2 = v9.getPackagesForUid(uid)
            if (v2 != null && v2.isNotEmpty()) {
                for (item in v2) {
                    try {
                        val v6 = v9.getPackageInfo(item, 0) ?: break
                        val v7 = v9.getApplicationLabel(
                            v9.getApplicationInfo(
                                v6.packageName,
                                PackageManager.GET_META_DATA
                            )
                        )
                        Log.d(
                            "TAG",
                            "应用名称 = " + v7.toString() + " (" + v6.packageName + ")"
                        )
                    } catch (e: PackageManager.NameNotFoundException) {
                        e.printStackTrace()
                    }
                }
            }
            uid++
        }
    }

    override fun connectTrialEnd(
        task: DownloadTask,
        responseCode: Int,
        responseHeaderFields: MutableMap<String, MutableList<String>>
    ) {
    }

    override fun fetchEnd(task: DownloadTask, blockIndex: Int, contentLength: Long) {
    }

    override fun downloadFromBeginning(
        task: DownloadTask,
        info: BreakpointInfo,
        cause: ResumeFailedCause
    ) {
    }

    override fun taskStart(task: DownloadTask) {
//        Toast.makeText(this, "检查更新", Toast.LENGTH_SHORT).show()
    }

    override fun taskEnd(task: DownloadTask, cause: EndCause, realCause: Exception?) {
        if (EndCause.COMPLETED == cause) {
//            Toast.makeText(this, "下载完成", Toast.LENGTH_SHORT).show()

            installUpdateFile()
        } else {
            Log.i("Download", "cause: $cause")
            Log.e("Download", realCause.toString())
//            Toast.makeText(this, "下载失败", Toast.LENGTH_SHORT).show()
        }
    }

    override fun connectTrialStart(
        task: DownloadTask,
        requestHeaderFields: MutableMap<String, MutableList<String>>
    ) {
    }

    override fun downloadFromBreakpoint(task: DownloadTask, info: BreakpointInfo) {
    }

    override fun fetchStart(task: DownloadTask, blockIndex: Int, contentLength: Long) {
    }

    override fun fetchProgress(task: DownloadTask, blockIndex: Int, increaseBytes: Long) {
        Log.i("Download", "progress: $blockIndex, $increaseBytes")
    }

    override fun connectEnd(
        task: DownloadTask,
        blockIndex: Int,
        responseCode: Int,
        responseHeaderFields: MutableMap<String, MutableList<String>>
    ) {
    }

    override fun connectStart(
        task: DownloadTask,
        blockIndex: Int,
        requestHeaderFields: MutableMap<String, MutableList<String>>
    ) {
    }

    override fun onResult(result: Boolean) {
        update()
    }
}