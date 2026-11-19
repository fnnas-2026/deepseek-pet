package com.ferry.pet.chat

import android.net.Uri

/**
 * 把用户粘贴的接入端链接 / Webhook 地址 / 扫码结果解析成渠道配置字段。
 *
 * 支持常见形态：
 *  - 企业微信群机器人：https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxxx
 *    → baseUrl=完整 webhook，key 取 query 参数。
 *  - NapCat/Lagrange QQ 接入端：http://1.2.3.4:3000；可带 ?token=xxx
 *    → baseUrl=主机根，token 取 query。
 *  - 中转/回调链接里带 target=QQ号 / group=群号 / user=号。
 *
 * 解析不到的字段返回空串，由用户在表单里补全。
 */
object ChannelLinkParser {

    data class Parsed(
        val baseUrl: String,
        val target: String,
        val apiKey: String,
        /** 推断的渠道类型：qq / wechat / 空（未识别）。 */
        val kind: String,
    )

    fun parse(raw: String): Parsed {
        val text = raw.trim()
        if (text.isBlank()) return Parsed("", "", "", "")

        // 可能是一段文本里夹带了链接，抽取第一个 http(s) 地址。
        val uri = runCatching {
            val m = Regex("https?://[^\\s，,；;）)]+").find(text)
            Uri.parse(m?.value ?: text)
        }.getOrNull() ?: return Parsed("", "", "", "")

        val host = uri.host.orEmpty().lowercase()
        val query = uri.queryParameterNames.associateWith { uri.getQueryParameter(it).orEmpty() }

        var kind = ""
        when {
            host.contains("weixin.qq.com") || host.contains("qyapi.weixin") ->
                kind = com.ferry.pet.MessageChannel.KIND_WECHAT
            host.contains("qq") || host.contains("napcat") || host.contains("lagrange") ->
                kind = com.ferry.pet.MessageChannel.KIND_QQ
        }

        // Webhook：保留完整地址（含 /send 路径与 key query）。
        val baseUrl = when {
            host.contains("qyapi.weixin") -> uri.toString()
            else -> "${uri.scheme ?: "http"}://$host${if (uri.port > 0) ":${uri.port}" else ""}"
        }

        val target = query["target"] ?: query["group"]?.let { if (it.isNotBlank()) "g:$it" else "" }
            ?: query["user"] ?: ""
        val apiKey = query["token"] ?: query["key"] ?: query["secret"] ?: query["access_token"] ?: ""

        return Parsed(baseUrl.trimEnd('/'), target, apiKey, kind)
    }
}
