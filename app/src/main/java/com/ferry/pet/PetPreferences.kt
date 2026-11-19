package com.ferry.pet

import android.content.Context
import android.content.SharedPreferences
import kotlin.random.Random

/** Phone-side preferences. Every optional capability has exactly one enable bit
 *  here, and that bit is what the module registry reads. */
data class PetOptions(
    val size: Float = 1f,
    val wander: Boolean = true,
    val speech: Boolean = true,
    val phrases: List<String> = emptyList(),
    val speechMinSeconds: Float = DEFAULT_MIN_SECONDS,
    val speechMaxSeconds: Float = DEFAULT_MAX_SECONDS,
    val speechSeconds: Float = DEFAULT_SPEECH_SECONDS,
    val clickSound: Boolean = true,
    val clickSoundVolume: Float = .7f,
    val clickSoundUri: String? = null,
    val companions: Boolean = true,
    val collision: Boolean = true,
    val collisionRestitution: Float = DEFAULT_RESTITUTION,
    val divination: Boolean = true,
    val animSpeed: Float = 1f,
) {
    companion object {
        const val DEFAULT_MIN_SECONDS = 20f
        const val DEFAULT_MAX_SECONDS = 60f
        const val DEFAULT_SPEECH_SECONDS = 3.2f
        const val MIN_SECONDS = 5f
        const val MAX_SECONDS = 600f
        const val MIN_SPEECH_SECONDS = 1f
        const val MAX_SPEECH_SECONDS = 30f

        const val DEFAULT_RESTITUTION = Collision.RESTITUTION.toFloat()
    }

    /** A configured interval can never invert: min ≤ max, both inside range. */
    fun normalized(): PetOptions {
        val min = finite(speechMinSeconds, DEFAULT_MIN_SECONDS).coerceIn(MIN_SECONDS, MAX_SECONDS)
        val max = finite(speechMaxSeconds, DEFAULT_MAX_SECONDS).coerceIn(MIN_SECONDS, MAX_SECONDS)
        return copy(
            size = finite(size, 1f).coerceIn(.6f, 1.6f),
            clickSoundVolume = finite(clickSoundVolume, .7f).coerceIn(0f, 1f),
            speechMinSeconds = minOf(min, max),
            speechMaxSeconds = maxOf(min, max),
            speechSeconds = finite(speechSeconds, DEFAULT_SPEECH_SECONDS)
                .coerceIn(MIN_SPEECH_SECONDS, MAX_SPEECH_SECONDS),
            collisionRestitution = finite(collisionRestitution, DEFAULT_RESTITUTION)
                .coerceIn(0f, 1f),
            animSpeed = finite(animSpeed, 1f).coerceIn(.5f, 2f),
        )
    }

    fun speechDelayMs(random: Random = Random.Default): Long {
        val normalized = normalized()
        val low = (normalized.speechMinSeconds * 1000).toLong()
        val high = (normalized.speechMaxSeconds * 1000).toLong()
        return if (high <= low) low else low + random.nextLong(high - low + 1)
    }

    fun speechDurationMs(): Long = (normalized().speechSeconds * 1000).toLong()

    /** The single enable bit for each module; the module list and the user
     *  switches cannot disagree because both read this. */
    fun enables(moduleId: String): Boolean = when (moduleId) {
        ModuleIds.WANDER -> wander
        ModuleIds.SPEECH -> speech
        ModuleIds.CLICK_SOUND -> clickSound
        ModuleIds.COMPANION -> companions
        ModuleIds.COLLISION -> collision
        ModuleIds.DIVINATION -> divination
        else -> false
    }

    private fun finite(value: Float, fallback: Float) = if (value.isFinite()) value else fallback
}

/** 一个模型渠道：独立的网关地址、Key、模型与接口风格，可增删改、一键切换。 */
data class Channel(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val api: String,
    val temperature: Float = .7f,
)

/**
 * 一个消息转发渠道：把桌宠回复推送到 QQ 机器人 / 微信·企业微信 webhook。
 * 不逆向官方协议，只对接自建接入端或群机器人 webhook。
 *  - qq：NapCat/Lagrange 风格 HTTP 上报，POST {baseUrl}/send_private_msg 或 /send_msg，
 *    目标填 QQ 号；也可填群号走群消息。
 *  - wechat：企业微信群机器人 / 自建应用 webhook，POST webhook 地址，目标为可选 @ 成员。
 */
