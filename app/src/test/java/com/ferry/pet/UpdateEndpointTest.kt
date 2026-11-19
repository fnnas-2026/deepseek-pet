package com.ferry.pet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 更新清单地址迁移判定：旧废弃域名回落默认线路，合法 CDN 地址保留。 */
class UpdateEndpointTest {

    @Test
    fun `legacy 800705 domain detected`() {
        assertTrue(PetPreferences.isLegacyEndpoint("https://newapi.800705.xyz/app/latest.json"))
        assertTrue(PetPreferences.isLegacyEndpoint("https://800705.xyz/latest.json"))
    }

    @Test
    fun `jsDelivr default endpoints are not legacy`() {
        assertFalse(PetPreferences.isLegacyEndpoint(
            "https://cdn.jsdelivr.net/gh/fnnas-2026/deepseek-pet@main/update.json"))
        assertFalse(PetPreferences.isLegacyEndpoint(
            "https://fastly.jsdelivr.net/gh/fnnas-2026/deepseek-pet@main/update.json"))
    }

    @Test
    fun `github release host is not legacy`() {
        assertFalse(PetPreferences.isLegacyEndpoint(
            "https://github.com/fnnas-2026/deepseek-pet/releases/download/v1.5.0/app.apk"))
    }
}
