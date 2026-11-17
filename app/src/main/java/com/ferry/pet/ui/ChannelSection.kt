package com.ferry.pet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ferry.pet.Channel
import com.ferry.pet.ChatOptions
import com.ferry.pet.PetPreferences
import com.ferry.pet.R
import com.ferry.pet.chat.ChatClient
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 模型渠道页：多套网关（名称/接口类型/BaseURL/Key/模型/温度）可增删改、一键切换；
 * 选中渠道可拉取模型列表。
 */
@Composable
fun ChannelSection(options: ChatOptions, onChanged: (ChatOptions) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { PetPreferences(context) }
    val scope = rememberCoroutineScope()

    var channels by remember(options.channels) { mutableStateOf(options.channels) }
    var activeId by remember(options.activeChannelId) { mutableStateOf(options.activeChannelId) }

    var editingId by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf("") }
    var editBase by remember { mutableStateOf("") }
    var editKey by remember { mutableStateOf("") }
    var editModel by remember { mutableStateOf("") }
    var editApi by remember { mutableStateOf("openai") }
    var editTemp by remember { mutableStateOf(.7f) }

    var fetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var fetching by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf("") }

    fun emit() = onChanged(options.copy(channels = channels, activeChannelId = activeId))

    fun startEdit(c: Channel?) {
        editingId = c?.id ?: "new"
        editName = c?.name.orEmpty()
        editBase = c?.baseUrl.orEmpty()
        editKey = c?.apiKey.orEmpty()
        editModel = c?.model.orEmpty()
        editApi = c?.api ?: "openai"
        editTemp = c?.temperature ?: .7f
        fetchedModels = emptyList()
        fetchError = ""
    }

    SectionTitle(R.string.nav_channel)
    SettingSwitch(R.string.chat_enabled, options.enabled) { onChanged(options.copy(enabled = it)) }
    Text(stringResource(R.string.chat_hint), color = Muted,
        style = androidx.compose.ui.text.TextStyle(fontSize = 12f.sp))
    androidx.compose.material3.HorizontalDivider()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        channels.forEach { c ->
            val selected = c.id == activeId
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) Amber.copy(alpha = .14f) else SurfaceVariant)
                    .border(1.dp, if (selected) Amber else Outline, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(c.name.ifBlank { c.model }, color = if (selected) Amber else TextLight)
                    Text(c.model.ifBlank { c.baseUrl }, color = Muted,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = androidx.compose.ui.unit.TextUnit(12f,
                                androidx.compose.ui.unit.TextUnitType.Sp)))
                }
                TextButton(onClick = { activeId = c.id; emit() }) {
                    Text(stringResource(if (selected) R.string.channel_selected else R.string.channel_use),
                        color = if (selected) Amber else Color.Unspecified)
                }
                TextButton(onClick = { startEdit(c) }) { Text(stringResource(R.string.channel_edit)) }
                if (channels.size > 1) {
                    TextButton(onClick = {
                        val next = channels.filter { it.id != c.id }
                        channels = next
                        if (activeId == c.id) activeId = next.first().id
                        emit()
                    }) { Text(stringResource(R.string.channel_delete), color = Color(0xFFE06A6A)) }
                }
            }
        }
        if (editingId == null) {
            TextButton(onClick = { startEdit(null) }) { Text(stringResource(R.string.channel_new)) }
        }
    }

    if (editingId != null) {
        OutlinedTextField(value = editName, onValueChange = { editName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.channel_label)) }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { editApi = "openai" }) {
                Text(stringResource(R.string.chat_api_openai),
                    color = if (editApi == "openai") Amber else Color.Unspecified)
            }
            TextButton(onClick = { editApi = "ollama" }) {
                Text(stringResource(R.string.chat_api_ollama),
                    color = if (editApi == "ollama") Amber else Color.Unspecified)
            }
        }
        OutlinedTextField(value = editBase, onValueChange = { editBase = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.chat_base_url)) }, singleLine = true)
        OutlinedTextField(value = editKey, onValueChange = { editKey = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.chat_api_key)) }, singleLine = true)
        OutlinedTextField(value = editModel, onValueChange = { editModel = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.chat_model)) }, singleLine = true)

        fetchedModels.forEach { candidate ->
            TextButton(onClick = { editModel = candidate }, Modifier.padding(horizontal = 4.dp)) {
                Text(candidate)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                fetching = true; fetchError = ""
                val snapshot = Channel(UUID.randomUUID().toString(), editName,
                    editBase.trim(), editKey.trim(), editModel.trim(), editApi, editTemp)
                scope.launch {
                    when (val result = ChatClient(snapshot).fetchModels()) {
                        is ChatClient.ModelsResult.Success -> {
                            fetchedModels = result.models
                            result.models.forEach { prefs.rememberModel(it) }
                            if (editModel.isBlank() && result.models.isNotEmpty())
                                editModel = result.models.first()
                        }
                        is ChatClient.ModelsResult.Failure -> fetchError = result.message
                    }
                    fetching = false
                }
            }, enabled = !fetching) {
                Text(stringResource(R.string.chat_fetch_models), Modifier.padding(horizontal = 4.dp))
            }
            if (fetching) CircularProgressIndicator(modifier = Modifier.padding(8.dp), strokeWidth = 2.dp)
        }
        if (fetchError.isNotBlank()) Text(fetchError, color = Color(0xFFB00020))

        SettingSlider(label = R.string.chat_temperature,
            valueText = String.format("%.1f", editTemp),
            value = editTemp, range = 0f..2f, changed = { editTemp = it }, onFinished = { })

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                val cleanName = editName.trim().ifBlank { editModel.trim().ifBlank { "渠道" } }
                val saved = Channel(
                    id = if (editingId == "new") UUID.randomUUID().toString() else editingId!!,
                    name = cleanName, baseUrl = editBase.trim(), apiKey = editKey.trim(),
                    model = editModel.trim(), api = editApi, temperature = editTemp)
                val list = channels.toMutableList()
                val idx = list.indexOfFirst { it.id == saved.id }
                if (idx >= 0) list[idx] = saved else list.add(saved)
                channels = list
                if (activeId == editingId || activeId.isBlank()) activeId = saved.id
                emit()
                editingId = null
            }) { Text(stringResource(R.string.channel_save)) }
            TextButton(onClick = { editingId = null }) {
                Text(stringResource(R.string.chat_persona_cancel))
            }
        }
    }

    var ttsOn by remember { mutableStateOf(prefs.ttsEnabled()) }
    var ttsRate by remember { mutableStateOf(prefs.ttsRate()) }

    LaunchedEffect(ttsOn, ttsRate) { prefs.saveTts(ttsOn, ttsRate) }

    androidx.compose.material3.HorizontalDivider()
    SettingSwitch(R.string.tts_enable, ttsOn) { ttsOn = it }
    SettingSlider(label = R.string.tts_speech_rate,
        valueText = String.format("%.1f", ttsRate),
        value = ttsRate, range = .5f..2f, changed = { ttsRate = it }, onFinished = { })

    LaunchedEffect(channels, activeId) { emit() }
}

private val Amber = Color(0xFFE8A87C)
private val SurfaceVariant = Color(0xFF1E3E53)
private val Outline = Color(0xFF3E5C6E)
private val Muted = Color(0xB8E8A87C)
private val TextLight = Color(0xFFEDE7DF)