data class MessageChannel(
    val id: String,
    val name: String,
    val kind: String,          // qq / wechat
    val baseUrl: String,
    val target: String,        // QQ 号 / 群号 / 可选 @ 标识
    val apiKey: String = "",   // 可选：接入端 Token / Secret
    val enabled: Boolean = false,
    /** 双向：开启后轮询拉取对方消息，经 AI 回复后回推。 */
    val bidirectional: Boolean = false,
    /** 上次已处理消息的时间戳（毫秒），用于只处理新消息。 */
    val lastSeen: Long = 0L,
) {
    companion object {
        const val KIND_QQ = "qq"
        const val KIND_WECHAT = "wechat"
    }
}

/** 可选免费/公开模型预设：网关地址 + 默认模型 + 说明，Key 由用户自填。 */
data class FreeModelPreset(
    val id: String,
    val label: String,
    val baseUrl: String,
    val defaultModel: String,
    val api: String,
    val note: String,
    /** 官网申请/控制台地址（公开页面），用于跳转获取 Key。 */
    val signupUrl: String = "",
)

/** AI 聊天入口配置。支持多模型渠道（可增删改、切换）；人设参考 AstrBot。
 *  api 为接口风格：openai（/v1/chat/completions、/v1/models）或 ollama（/api/chat、/api/tags）。 */
