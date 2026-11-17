package com.ferry.pet.modules

import android.content.Context
import com.ferry.pet.ModuleIds
import com.ferry.pet.PetHandle
import com.ferry.pet.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 每日一签：本地纯逻辑，不联网。同一天抽到的签固定，跨天换新；先冒「抽签中…」，
 * 约 1 秒后在气泡里出签。签号由日期哈希在固定签库内取，避免同日内来回变。
 */
class DivinationModule : HostBoundModule() {
    override val id = ModuleIds.DIVINATION

    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private var cachedDay: String = ""
    private var cachedQuote: DivinationQuote? = null

    override fun menuItems(pet: PetHandle): List<ModuleMenu> = listOf(
        ModuleMenu(R.string.divination_draw) { draw(pet) }
    )

    private fun draw(pet: PetHandle) {
        host.showPhrase(pet, host.context.getString(R.string.divination_shuffling), SHUFFLE_MS)
        host.postDelayed(SHUFFLE_MS) {
            val quote = quoteForToday()
            val verse = quote.verses.joinToString(" ")
            val text = host.context.getString(
                R.string.divination_result, quote.tier, quote.title, verse, quote.reading)
            host.showPhrase(pet, text, resultDurationMs(quote.reading))
        }
    }

    private fun quoteForToday(): DivinationQuote {
        val today = fmt.format(Date())
        if (today == cachedDay && cachedQuote != null) return cachedQuote!!
        // 日期字符串哈希 → 固定下标，同一天结果一致；跨天自然换签。
        val hash = today.hashCode().toLong() and Long.MAX_VALUE
        val index = (hash % DivinationQuotes.ALL.size).toInt()
        val quote = DivinationQuotes.ALL[index]
        cachedDay = today
        cachedQuote = quote
        return quote
    }

    private fun resultDurationMs(reading: String): Long {
        // 4 秒打底，每字 90ms，上限 30 秒（对齐上游气泡时长口径）。
        val base = 4000L
        val perChar = 90L
        val cap = 30000L
        return (base + reading.length * perChar).coerceAtMost(cap)
    }

    override fun onDisable() {
        cachedDay = ""
        cachedQuote = null
    }

    override fun diagnostics() = "quotes=${DivinationQuotes.ALL.size}"

    private companion object {
        const val SHUFFLE_MS = 1000L
    }
}
