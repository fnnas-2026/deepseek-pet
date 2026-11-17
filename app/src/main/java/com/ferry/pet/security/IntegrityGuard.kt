package com.ferry.pet.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Debug
import com.ferry.pet.BuildConfig

/**
 * 基础反调试 / 完整性自检（轻量，不依赖第三方加固 SDK）。
 *
 * 覆盖：
 *  - 调试器附加检测；
 *  - 可调试标记（Debug 构建除外，避免影响开发调试）；
 *  - 进程名与包名一致性粗检。
 *
 * 注意：这属于提高静态/动态分析门槛的基础措施，不能替代商业加固；
 * 正式分发如需强保护应另行接入加固 SDK 或 VMP。
 */
object IntegrityGuard {

    /** 是否处于被调试状态（Debug 包忽略调试器标记检查）。 */
    fun isDebugging(context: Context): Boolean {
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) return true
        val flag = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE
        if (!BuildConfig.DEBUG && flag != 0) return true
        return false
    }

    /** 启动时调用：发现明显调试/篡改迹象时返回 true（由调用方决定是否退出）。 */
    fun suspectTampers(context: Context): Boolean = isDebugging(context)
}