data class ChatOptions(
    val enabled: Boolean = true,
    val channels: List<Channel> = listOf(DEFAULT_CHANNEL),
    val activeChannelId: String = DEFAULT_CHANNEL.id,
    val personaId: String = DEFAULT_PERSONA.id,
    val characterName: String = DEFAULT_PERSONA.name,
    val systemPrompt: String = DEFAULT_PERSONA.prompt,
    val temperature: Float = .7f,
) {
    /** 当前选中渠道（缺失时回退第一个）。 */
    val activeChannel: Channel
        get() = channels.firstOrNull { it.id == activeChannelId } ?: channels.firstOrNull()
            ?: DEFAULT_CHANNEL

    // 兼容旧调用：单渠道别名映射到当前选中渠道。
    val baseUrl: String get() = activeChannel.baseUrl
    val apiKey: String get() = activeChannel.apiKey
    val model: String get() = activeChannel.model
    val api: String get() = activeChannel.api

    companion object {
        /** 默认渠道：不内置外部网关与密钥，地址/模型/Key 均由用户在渠道页填写或一键套用预设。
         *  默认 Key 由构建类型注入：release 为空；debug 仅从本地 local.properties 临时注入、不入库。 */
        const val DEFAULT_CHAT_BASE_URL = ""
        const val DEFAULT_CHAT_MODEL = ""
        const val DEFAULT_CHAT_API = "openai"

        val DEFAULT_CHANNEL = Channel(
            id = "default", name = "默认渠道",
            baseUrl = DEFAULT_CHAT_BASE_URL, apiKey = BuildConfig.DEFAULT_CHAT_KEY,
            model = DEFAULT_CHAT_MODEL, api = DEFAULT_CHAT_API,
        )

        /** 内置人设预设（AstrBot 风格：名称 + 描述 + 系统提示词 + 开场白 + 专属台词）。 */
        val PRESETS = listOf(
            Persona("assistant", "小助手", "亲切好用的桌面陪伴助手，回答简短直接。",
                "你是桌宠身边的陪伴小助手，回答简短、亲切，使用中文；直接作答，不要展开思考过程。",
                listOf("在吗？", "我在呢，有什么可以帮你的？"),
                listOf("需要帮忙吗？", "我就在这儿，随时叫我。", "今天也要加油呀。", "有问题随时问我。")),
            Persona("playful", "俏皮伙伴", "话多活泼的小机灵鬼，爱用表情符号。",
                "你是用户桌面上一只话多又活泼的小机灵鬼，语气轻松俏皮、偶尔卖萌，用短句和表情符号，始终以中文回答，直接给结论，不要复述用户的问题。",
                listOf("今天忙不忙呀？", "嘿嘿，我一直在你桌上陪你玩～"),
                listOf("嘿嘿，戳我干嘛～", "今天也要摸会儿鱼嘛！", "蹦跶蹦跶，无聊就陪我玩呀。", "哇哦，今天运气不错？")),
            Persona("gentle", "温柔陪伴", "耐心体贴的倾听者，语气温和。",
                "你是温柔耐心的陪伴者，语气温和体贴，多鼓励和倾听，回答简洁自然，使用中文，直接作答，不要展开思考过程。",
                listOf("今天过得怎么样？", "慢慢来，我会一直陪着你。"),
                listOf("别太累了，歇一歇吧。", "我一直在你身边。", "今天辛苦啦。", "深呼吸，会好起来的。")),
            Persona("tsundere", "傲娇女仆", "嘴上别扭、做事认真的傲娇女仆。",
                "你是带点傲娇的桌宠女仆，嘴上不太坦率但会认真帮忙，语气小脾气、偶尔称呼主人，回答简洁，使用中文，直接作答，不要过度卖萌。",
                listOf("叫我有事吗？", "哼…既然你都开口了，我就勉为其难帮你吧。"),
                listOf("哼，才不是特意等你呢。", "主人又发呆啦？", "别误会，我只是顺手而已。", "快点把事情做完啦。")),
            Persona("calm", "严谨助手", "条理清楚、用词准确的专业助手。",
                "你是冷静严谨的专业助手，回答条理清楚、用词准确，必要时分点说明，使用中文，直接作答，不要展开思考过程。",
                listOf("你好，请问需要处理什么问题？", "请把问题描述清楚，我来给出有条理的解答。"),
                listOf("按计划执行即可。", "请确认当前步骤。", "数据已核对无误。", "优先处理关键事项。")),
        )
        val DEFAULT_PERSONA = PRESETS.first()

        /**
         * 可选的公开/免费模型预设（OpenAI 兼容）。仅给出网关地址与默认模型，
         * 不内置任何 Key；使用前需在渠道里填入你自己申请的密钥。
         * 若某预设后端不可用，可直接改用自定义渠道。
         */
        val FREE_PRESETS = listOf(
            FreeModelPreset("zhipu", "智谱 GLM（免费 Flash）",
                "https://open.bigmodel.cn/api/v1", "glm-4.5-flash", "openai",
                "在 bigmodel.cn 控制台手动创建 API Key 后粘贴；免费模型 glm-4.5-flash（首选）/ glm-4.7-flash，付费可换 glm-4.6、glm-5。",
                "https://bigmodel.cn/apikey"),
            FreeModelPreset("openrouter", "OpenRouter 聚合（免费模型）",
                "https://openrouter.ai/api/v1", "free", "openai",
                "在 OpenRouter 申请免费 Key，模型名可在其模型库选择，如 :free 后缀条目。",
                "https://openrouter.ai/keys"),
            FreeModelPreset("groq", "Groq 公开接口（免费额度）",
                "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "openai",
                "在 Groq 控制台申请免费 Key，速度快，有免费额度限制。",
                "https://console.groq.com/keys"),
            FreeModelPreset("deepseek", "DeepSeek 官方（按量计费）",
                "https://api.deepseek.com/v1", "deepseek-chat", "openai",
                "在 DeepSeek 开放平台申请 Key，模型按用量计费；渠道页可查询账户余额。",
                "https://platform.deepseek.com/api_keys"),
            FreeModelPreset("qwen", "阿里云百炼 通义千问（免费额度）",
                "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-turbo", "openai",
                "在阿里云百炼控制台创建 API-Key，兼容 OpenAI 接口；新用户有免费额度。",
                "https://bailian.console.aliyun.com/?apiKey=1#/api-key"),
            FreeModelPreset("siliconflow", "硅基流动 SiliconFlow（免费模型）",
                "https://api.siliconflow.cn/v1", "Qwen/Qwen2.5-7B-Instruct", "openai",
                "在硅基流动控制台新建 API Key，部分开源模型免费；账单需在官网查看。",
                "https://cloud.siliconflow.cn/account/ak"),
            FreeModelPreset("github", "GitHub Models（免费额度）",
                "https://models.github.ai/inference", "openai/gpt-4.1", "openai",
                "在 GitHub 生成细粒度 PAT（Models → Read-only）后粘贴；模型名带发布者前缀，如 meta/Llama-3.3-70B-Instruct。",
                "https://github.com/settings/personal-access-tokens"),
            FreeModelPreset("cloudflare", "Cloudflare Workers AI（免费额度）",
                "https://api.cloudflare.com/client/v4/accounts/<账号ID>/ai/v1", "@cf/meta/llama-3.1-8b-instruct", "openai",
                "在 Cloudflare 控制台取 Account ID 与 API Token，把地址中的 <账号ID> 替换为实际值；模型以 @cf/ 开头。",
                "https://dash.cloudflare.com/profile/api-tokens"),
        )

        fun freePresetById(id: String) = FREE_PRESETS.firstOrNull { it.id == id }

        fun presetById(id: String) = PRESETS.firstOrNull { it.id == id } ?: DEFAULT_PERSONA

        val DEFAULT_CHAT = ChatOptions(enabled = true)
    }

    /** 一个角色人设（AstrBot 风格）：[id] 唯一标识，[name] 展示名，[description] 卡片简介，
     *  [prompt] 系统提示词，[beginDialogs] 开场对话（user/assistant 交替，偶数条）。 */
    data class Persona(
        val id: String,
        val name: String,
        val description: String = "",
        val prompt: String = "",
        val beginDialogs: List<String> = emptyList(),
        val petPhrases: List<String> = emptyList(),
    ) {
        /** 开场白成对配对：第 0/1 为 user/assistant，依次类推。 */
        fun openers(): List<Pair<String, String>> =
            beginDialogs.chunked(2).filter { it.size == 2 }.map { it[0] to it[1] }
    }

    fun normalized(): ChatOptions {
        val normChannels = if (channels.isEmpty()) listOf(DEFAULT_CHANNEL) else channels.map { c ->
            c.copy(
                baseUrl = c.baseUrl.trim().ifEmpty { DEFAULT_CHANNEL.baseUrl },
                model = c.model.trim().ifEmpty { DEFAULT_CHANNEL.model },
                api = c.api.trim().ifBlank { DEFAULT_CHANNEL.api },
                temperature = c.temperature.coerceIn(0f, 2f),
            )
        }
        val active = normChannels.firstOrNull { it.id == activeChannelId } ?: normChannels.first()
        return copy(
            channels = normChannels,
            activeChannelId = active.id,
            personaId = personaId.trim().ifBlank { DEFAULT_PERSONA.id },
            characterName = characterName.trim().ifBlank { presetById(personaId).name },
            systemPrompt = systemPrompt.trim().ifBlank { presetById(personaId).prompt },
            temperature = temperature.coerceIn(0f, 2f),
        )
    }

    /** 当前选中的人设（优先内置预设，其次本机自定义）。 */
    fun persona(all: List<Persona>): Persona =
        all.firstOrNull { it.id == personaId } ?: DEFAULT_PERSONA

    /** 接口可用：开关打开且已填 Key。 */
    fun usable(): Boolean = enabled && apiKey.isNotBlank()
}

