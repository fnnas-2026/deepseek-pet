package com.ferry.pet.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ChatClient 纯逻辑单元测试：地址归一化、OpenAI 返回解析（JSON / SSE）、
 * 内容清洗与回复截断。不依赖 Android 框架与网络。
 */
class ChatClientLogicTest {

    @Test
    fun `normalizeBase adds v1 to plain openai host`() {
        assertEquals("https://api.deepseek.com/v1",
            ChatClient.normalizeBase("https://api.deepseek.com", isOllama = false))
        assertEquals("https://api.deepseek.com/v1",
            ChatClient.normalizeBase("https://api.deepseek.com/v1/", isOllama = false))
    }

    @Test
    fun `normalizeBase keeps full endpoint untouched`() {
        assertEquals("https://x.com/chat/completions",
            ChatClient.normalizeBase("https://x.com/chat/completions", isOllama = false))
        assertEquals("https://x.com/responses",
            ChatClient.normalizeBase("https://x.com/responses", isOllama = false))
    }

    @Test
    fun `normalizeBase ollama does not add v1`() {
        assertEquals("http://192.168.1.10:11434",
            ChatClient.normalizeBase("http://192.168.1.10:11434", isOllama = true))
    }

    @Test
    fun `parseOpenAiText plain json message content`() {
        val body = """{"choices":[{"message":{"role":"assistant","content":"1+1等于2。"}}]}"""
        assertEquals("1+1等于2。", ChatClient.parseOpenAiText(body))
    }

    @Test
    fun `parseOpenAiText plain json text field`() {
        val body = """{"choices":[{"text":"纯文本回退"}]}"""
        assertEquals("纯文本回退", ChatClient.parseOpenAiText(body))
    }

    @Test
    fun `parseOpenAiText sse concatenates delta content`() {
        val body = """
            data: {"choices":[{"delta":{"content":"你好"}}]}
            data: {"choices":[{"delta":{"content":"，世界"}}]}
            data: [DONE]
        """.trimIndent()
        assertEquals("你好，世界", ChatClient.parseOpenAiText(body))
    }

    @Test
    fun `parseOpenAiText returns blank for empty body`() {
        assertTrue(ChatClient.parseOpenAiText("").isBlank())
        assertTrue(ChatClient.parseOpenAiText("not-json").isBlank())
    }

    @Test
    fun `sanitizeContent strips control chars and trims`() {
        val dirty = "  hello\u0000\u0008world\n  "
        val out = ChatClient.sanitizeContent(dirty)
        assertFalse(out.contains("\u0000"))
        assertFalse(out.contains("\u0008"))
        assertEquals("helloworld", out)
    }

    @Test
    fun `sanitizeContent truncates overlong`() {
        val long = "a".repeat(10_000)
        assertEquals(ChatClient.MAX_CONTENT_CHARS, ChatClient.sanitizeContent(long).length)
    }

    @Test
    fun `normalizeReply collapses whitespace`() {
        assertEquals("你好 世界", ChatClient.normalizeReply("你好   世界\r\n"))
    }

    @Test
    fun `normalizeReply truncates overlong with marker`() {
        val long = "字".repeat(ChatClient.MAX_REPLY_CHARS + 50)
        val out = ChatClient.normalizeReply(long)
        assertTrue(out.endsWith("…（已截断）"))
        assertTrue(out.length <= ChatClient.MAX_REPLY_CHARS + 8)
    }

    @Test
    fun `businessError reads zhipu success-false payload`() {
        val body = """{"code":1001,"msg":"Header中未收到Authorization参数","success":false}"""
        assertEquals("错误 1001：Header中未收到Authorization参数", ChatClient.businessError(body))
    }

    @Test
    fun `businessError reads openai error message`() {
        val body = """{"error":{"message":"Invalid API key","type":"invalid_request_error"}}"""
        assertEquals("Invalid API key", ChatClient.businessError(body))
    }

    @Test
    fun `businessError returns null for normal choices response`() {
        val body = """{"choices":[{"message":{"content":"pong"}}]}"""
        assertNull(ChatClient.businessError(body))
    }

    @Test
    fun `businessError returns null for blank body`() {
        assertNull(ChatClient.businessError(""))
    }

    @Test
    fun `parseOpenRouterQuota renders usage daily remaining and free tier`() {
        val body = """{"data":{"usage":12.5,"usage_daily":3.2,"limit_remaining":87.5,
            "is_free_tier":true,"free_model_daily_requests":{"remaining":42}}}"""
        val text = ChatClient.parseOpenRouterQuota(body)
        assertTrue(text!!.contains("累计已用 12.5"))
        assertTrue(text.contains("今日已用 3.2"))
        assertTrue(text.contains("剩余额度 87.5"))
        assertTrue(text.contains("免费模型今日剩 42 次"))
        assertTrue(text.contains("（免费档）"))
    }

    @Test
    fun `parseOpenRouterQuota falls back to limit when remaining absent`() {
        val body = """{"data":{"usage":1,"limit":100}}"""
        val text = ChatClient.parseOpenRouterQuota(body)
        assertTrue(text!!.contains("额度上限 100"))
        assertFalse(text.contains("剩余额度"))
    }

    @Test
    fun `parseOpenRouterQuota returns null for empty payload`() {
        assertNull(ChatClient.parseOpenRouterQuota("{}"))
        assertNull(ChatClient.parseOpenRouterQuota("not-json"))
    }
}
