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
import com.ferry.pet.ChatOptions
import com.ferry.pet.PetPreferences
import com.ferry.pet.R
import java.util.UUID

/**
 * 人设设置页：内置 + 自定义人格卡片，可新建/编辑/删除；含名称、简介、系统提示词、
 * 开场白（user/assistant 交替）与专属桌宠台词。参考 AstrBot 人格模型。
 */
@Composable
fun PersonaSection(options: ChatOptions, onChanged: (ChatOptions) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { PetPreferences(context) }

    var personaId by remember(options.personaId) { mutableStateOf(options.personaId) }
    var name by remember(options.characterName) { mutableStateOf(options.characterName) }
    var system by remember(options.systemPrompt) { mutableStateOf(options.systemPrompt) }

    var personas by remember { mutableStateOf(prefs.allPersonas()) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf("") }
    var editDesc by remember { mutableStateOf("") }
    var editPrompt by remember { mutableStateOf("") }
    var editOpeners by remember { mutableStateOf("") }
    var editPhrases by remember { mutableStateOf("") }
    var editingCustom by remember { mutableStateOf(false) }

    fun commitCustom(list: List<ChatOptions.Persona>) = prefs.saveCustomPersonas(list)

    fun selectPersona(p: ChatOptions.Persona) {
        personaId = p.id
        name = p.name
        system = p.prompt
        onChanged(options.copy(personaId = p.id, characterName = p.name, systemPrompt = p.prompt))
    }

    fun emit() = onChanged(options.copy(
        personaId = personaId, characterName = name, systemPrompt = system))

    Text(stringResource(R.string.chat_persona))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        personas.forEach { p ->
            val selected = p.id == personaId
            val isBuiltin = ChatOptions.PRESETS.any { it.id == p.id }
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) Amber.copy(alpha = .14f) else SurfaceVariant)
                    .border(1.dp, if (selected) Amber else Outline, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, color = if (selected) Amber else TextLight)
                    if (p.description.isNotBlank()) {
                        Text(p.description, color = Muted,
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = androidx.compose.ui.unit.TextUnit(12f,
                                    androidx.compose.ui.unit.TextUnitType.Sp)))
                    }
                }
                TextButton(onClick = { selectPersona(p) }) {
                    Text(stringResource(if (selected) R.string.chat_persona_selected else R.string.chat_persona_use),
                        color = if (selected) Amber else Color.Unspecified)
                }
                TextButton(onClick = {
                    editingId = p.id
                    editingCustom = !isBuiltin
                    editName = p.name
                    editDesc = p.description
                    editPrompt = p.prompt
                    editOpeners = p.beginDialogs.joinToString("\n")
                    editPhrases = p.petPhrases.joinToString("\n")
                }) { Text(stringResource(R.string.chat_persona_edit)) }
                if (!isBuiltin) {
                    TextButton(onClick = {
                        val list = prefs.customPersonas().filter { it.id != p.id }
                        commitCustom(list)
                        personas = ChatOptions.PRESETS + list
                        if (personaId == p.id) {
                            personaId = ChatOptions.DEFAULT_PERSONA.id
                            name = ChatOptions.DEFAULT_PERSONA.name
                            system = ChatOptions.DEFAULT_PERSONA.prompt
                            emit()
                        }
                    }) { Text(stringResource(R.string.chat_persona_delete), color = Color(0xFFE06A6A)) }
                }
            }
        }

        if (editingId == null) {
            TextButton(onClick = {
                editingId = "new"; editingCustom = true
                editName = ""; editDesc = ""; editPrompt = ""; editOpeners = ""; editPhrases = ""
            }) { Text(stringResource(R.string.chat_persona_new)) }
        }
    }

    if (editingId != null) {
        OutlinedTextField(value = editName, onValueChange = { editName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.chat_character_name)) }, singleLine = true)
        OutlinedTextField(value = editDesc, onValueChange = { editDesc = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.chat_persona_desc)) }, singleLine = true)
        OutlinedTextField(value = editPrompt, onValueChange = { editPrompt = it },
            modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
            label = { Text(stringResource(R.string.chat_system_prompt)) })
        OutlinedTextField(value = editOpeners, onValueChange = { editOpeners = it },
            modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
            label = { Text(stringResource(R.string.chat_persona_openers)) })
        OutlinedTextField(value = editPhrases, onValueChange = { editPhrases = it },
            modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
            label = { Text(stringResource(R.string.chat_persona_phrases)) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                val clean = editName.trim()
                if (clean.isBlank()) return@TextButton
                val dialogs = editOpeners.split('\n').map { it.trim() }.filter { it.isNotBlank() }
                val phrases = editPhrases.split('\n').map { it.trim() }.filter { it.isNotBlank() }
                val saved = prefs.customPersonas().toMutableList()
                if (editingId == "new") {
                    val p = ChatOptions.Persona(UUID.randomUUID().toString(), clean,
                        editDesc.trim(), editPrompt.trim(), dialogs, phrases)
                    saved.add(p)
                    selectPersona(p)
                } else {
                    val idx = saved.indexOfFirst { it.id == editingId }
                    if (idx >= 0) {
                        val old = saved[idx]
                        val p = old.copy(name = clean, description = editDesc.trim(),
                            prompt = editPrompt.trim(), beginDialogs = dialogs, petPhrases = phrases)
                        saved[idx] = p
                        selectPersona(p)
                    }
                }
                commitCustom(saved)
                personas = ChatOptions.PRESETS + saved
                editingId = null
            }) { Text(stringResource(R.string.chat_persona_save)) }
            TextButton(onClick = { editingId = null }) {
                Text(stringResource(R.string.chat_persona_cancel))
            }
        }
    }

    OutlinedTextField(value = name, onValueChange = { name = it; emit() },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.chat_character_name)) }, singleLine = true)
    OutlinedTextField(value = system, onValueChange = { system = it; emit() },
        modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
        label = { Text(stringResource(R.string.chat_system_prompt)) })
}

private val Amber = Color(0xFFE8A87C)
private val SurfaceVariant = Color(0xFF1E3E53)
private val Outline = Color(0xFF3E5C6E)
private val Muted = Color(0xB8E8A87C)
private val TextLight = Color(0xFFEDE7DF)