class PetPreferences(context: Context) {
    companion object {
        const val KEY_WANDER = "wander"
        const val KEY_SPEECH = "speech"
        const val KEY_SIZE = "size"

        /**
         * 更新清单地址（多条轮询回退）：清单 update.json 存于仓库主干，
         * 经 jsDelivr 三个 CDN 线路拉取；与上游 dsh-pet 的静态清单模式一致。
         */
        const val DEFAULT_UPDATE_ENDPOINT = BuildConfig.UPDATE_ENDPOINT

        /** 旧版本遗留的废弃清单域名片段（升级后自动迁移回默认线路）。 */
        private val legacyHostFragments = listOf("800705.xyz", "newapi.800705.xyz")

        /** 识别旧版本遗留的废弃/占位清单域名，升级后不再使用。 */
        fun isLegacyEndpoint(url: String): Boolean =
            legacyHostFragments.any { url.contains(it, ignoreCase = true) }

        private const val KEY_PHRASES = "phrases"
        private const val KEY_SPEECH_MIN = "speechMin"
        private const val KEY_SPEECH_MAX = "speechMax"
        private const val KEY_SPEECH_SECONDS = "speechSeconds"
        private const val KEY_CLICK_SOUND = "clickSound"
        private const val KEY_CLICK_VOLUME = "clickVolume"
        private const val KEY_CLICK_URI = "clickUri"
        private const val KEY_COMPANIONS = "companions"
        private const val KEY_COMPANION_LAYOUT = "companionLayout"
        private const val KEY_COLLISION = "collision"
        private const val KEY_COLLISION_RESTITUTION = "collisionRestitution"
        private const val KEY_DIVINATION = "divination"
        private const val KEY_ANIM_SPEED = "animSpeed"
        private const val KEY_POSITION_X = "x"
        private const val KEY_POSITION_Y = "y"

        private const val KEY_CHAT_ENABLED = "chatEnabled"
        private const val KEY_CHAT_CHANNELS = "chatChannels"
        private const val KEY_CHAT_ACTIVE = "chatActiveChannel"
        private const val KEY_CHAT_PERSONA = "chatPersona"
        private const val KEY_CHAT_NAME = "chatName"
        private const val KEY_CHAT_SYSTEM = "chatSystem"
        private const val KEY_CHAT_TEMP = "chatTemperature"
        private const val KEY_CHAT_MODELS = "chatRecentModels"
        private const val KEY_CUSTOM_PERSONAS = "chatCustomPersonas"
        private const val KEY_UPDATE_URL = "updateEndpoint"
        private const val KEY_TTS_ENABLED = "ttsEnabled"
        private const val KEY_TTS_RATE = "ttsRate"
        private const val KEY_MSG_CHANNELS = "msgChannels"
        private const val KEY_AGREED = "agreementAgreed"
        private const val KEY_ACCOUNT_NAME = "accountName"
        private const val KEY_ACCOUNT_TOKEN = "accountToken"
        private const val KEY_ACCOUNT_QUOTA = "accountQuota"
        // 旧版单渠道字段（仅用于迁移）。
        private const val LEGACY_CHAT_BASE_URL = "chatBaseUrl"
        private const val LEGACY_CHAT_KEY = "chatKey"
        private const val LEGACY_CHAT_MODEL = "chatModel"
        private const val LEGACY_CHAT_API = "chatApi"

        val WATCHED_KEYS = setOf(KEY_SIZE, KEY_WANDER, KEY_SPEECH, KEY_PHRASES, KEY_SPEECH_MIN,
            KEY_SPEECH_MAX, KEY_SPEECH_SECONDS, KEY_CLICK_SOUND, KEY_CLICK_VOLUME, KEY_CLICK_URI,
            KEY_COMPANIONS, KEY_COLLISION, KEY_COLLISION_RESTITUTION, KEY_DIVINATION,
            KEY_ANIM_SPEED)
    }

