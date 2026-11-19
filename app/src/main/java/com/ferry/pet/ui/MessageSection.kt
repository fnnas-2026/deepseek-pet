package com.ferry.pet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ferry.pet.MessageChannel
import com.ferry.pet.PetPreferences
import com.ferry.pet.R
import com.ferry.pet.chat.ChannelLinkParser
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import java.util.UUID

/**
 * 消息渠道页：配置 QQ 机器人接入端 / 微信·企业微信 webhook，可增删改、单独启用。
 * 回复转发在聊天面板内触发；本页只负责渠道配置与持久化。
 */
@Composable
fun MessageSection(preferences: PetPreferences) {
    val prefs = preferences
    var channels by remember { mutableStateOf(prefs.messageChannels()) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf("") }
    var editKind by remember { mutableStateOf(MessageChannel.KIND_QQ) }
    var editBase by remember { mutableStateOf("") }
    var editTarget by remember { mutableStateOf("") }
    var editToken by remember { mutableStateOf("") }
    var editEnabled by remember { mutableStateOf(false) }
    var editBidirectional by remember { mutableStateOf(false) }
    val context = LocalContext.current

    fun persist(next: List<MessageChannel>) {
        channels = next
        prefs.saveMessageChannels(next)
    }

    fun startEdit(c: MessageChannel?) {
        editingId = c?.id ?: "new"
        editName = c?.name.orEmpty()
        editKind = c?.kind ?: MessageChannel.KIND_QQ
        editBase = c?.baseUrl.orEmpty()
        editTarget = c?.target.orEmpty()
        editToken = c?.apiKey.orEmpty()
        editEnabled = c?.enabled ?: false
        editBidirectional = c?.bidirectional ?: false
    }

    fun parseClipboard() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = cm?.primaryClip ?: return
        val text = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).text?.toString() }
            .firstOrNull().orEmpty()
        val parsed = ChannelLinkParser.parse(text)
        if (parsed.baseUrl.isBlank()) {
            // 解析不到，保持原表单，不覆盖已填内容。
            return
        }
        editBase = parsed.baseUrl
        if (parsed.target.isNotBlank()) editTarget = parsed.target
        if (parsed.apiKey.isNotBlank()) editToken = parsed.apiKey
        if (parsed.kind.isNotBlank()) editKind = parsed.kind
    }

    SectionTitle(R.string.nav_message)
    Text(stringResource(R.string.msg_hint), color = Muted,
        style = androidx.compose.ui.text.TextStyle(fontSize = 12f.sp))
    Text(stringResource(R.string.msg_bind_tip), color = Muted,
        style = androidx.compose.ui.text.TextStyle(fontSize = 12f.sp))

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        channels.forEach { c ->
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceVariant)
                    .border(1.dp, Outline, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(c.name.ifBlank { kindLabel(c.kind) }, color = TextLight)
                    Text(if (c.kind == MessageChannel.KIND_QQ) "QQ" else "微信",
                        color = Muted, style = androidx.compose.ui.text.TextStyle(fontSize = 12f.sp))
                }
                SettingSwitch(R.string.msg_enabled, c.enabled) { checked ->
                    persist(channels.map { if (it.id == c.id) it.copy(enabled = checked) else it })
                }
                TextButton(onClick = { startEdit(c) }) { Text(stringResource(R.string.channel_edit)) }
                TextButton(onClick = { persist(channels.filter { it.id != c.id }) }) {
                    Text(stringResource(R.string.channel_delete), color = Color(0xFFE06A6A))
                }
            }
        }
        if (editingId == null) {
            TextButton(onClick = { startEdit(null) }) { Text(stringResource(R.string.msg_new)) }
        }
    }

    if (editingId != null) {
        OutlinedTextField(value = editName, onValueChange = { editName = it },
            modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.channel_label)) },
            singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { editKind = MessageChannel.KIND_QQ }) {
                Text(stringResource(R.string.msg_kind_qq),
                    color = if (editKind == MessageChannel.KIND_QQ) Amber else Color.Unspecified)
            }
            TextButton(onClick = { editKind = MessageChannel.KIND_WECHAT }) {
                Text(stringResource(R.string.msg_kind_wechat),
                    color = if (editKind == MessageChannel.KIND_WECHAT) Amber else Color.Unspecified)
            }
        }
        Text(
            stringResource(if (editKind == MessageChannel.KIND_WECHAT)
                R.string.msg_wechat_guide else R.string.msg_qq_guide),
            color = Muted, style = androidx.compose.ui.text.TextStyle(fontSize = 12f.sp))
        OutlinedTextField(value = editBase, onValueChange = { editBase = it },
            modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.msg_base_url)) },
            singleLine = true)
        if (editKind != MessageChannel.KIND_WECHAT) {
            OutlinedTextField(value = editTarget, onValueChange = { editTarget = it },
                modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.msg_target)) },
                supportingText = { Text(stringResource(R.string.msg_target_hint)) }, singleLine = true)
        }
        OutlinedTextField(value = editToken, onValueChange = { editToken = it },
            modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.msg_token)) },
            singleLine = true)

        TextButton(onClick = { parseClipboard() }) { Text(stringResource(R.string.msg_paste_parse), color = Amber) }
        Text(stringResource(R.string.msg_paste_hint), color = Muted,
            style = androidx.compose.ui.text.TextStyle(fontSize = 12f.sp))

        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = editBidirectional, onCheckedChange = { editBidirectional = it })
            Text(stringResource(R.string.msg_bidirectional), color = TextLight)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                val saved = MessageChannel(
                    id = if (editingId == "new") UUID.randomUUID().toString() else editingId!!,
                    name = editName.trim().ifBlank { kindLabel(editKind) },
                    kind = editKind, baseUrl = editBase.trim(), target = editTarget.trim(),
                    apiKey = editToken.trim(), enabled = editEnabled,
                    bidirectional = editBidirectional)
                val list = channels.toMutableList()
                val idx = list.indexOfFirst { it.id == saved.id }
                if (idx >= 0) list[idx] = saved else list.add(saved)
                persist(list)
                editingId = null
            }) { Text(stringResource(R.string.channel_save)) }
            TextButton(onClick = { editingId = null }) { Text(stringResource(R.string.chat_persona_cancel)) }
        }
    }
}

private fun kindLabel(kind: String) =
    if (kind == MessageChannel.KIND_WECHAT) "微信渠道" else "QQ 渠道"

private val Amber = Color(0xFFE8A87C)
private val SurfaceVariant = Color(0xFF1E3E53)
private val Outline = Color(0xFF3E5C6E)
private val Muted = Color(0xB8E8A87C)
private val TextLight = Color(0xFFEDE7DF)
