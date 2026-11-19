package com.ferry.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 内置免费/公开渠道预设结构校验：不依赖 Android 框架与网络。 */
class PresetTest {

    @Test
    fun `github models preset uses openai compatible endpoint`() {
        val p = ChatOptions.freePresetById("github")
        requireNotNull(p) { "github 预设应存在" }
        assertEquals("openai", p.api)
        assertTrue(p.baseUrl.startsWith("https://models.github.ai/"))
        assertTrue(p.signupUrl.startsWith("https://github.com/"))
        assertTrue(p.defaultModel.contains("/"))
    }

    @Test
    fun `cloudflare preset carries account id placeholder`() {
        val p = ChatOptions.freePresetById("cloudflare")
        requireNotNull(p) { "cloudflare 预设应存在" }
        assertEquals("openai", p.api)
        assertTrue(p.baseUrl.contains("api.cloudflare.com"))
        assertTrue(p.baseUrl.contains("<账号ID>"))
        assertTrue(p.defaultModel.startsWith("@cf/"))
        assertTrue(p.signupUrl.startsWith("https://dash.cloudflare.com/"))
    }

    @Test
    fun `all presets have non-empty required fields`() {
        ChatOptions.FREE_PRESETS.forEach { p ->
            assertTrue("${p.id} 名称为空", p.label.isNotBlank())
            assertTrue("${p.id} 地址为空", p.baseUrl.isNotBlank())
            assertTrue("${p.id} 模型为空", p.defaultModel.isNotBlank())
            assertTrue("${p.id} 接口类型为空", p.api.isNotBlank())
        }
    }
}