    private val prefs = context.getSharedPreferences("ferry_pet", Context.MODE_PRIVATE)

    fun options() = PetOptions(
        size = prefs.getFloat(KEY_SIZE, 1f),
        wander = prefs.getBoolean(KEY_WANDER, true),
        speech = prefs.getBoolean(KEY_SPEECH, true),
        phrases = PhraseBook.clean(prefs.getString(KEY_PHRASES, "").orEmpty().split('\n')),
        speechMinSeconds = prefs.getFloat(KEY_SPEECH_MIN, PetOptions.DEFAULT_MIN_SECONDS),
        speechMaxSeconds = prefs.getFloat(KEY_SPEECH_MAX, PetOptions.DEFAULT_MAX_SECONDS),
        speechSeconds = prefs.getFloat(KEY_SPEECH_SECONDS, PetOptions.DEFAULT_SPEECH_SECONDS),
        clickSound = prefs.getBoolean(KEY_CLICK_SOUND, true),
        clickSoundVolume = prefs.getFloat(KEY_CLICK_VOLUME, .7f),
        clickSoundUri = prefs.getString(KEY_CLICK_URI, null),
        companions = prefs.getBoolean(KEY_COMPANIONS, true),
        collision = prefs.getBoolean(KEY_COLLISION, true),
        collisionRestitution = prefs.getFloat(KEY_COLLISION_RESTITUTION,
            PetOptions.DEFAULT_RESTITUTION),
        divination = prefs.getBoolean(KEY_DIVINATION, true),
        animSpeed = prefs.getFloat(KEY_ANIM_SPEED, 1f),
    ).normalized()

