package com.ferry.pet.phone

import android.content.Context
import android.provider.Settings
import android.text.TextUtils

/** 读取本机是否已启用本应用的无障碍服务（纯状态查询，不触碰节点树）。 */
object AccessibilityBridge {

    fun isEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        if (TextUtils.isEmpty(enabled)) return false
        val component = "${context.packageName}/${context.packageName}.phone.PetA11yService"
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            val entry = splitter.next()
            if (entry.equals(component, ignoreCase = true) ||
                entry.startsWith("${context.packageName}/", ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
