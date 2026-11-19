package com.ferry.pet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ferry.pet.ChatOptions
import com.ferry.pet.PetPreferences
import com.ferry.pet.R
import com.ferry.pet.chat.ChatClient
import com.ferry.pet.chat.ChatMessage
import com.ferry.pet.chat.MessagePusher
import com.ferry.pet.chat.Speaker
import com.ferry.pet.chat.VoiceInput
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/**
 * 悬浮聊天小窗：不进全屏 Activity，以紧凑卡片展示最近对话 + 底部输入框；
 * 回复可由系统 TTS 朗读。关闭后回到桌宠桌面。
 */
@Composable
fun FloatingChatPanel(
    preferences: PetPreferences,
    speaker: Speaker,
    onClose: () -> Unit,
) {
    val saved = preferences.chatOptions()
    val persona = remember(saved.personaId) {
        preferences.allPersonas().firstOrNull { it.id == saved.personaId }
            ?: ChatOptions.DEFAULT_PERSONA
    }
    val openers = remember(persona.id) { persona.openers() }

    val context = LocalContext.current
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var voice by remember { mutableStateOf<VoiceInput?>(null) }

    val startVoice: () -> Unit = {
        val v = voice ?: VoiceInput(context, onResult = {
            input = if (input.isBlank()) it else "$input $it"
            listening = false
        }, onError = { status = it; listening = false }).also { voice = it }
        listening = true; status = "正在聆听…"
        v.start()
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoice() else status = "缺少录音权限"
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { voice?.destroy() }
    }

    Column(
        Modifier
            .widthIn(max = 320.dp)
            .heightIn(max = 420.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(DeepBg)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Amber)
            }
            Column(Modifier.weight(1f)) {
                Text(saved.characterName.ifBlank { persona.name }, color = Amber,
                    style = androidx.compose.ui.text.TextStyle(fontSize = 15f.sp))
                Text(saved.activeChannel.model, color = MutedAmber,
                    style = androidx.compose.ui.text.TextStyle(fontSize = 11f.sp))
            }
            val lastReply = messages.lastOrNull { it.role == "assistant" }?.content
            if (!lastReply.isNullOrBlank()) {
                TextButton(onClick = {
                    val pushChannels = preferences.messageChannels().filter { it.enabled }
                    if (pushChannels.isEmpty()) { status = "未启用消息渠道" } else {
                        scope.launch {
                            when (val r = MessagePusher.push(pushChannels, lastReply)) {
                                is MessagePusher.Result.Success ->
                                    status = "已推送到 ${r.sent} 个渠道"
                                is MessagePusher.Result.Failure -> status = r.message
                            }
                        }
                    }
                }) { Text("转发", color = MutedAmber) }
            }
            if (messages.isNotEmpty()) {
                TextButton(onClick = { messages = emptyList(); status = "" }) {
                    Text("清空", color = MutedAmber)
                }
            }
        }

        LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 220.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { msg -> FloatBubble(msg, saved.characterName.ifBlank { persona.name }) }
        }
        if (status.isNotBlank()) Text(status, color = Color(0xFFE06A6A),
            style = androidx.compose.ui.text.TextStyle(fontSize = 12f.sp))

        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                placeholder = { Text("说点什么…", color = MutedAmber) },
                maxLines = 3,
            )
            IconButton(onClick = {
                if (listening) { voice?.destroy(); listening = false; status = "" }
                else if (VoiceInput.hasPermission(context)) startVoice()
                else micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
            }) {
                Icon(painterResource(if (listening) R.drawable.ic_mic_off else R.drawable.ic_mic),
                    contentDescription = null,
                    tint = if (listening) Color(0xFFE06A6A) else Amber)
            }
            IconButton(onClick = {
                val text = input.trim()
                if (text.isEmpty() || busy) return@IconButton
                val openerHistory = openers.flatMap {
                    listOf(ChatMessage("user", it.first), ChatMessage("assistant", it.second))
                }
                val history = openerHistory + messages + ChatMessage("user", text)
                messages = messages + ChatMessage("user", text)
                input = ""
                busy = true; status = ""
                scope.launch {
                    val result = ChatClient(saved.activeChannel, saved.systemPrompt,
                        saved.activeChannel.temperature).send(history)
                    busy = false
                    when (result) {
                        is ChatClient.Result.Success -> {
                            messages = messages + ChatMessage("assistant", result.reply)
                            speaker.speak(result.reply)
                        }
                        is ChatClient.Result.Failure -> status = result.message
                    }
                }
            }, enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.padding(4.dp))
                else Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Amber)
            }
        }
    }
}

@Composable
private fun FloatBubble(msg: ChatMessage, characterName: String) {
    val isUser = msg.role == "user"
    Row(Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Column(Modifier.widthIn(max = 240.dp)) {
            if (!isUser) Text(characterName, color = MutedAmber,
                style = androidx.compose.ui.text.TextStyle(fontSize = 10f.sp),
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
            Box(Modifier.clip(RoundedCornerShape(14.dp))
                .background(if (isUser) Amber.copy(alpha = .18f) else BubbleDark)
                .padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(msg.content, color = if (isUser) AmberSoft else TextLight,
                    style = androidx.compose.ui.text.TextStyle(fontSize = 13f.sp))
            }
        }
    }
}

private val DeepBg = Color(0xFF0E2233)
private val BubbleDark = Color(0xFF173247)
private val Amber = Color(0xFFE8A87C)
private val AmberSoft = Color(0xFFF3C9A6)
private val MutedAmber = Color(0xB8E8A87C)
private val TextLight = Color(0xFFEDE7DF)