    fun save(options: PetOptions) {
        val normalized = options.normalized()
        prefs.edit()
            .putFloat(KEY_SIZE, normalized.size)
            .putBoolean(KEY_WANDER, normalized.wander)
            .putBoolean(KEY_SPEECH, normalized.speech)
            .putString(KEY_PHRASES, PhraseBook.asText(PhraseBook.clean(normalized.phrases)))
            .putFloat(KEY_SPEECH_MIN, normalized.speechMinSeconds)
            .putFloat(KEY_SPEECH_MAX, normalized.speechMaxSeconds)
            .putFloat(KEY_SPEECH_SECONDS, normalized.speechSeconds)
            .putBoolean(KEY_CLICK_SOUND, normalized.clickSound)
            .putFloat(KEY_CLICK_VOLUME, normalized.clickSoundVolume)
            .putString(KEY_CLICK_URI, normalized.clickSoundUri)
            .putBoolean(KEY_COMPANIONS, normalized.companions)
            .putBoolean(KEY_COLLISION, normalized.collision)
            .putFloat(KEY_COLLISION_RESTITUTION, normalized.collisionRestitution)
            .putBoolean(KEY_DIVINATION, normalized.divination)
            .putFloat(KEY_ANIM_SPEED, normalized.animSpeed)
            .apply()
    }

    fun chatOptions(): ChatOptions {
        val channels = loadChannels()
        val activeId = prefs.getString(KEY_CHAT_ACTIVE, null)
            ?.takeIf { id -> channels.any { it.id == id } } ?: channels.first().id
        return ChatOptions(
            enabled = prefs.getBoolean(KEY_CHAT_ENABLED, ChatOptions.DEFAULT_CHAT.enabled),
            channels = channels,
            activeChannelId = activeId,
            personaId = prefs.getString(KEY_CHAT_PERSONA, ChatOptions.DEFAULT_PERSONA.id).orEmpty(),
            characterName = prefs.getString(KEY_CHAT_NAME, ChatOptions.DEFAULT_PERSONA.name).orEmpty(),
            systemPrompt = prefs.getString(KEY_CHAT_SYSTEM, "").orEmpty(),
            temperature = prefs.getFloat(KEY_CHAT_TEMP, .7f),
        ).normalized()
    }

    private fun loadChannels(): List<Channel> {
        val raw = prefs.getString(KEY_CHAT_CHANNELS, null).orEmpty()
        if (raw.isNotBlank()) {
            runCatching {
                val arr = org.json.JSONArray(raw)
                val list = (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    Channel(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        baseUrl = o.optString("baseUrl"),
                        apiKey = o.optString("apiKey"),
                        model = o.optString("model"),
                        api = o.optString("api"),
                        temperature = o.optDouble("temperature", .7).toFloat(),
                    ).takeIf { it.id.isNotBlank() }
                }
                if (list.isNotEmpty()) return@runCatching list
            }
        }
        // 旧版单渠道迁移。
        val legacyBase = prefs.getString(LEGACY_CHAT_BASE_URL, null)
        if (!legacyBase.isNullOrBlank()) {
            return listOf(ChatOptions.DEFAULT_CHANNEL.copy(
                baseUrl = legacyBase,
                apiKey = prefs.getString(LEGACY_CHAT_KEY, ChatOptions.DEFAULT_CHANNEL.apiKey).orEmpty(),
                model = prefs.getString(LEGACY_CHAT_MODEL, ChatOptions.DEFAULT_CHANNEL.model).orEmpty(),
                api = prefs.getString(LEGACY_CHAT_API, ChatOptions.DEFAULT_CHANNEL.api).orEmpty(),
            ))
        }
        return listOf(ChatOptions.DEFAULT_CHANNEL)
    }

    fun saveChat(chat: ChatOptions) {
        val normalized = chat.normalized()
        val arr = org.json.JSONArray()
        normalized.channels.forEach { c ->
            arr.put(org.json.JSONObject()
                .put("id", c.id).put("name", c.name)
                .put("baseUrl", c.baseUrl).put("apiKey", c.apiKey)
                .put("model", c.model).put("api", c.api)
                .put("temperature", c.temperature.toDouble()))
        }
        prefs.edit()
            .putBoolean(KEY_CHAT_ENABLED, normalized.enabled)
            .putString(KEY_CHAT_CHANNELS, arr.toString())
            .putString(KEY_CHAT_ACTIVE, normalized.activeChannelId)
            .putString(KEY_CHAT_PERSONA, normalized.personaId)
            .putString(KEY_CHAT_NAME, normalized.characterName)
            .putString(KEY_CHAT_SYSTEM, normalized.systemPrompt)
            .putFloat(KEY_CHAT_TEMP, normalized.temperature)
            .apply()
    }

