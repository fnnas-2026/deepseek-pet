package com.ferry.pet.phone

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.roundToInt

/**
 * B 档手机操控服务。
 *
 * 能力：读取当前界面节点树；对外提供点按 / 长按 / 滑动 / 填文本。
 * 安全：每个动作执行前先过 [ActionGate] 确认闸门；命中 [ProtectedApps] 的支付/密码/银行等
 * 敏感页面直接拒绝并交还人工。节点不可点时回退到 [dispatchGesture] 坐标手势。
 *
 * 本服务不做 OCR、不做后台自动规划；动作由外部（后续模型指令或手动触发）显式调用。
 */
class PetA11yService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: PetA11yService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // B 档不做实时监听驱动，仅保留事件通道；节点读取走 [rootInActiveWindow]。
    }

    override fun onInterrupt() {}

    /** 当前前台窗口根节点（可能为 null）。 */
    fun root(): AccessibilityNodeInfo? = rootInActiveWindow

    /** 当前前台包名。 */
    fun foregroundPackage(): String? = root()?.packageName?.toString()

    /** 收集当前界面可见文本，用于敏感页面判断。 */
    fun visibleTitles(limit: Int = 10): List<String> {
        val out = mutableListOf<String>()
        fun walk(node: AccessibilityNodeInfo?) {
            node ?: return
            if (out.size >= limit) return
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        walk(root())
        return out
    }

    /** 是否处于敏感页面（支付/密码/银行/系统安全）。 */
    fun isProtected(): Boolean =
        ProtectedApps.isProtected(foregroundPackage(), visibleTitles())

    /** 点按节点；节点不可点时回退到中心坐标手势。返回是否成功。 */
    fun clickNode(node: AccessibilityNodeInfo, long: Boolean = false): Boolean {
        if (!guard("${if (long) "长按" else "点按"}界面元素")) return false
        val action = if (long) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK
        val able: (AccessibilityNodeInfo) -> Boolean = { n ->
            n.isEnabled && if (long) n.isLongClickable else n.isClickable
        }
        if (able(node) && node.performAction(action)) return true
        // 向上找可点击父节点。
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 4) {
            if (able(parent) && parent.performAction(action)) return true
            parent = parent.parent
            depth++
        }
        // 回退：中心坐标手势。
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty) return false
        return tap(bounds.centerX(), bounds.centerY(), long)
    }

    /** 坐标点按 / 长按（手势）。 */
    fun tap(x: Int, y: Int, long: Boolean = false): Boolean {
        if (!guard(if (long) "长按坐标 ($x, $y)" else "点按坐标 ($x, $y)")) return false
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(
            path, 0, if (long) 500L else 80L)
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    /** 滑动手势。 */
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 350): Boolean {
        if (!guard("滑动 ($x1,$y1) → ($x2,$y2)")) return false
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    /** 向输入框填文本。 */
    fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (!guard("向输入框填写文本")) return false
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** 安全护栏：敏感页面拒绝；否则过确认闸门。 */
    private fun guard(describe: String): Boolean {
        if (isProtected()) return false
        return ActionGate.request(describe)
    }

    /** 供确认卡 UI 查询服务是否在线。 */
    fun alive(): Boolean = instance != null
}
