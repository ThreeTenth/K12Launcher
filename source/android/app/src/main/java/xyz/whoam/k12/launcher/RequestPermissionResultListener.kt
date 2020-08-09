package xyz.whoam.k12.launcher

/**
 * 请求权限结果反馈接口
 */
interface RequestPermissionResultListener {
    /**
     * @param result true: 表示已授权
     */
    fun onResult(result: Boolean)
}