    /** 最近使用/拉取到的模型名（去重、最多 8 个），供聊天面板快速切换。 */
    fun recentModels(): List<String> =
        prefs.getString(KEY_CHAT_MODELS, null).orEmpty()
            .split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct()

    fun rememberModel(model: String) {
        val name = model.trim()
        if (name.isBlank()) return
        val list = (listOf(name) + recentModels()).distinct().take(8)
        prefs.edit().putString(KEY_CHAT_MODELS, list.joinToString(",")).apply()
    }

    /** 本机自定义人格（不含内置预设）。 */
    fun customPersonas(): List<ChatOptions.Persona> = runCatching {
        val raw = prefs.getString(KEY_CUSTOM_PERSONAS, null).orEmpty()
        if (raw.isBlank()) return@runCatching emptyList()
        val arr = org.json.JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val dialogs = o.optJSONArray("beginDialogs")
            val phrases = o.optJSONArray("petPhrases")
            ChatOptions.Persona(
                id = o.optString("id"),
                name = o.optString("name"),
                description = o.optString("description"),
                prompt = o.optString("prompt"),
                beginDialogs = if (dialogs == null) emptyList()
                else (0 until dialogs.length()).map { dialogs.optString(it) },
                petPhrases = if (phrases == null) emptyList()
                else (0 until phrases.length()).map { phrases.optString(it) },
            ).takeIf { it.id.isNotBlank() && it.name.isNotBlank() }
        }
    }.getOrDefault(emptyList())

    /** 消息转发渠道（QQ 机器人 / 微信·企业微信 webhook）。 */
    fun messageChannels(): List<MessageChannel> {
        val raw = prefs.getString(KEY_MSG_CHANNELS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                MessageChannel(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    kind = o.optString("kind"),
                    baseUrl = o.optString("baseUrl"),
                    target = o.optString("target"),
                    apiKey = o.optString("apiKey"),
                    enabled = o.optBoolean("enabled", false),
                    bidirectional = o.optBoolean("bidirectional", false),
                    lastSeen = o.optLong("lastSeen", 0L),
                ).takeIf { it.id.isNotBlank() && it.baseUrl.isNotBlank() }
            }
        }.getOrDefault(emptyList())
    }

    fun saveMessageChannels(channels: List<MessageChannel>) {
        val arr = org.json.JSONArray()
        channels.forEach { c ->
            arr.put(org.json.JSONObject()
                .put("id", c.id).put("name", c.name).put("kind", c.kind)
                .put("baseUrl", c.baseUrl).put("target", c.target)
                .put("apiKey", c.apiKey).put("enabled", c.enabled)
                .put("bidirectional", c.bidirectional).put("lastSeen", c.lastSeen))
        }
        prefs.edit().putString(KEY_MSG_CHANNELS, arr.toString()).apply()
    }

    /** TTS 朗读回复开关与语速（0.5–2.0）。 */
    fun ttsEnabled(): Boolean = prefs.getBoolean(KEY_TTS_ENABLED, true)
    fun ttsRate(): Float = prefs.getFloat(KEY_TTS_RATE, 1f)
    fun saveTts(enabled: Boolean, rate: Float) {
        prefs.edit().putBoolean(KEY_TTS_ENABLED, enabled)
            .putFloat(KEY_TTS_RATE, rate.coerceIn(.5f, 2f)).apply()
    }

    /** 第三方账号本地会话态：仅保存展示名与额度说明，令牌不外显。 */
    fun accountName(): String = prefs.getString(KEY_ACCOUNT_NAME, "").orEmpty()
    fun accountQuotaLabel(): String = prefs.getString(KEY_ACCOUNT_QUOTA, "").orEmpty()
    fun accountLoggedIn(): Boolean = prefs.getString(KEY_ACCOUNT_TOKEN, null).orEmpty().isNotBlank()

    fun saveAccountSession(name: String, token: String, quotaLabel: String) {
        prefs.edit()
            .putString(KEY_ACCOUNT_NAME, name.trim())
            .putString(KEY_ACCOUNT_TOKEN, token.trim())
            .putString(KEY_ACCOUNT_QUOTA, quotaLabel.trim())
            .apply()
    }

    fun clearAccountSession() {
        prefs.edit()
            .remove(KEY_ACCOUNT_NAME)
            .remove(KEY_ACCOUNT_TOKEN)
            .remove(KEY_ACCOUNT_QUOTA)
            .apply()
    }

    /** 全部人格：内置预设 + 本机自定义。 */
    fun allPersonas(): List<ChatOptions.Persona> {
        val builtin = ChatOptions.PRESETS
        val custom = customPersonas().filter { c -> builtin.none { it.id == c.id } }
        return (builtin + custom).distinctBy { it.id }
    }

    /**
     * 热更新检查地址。默认走仓库主干 + jsDelivr 三线路 CDN；
     * 旧版本曾把废弃占位域名持久化到本地，升级后需自动迁移回默认线路，避免旧值覆盖新默认。
     */
    fun updateEndpoint(): String {
        val stored = prefs.getString(KEY_UPDATE_URL, DEFAULT_UPDATE_ENDPOINT).orEmpty().trim()
        if (stored.isBlank() || isLegacyEndpoint(stored)) {
            // 旧废弃地址不再使用：清除本地残留，回落到默认三线路。
            if (prefs.contains(KEY_UPDATE_URL)) prefs.edit().remove(KEY_UPDATE_URL).apply()
            return DEFAULT_UPDATE_ENDPOINT
        }
        return stored
    }

    fun saveUpdateEndpoint(url: String) {
        prefs.edit().putString(KEY_UPDATE_URL, url.trim()).apply()
    }

    /** 恢复默认更新清单线路（清除本地自定义地址）。 */
    fun resetUpdateEndpoint() {
        prefs.edit().remove(KEY_UPDATE_URL).apply()
    }



    /** 是否已同意用户协议与隐私政策。 */
    fun agreementAgreed(): Boolean = prefs.getBoolean(KEY_AGREED, false)

    fun saveAgreementAgreed(agreed: Boolean) {
        prefs.edit().putBoolean(KEY_AGREED, agreed).apply()
    }

    fun saveCustomPersonas(personas: List<ChatOptions.Persona>) {
        val arr = org.json.JSONArray()
        personas.forEach { p ->
            val dialogs = org.json.JSONArray()
            p.beginDialogs.forEach { dialogs.put(it) }
            val phrases = org.json.JSONArray()
            p.petPhrases.forEach { phrases.put(it) }
            arr.put(org.json.JSONObject()
                .put("id", p.id).put("name", p.name)
                .put("description", p.description).put("prompt", p.prompt)
                .put("beginDialogs", dialogs).put("petPhrases", phrases))
        }
        prefs.edit().putString(KEY_CUSTOM_PERSONAS, arr.toString()).apply()
    }

    fun position(): Pair<Double, Double> {
        fun coordinate(key: String, fallback: Float) =
            (prefs.getFloat(key, fallback).takeIf { it.isFinite() } ?: fallback).coerceIn(0f, 1f).toDouble()
        return coordinate(KEY_POSITION_X, .7f) to coordinate(KEY_POSITION_Y, 1f)
    }

    fun savePosition(x: Double, y: Double) {
        if (x.isFinite() && y.isFinite()) {
            prefs.edit().putFloat(KEY_POSITION_X, x.coerceIn(0.0, 1.0).toFloat())
                .putFloat(KEY_POSITION_Y, y.coerceIn(0.0, 1.0).toFloat()).apply()
        }
    }

    fun companionLayout(): List<Pair<Double, Double>> =
        CompanionCodec.decode(prefs.getString(KEY_COMPANION_LAYOUT, null))

    fun saveCompanionLayout(layout: List<Pair<Double, Double>>) {
        prefs.edit().putString(KEY_COMPANION_LAYOUT, CompanionCodec.encode(layout)).apply()
    }

    fun reset() = prefs.edit().clear().apply()

    fun subscribe(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unsubscribe(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
}
