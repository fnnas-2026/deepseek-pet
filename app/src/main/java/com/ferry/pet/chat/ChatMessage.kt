package com.ferry.pet.chat

/** 一条聊天消息。[role] 取 system / user / assistant。 */
data class ChatMessage(val role: String, val content: String)

/** 一次问答会话的记录，便于在面板里回看历史。 */
data class ChatTurn(val user: String, val assistant: String)
