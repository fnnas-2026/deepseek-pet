package com.ferry.pet.account

/**
 * 第三方账号与免费额度抽象接入位。
 *
 * 说明：当前目标站点（humanslive.ai）未提供公开的鉴权 / 额度接口文档，
 * 因此这里只定义统一契约，不在代码中硬编码任何未知 endpoint。
 * 待提供真实的登录、令牌换取与额度查询文档后，新增一个实现类并在此绑定即可，
 * 上层 UI 与业务无需改动。
 */
interface AccountService {

    /** 是否已登录（本地是否存有有效令牌）。 */
    fun isLoggedIn(): Boolean

    /** 当前账号展示名；未登录返回空串。 */
    fun displayName(): String

    /**
     * 发起登录。真实实现会打开授权页或提交账号口令，成功后持久化令牌。
     * @return 成功返回登录态，失败返回错误信息。
     */
    suspend fun login(): AccountResult

    /** 退出登录并清除本地令牌。 */
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
 * 占位实现：未接入真实第三方时使用。所有写操作与查询均返回未接入状态，
 * 避免在缺少接口文档时误连未知服务。
 */
object PlaceholderAccountService : AccountService {
    override fun isLoggedIn(): Boolean = false
    override fun displayName(): String = ""
    override suspend fun login(): AccountResult =
        AccountResult.Failure("第三方登录尚未接入，等待接口配置")
    override fun logout() {}
    override suspend fun quota(): QuotaResult =
        QuotaResult.Unavailable("额度查询尚未接入")
}

/**
 * 对外门面：后续接入真实实现时，仅替换 [instance] 绑定，无需改动调用方。
 */
object Account {
    @Volatile
    var instance: AccountService = PlaceholderAccountService
}
