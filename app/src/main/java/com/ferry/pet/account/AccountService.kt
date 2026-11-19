package com.ferry.pet.account

import android.content.Context
import com.ferry.pet.PetPreferences
import com.ferry.pet.chat.ChatClient

/**
 * 第三方账号与免费额度抽象接入位。
 *
 * 说明：除 OpenRouter 已提供网页 OAuth 外，其余平台未提供公开的账号鉴权协议，
 * 因此这里不硬编码任何未知登录 endpoint。当前落地的是「本地会话态」实现：
 *  - 登录：把当前已填写 API Key 的渠道绑定为本地账号，仅在本机保存展示名；
 *  - 额度：复用 [ChatClient.fetchBalance]，仅对接提供公开 Bearer 余额接口的平台；
 *  - 退出：清除本地会话态，不删除渠道 Key。
 * 待某平台提供真实 OAuth / 令牌换取文档后，新增实现类并替换 [Account.instance] 即可，上层 UI 无需改动。
 */
interface AccountService {

    /** 是否已登录（本地是否存有有效会话）。 */
    fun isLoggedIn(): Boolean

    /** 当前账号展示名；未登录返回空串。 */
    fun displayName(): String

    /**
     * 发起登录。真实实现会打开授权页或提交账号口令，成功后持久化会话。
     * @return 成功返回登录态，失败返回错误信息。
     */
    suspend fun login(): AccountResult

    /** 退出登录并清除本地会话。 */
    fun logout()

    /** 查询剩余免费额度 / 用量；未接入实现时返回占位信息。 */
    suspend fun quota(): QuotaResult
}

/** 登录结果。 */
sealed class AccountResult {
    data class Success(val displayName: String) : AccountResult()
    data class Failure(val message: String) : AccountResult()
}

/** 额度结果。 */
sealed class QuotaResult {
    /** @param remaining 剩余额度，null 表示未知；@param label 可展示的额度说明。 */
    data class Available(val remaining: Long?, val label: String) : QuotaResult()
    data class Unavailable(val message: String) : QuotaResult()
}

/**
 * 本地会话实现：以当前活跃渠道的 API Key 作为本机登录凭证。
 * 不向任何第三方提交账号口令；额度查询走各平台公开 Bearer 接口。
 */
class LocalAccountService(private val context: Context) : AccountService {

    private val prefs: PetPreferences get() = PetPreferences(context)

    override fun isLoggedIn(): Boolean = prefs.accountLoggedIn()

    override fun displayName(): String = prefs.accountName()

    override suspend fun login(): AccountResult {
        val chat = prefs.chatOptions()
        val channel = chat.activeChannel
            .takeIf { it.baseUrl.isNotBlank() || it.apiKey.isNotBlank() }
            ?: return AccountResult.Failure("请先在渠道设置中选择并填写 API Key")
        if (channel.apiKey.isBlank()) {
            return AccountResult.Failure("当前渠道未填写 API Key，请先在渠道设置中粘贴密钥")
        }
        val name = channel.name.ifBlank { channel.baseUrl.hostLabel() }
        prefs.saveAccountSession(name, channel.apiKey, "")
        return AccountResult.Success(name)
    }

    override fun logout() = prefs.clearAccountSession()

    override suspend fun quota(): QuotaResult {
        if (!isLoggedIn()) return QuotaResult.Unavailable("尚未登录")
        val chat = prefs.chatOptions()
        val channel = chat.activeChannel
            .takeIf { it.baseUrl.isNotBlank() }
            ?: return QuotaResult.Unavailable("未绑定渠道")
        if (channel.apiKey.isBlank()) return QuotaResult.Unavailable("渠道 Key 已失效，请重新登录")
        return when (val r = ChatClient(channel).fetchBalance()) {
            is ChatClient.BalanceResult.Success -> {
                prefs.saveAccountSession(prefs.accountName(), channel.apiKey, r.text)
                QuotaResult.Available(null, r.text)
            }
            is ChatClient.BalanceResult.Failure -> QuotaResult.Unavailable(r.message)
            is ChatClient.BalanceResult.Unsupported -> QuotaResult.Unavailable(r.message)
        }
    }

    /** 从 baseUrl 提取可读的主机名作为兜底展示名。 */
    private fun String.hostLabel(): String = runCatching {
        val start = if (startsWith("http")) indexOf('/', 8) else 0
        substring(start).trim('/').split('/').firstOrNull().orEmpty()
    }.getOrNull().orEmpty().ifBlank { "已绑定渠道" }
}

/**
 * 对外门面：后续接入真实实现时，仅替换 [instance] 绑定，无需改动调用方。
 * 需在 Application / Activity 初始化时调用 [bind] 注入 Context。
 */
object Account {

    @Volatile
    var instance: AccountService = PlaceholderAccountService

    fun bind(context: Context) {
        instance = LocalAccountService(context.applicationContext)
    }
}

/** 未接入真实第三方时的占位实现，保留以兼容旧调用。 */
object PlaceholderAccountService : AccountService {
    override fun isLoggedIn(): Boolean = false
    override fun displayName(): String = ""
    override suspend fun login(): AccountResult =
        AccountResult.Failure("第三方登录尚未接入，等待接口配置")
    override fun logout() {}
    override suspend fun quota(): QuotaResult =
        QuotaResult.Unavailable("额度查询尚未接入")
}
