package com.ferry.pet.phone

/**
 * 敏感应用 / 敏感页面拦截清单。命中时不自动操控，交还人工。
 * 覆盖：支付、银行、密码/验证码输入、系统安全设置、钱包等。
 */
object ProtectedApps {

    private val packages = setOf(
        // 支付 / 钱包 / 银行
        "com.eg.android.AlipayGphone", "com.tencent.mm", "com.tencent.mobileqq",
        "com.unionpay", "com.icbc", "com.ccb.android", "com.chinamworld.main",
        "com.cmbchina.mobile", "com.bankcomm", "com.psbc", "com.cebbank",
        "com.spdb.mobilebank", "com.cmbc.app", "com.hxb", "com.bocsh",
        // 系统安全 / 账号
        "com.android.settings", "com.google.android.gms",
        "com.android.providers.settings",
    )

    /** 页面标题/文本命中这些词也视为敏感（密码、验证码、支付、指纹等）。 */
    private val titleWords = listOf(
        "密码", "验证码", "支付", "付款", "指纹", "人脸", "银行卡", "转账",
        "password", "verification", "payment", "pay", "fingerprint", "bank", "transfer",
    )

    fun isProtected(packageName: String?, titles: List<String>): Boolean {
        if (packageName == null) return false
        if (packageName in packages) return true
        val inSettings = packageName == "com.android.settings"
        return inSettings && titles.any { t ->
            titleWords.any { t.contains(it, ignoreCase = true) }
        }
    }
}
