package com.ferry.pet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ferry.pet.chat.ChatClient
import com.ferry.pet.chat.ChatMessage
import kotlinx.coroutines.launch

/**
 * 桌宠内置的 AI 聊天面板。借鉴 Ferry 摆渡的暖橙深色视觉；
 * 接口配置来自设置页（[ChatOptions]），未配置时给出引导提示。
 */
class ChatActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ChatScreen(onBack = { finish() }) }
    }
}

@Composable
private fun ChatScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { PetPreferences(context) }
    val saved = preferences.chatOptions()

    // 聊天会话内可临时切换模型；写回本机偏好，下次进入沿用。
    var model by remember(saved.activeChannel.model) { mutableStateOf(saved.activeChannel.model) }
    var recentModels by remember { mutableStateOf(preferences.recentModels()) }
    var menuOpen by remember { mutableStateOf(false) }
    var options by remember(saved) { mutableStateOf(saved) }

    val messages = remember { mutableStateOf(mutableListOf<ChatMessage>()) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // 当前人格及其开场白（user/assistant 交替），作为首轮上下文注入。
    val persona = remember(saved.personaId) {
        preferences.allPersonas().firstOrNull { it.id == saved.personaId }
            ?: ChatOptions.DEFAULT_PERSONA
    }
    val openers = remember(persona.id) { persona.openers() }

    val notConfigured = !saved.usable()

    MaterialTheme {
        Surface(Modifier.fillMaxSize().background(DeepBg)) {
            Column(Modifier.fillMaxSize().imePadding()) {
                // 顶部栏：返回 + 角色名/模型 + 清空对话。
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Amber)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(saved.characterName.ifBlank { persona.name }, color = Amber,
                            style = MaterialTheme.typography.titleMedium)
                        Box {
                            TextButton(onClick = { menuOpen = true },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) {
                                Text(model, color = MutedAmber,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                val candidates = (listOf(model) + recentModels).distinct()
                                candidates.forEach { candidate ->
                                    DropdownMenuItem(text = { Text(candidate) },
                                        onClick = {
                                            model = candidate
                                            preferences.rememberModel(candidate)
                                            recentModels = preferences.recentModels()
                                            options = options.copy(
                                                channels = options.channels.map {
                                                    if (it.id == options.activeChannelId) it.copy(model = candidate) else it
                                                })
                                            menuOpen = false
                                        })
                                }
                            }
                        }
                    }
                    if (messages.value.isNotEmpty()) {
                        TextButton(onClick = { messages.value = mutableListOf(); status = "" }) {
                            Text(stringResource(R.string.chat_clear), color = MutedAmber)
                        }
                    }
                }

                if (notConfigured) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.chat_not_configured), color = MutedAmber)
                    }
                    return@Surface
                }

                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (openers.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(R.string.chat_openers_preview),
                                    color = MutedAmber,
                                    style = MaterialTheme.typography.labelSmall)
                                openers.forEach { (u, a) ->
                                    OpenerBubble(u, isUser = true)
                                    OpenerBubble(a, isUser = false)
                                }
                            }
                        }
                    }
                    items(messages.value) { msg -> Bubble(msg, saved.characterName.ifBlank { persona.name }) }
                }

                if (status.isNotBlank()) {
                    Text(status, color = Color(0xFFE06A6A),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }

                Row(Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        placeholder = { Text(stringResource(R.string.chat_input_hint), color = MutedAmber) },
                        maxLines = 4,
                    )
                    IconButton(
                        onClick = {
                            val text = input.trim()
                            if (text.isEmpty() || busy) return@IconButton
                            val openerHistory = openers.flatMap {
                                listOf(ChatMessage("user", it.first), ChatMessage("assistant", it.second))
                            }
                            val history = openerHistory + messages.value.toList() + ChatMessage("user", text)
                            messages.value = (messages.value + ChatMessage("user", text)).toMutableList()
                            input = ""
                            busy = true
                            status = ""
                            scope.launch {
                                val result = ChatClient(options.activeChannel, options.systemPrompt,
                                    options.activeChannel.temperature).send(history)
                                busy = false
                                when (result) {
                                    is ChatClient.Result.Success -> {
                                        messages.value = (messages.value +
                                            ChatMessage("assistant", result.reply)).toMutableList()
                                    }
                                    is ChatClient.Result.Failure -> status = result.message
                                }
                            }
                        },
                        enabled = !busy) {
                        if (busy) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        else Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Amber)
                    }
                }
            }
        }
    }
}

/** 开场白预览气泡：灰色只读，区分左右，提示这是预设开场而非真实对话。 */
@Composable
private fun OpenerBubble(text: String, isUser: Boolean) {
    Row(Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Box(Modifier.widthIn(max = 280.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(OpenerBg)
            .padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(text, color = OpenerText,
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Bubble(msg: ChatMessage, characterName: String) {
    val isUser = msg.role == "user"
    Row(Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Column(Modifier.widthIn(max = 300.dp)) {
            if (!isUser) {
                Text(characterName, color = MutedAmber,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
            }
            Box(
                Modifier.clip(RoundedCornerShape(16.dp))
                    .background(if (isUser) Amber.copy(alpha = .18f) else BubbleDark)
                    .padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(msg.content, color = if (isUser) AmberSoft else TextLight)
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
private val OpenerBg = Color(0x14FFFFFF)
private val OpenerText = Color(0x99EDE7DF)
