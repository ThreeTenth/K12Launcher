package xyz.whoam.k12.launcher

import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


open class BaseActivity : AppCompatActivity() {
    private lateinit var requestPermissionResultListener: RequestPermissionResultListener

    fun requestPermissions(
        l: RequestPermissionResultListener,
        vararg permissions: String
    ) {
        requestPermissionResultListener = l
        var waitingPermissions =
            arrayOfNulls<String>(permissions.size)
        var waitingPermissionCount = 0
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED
            ) {
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                    waitingPermissions[waitingPermissionCount] = permission
                    waitingPermissionCount++
                }
            }
        }
        if (0 == waitingPermissionCount) {
            l.onResult(true)
        } else {
            waitingPermissions =
                waitingPermissions.copyOf(waitingPermissionCount)
            ActivityCompat.requestPermissions(
                this,
                waitingPermissions,
                MY_PERMISSIONS_REQUEST_READ_CONTACTS
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_READ_CONTACTS -> {
                var result = true
                for (grantResult in grantResults) {
                    if (1 == grantResult) {
                        result = false
                        break
                    }
                }

                requestPermissionResultListener.onResult(result)
            }
        }
    }

    companion object {
        private const val MY_PERMISSIONS_REQUEST_READ_CONTACTS = 100
    }
}
