package com.ferry.pet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ferry.pet.ModuleIds
import com.ferry.pet.PetOptions
import com.ferry.pet.R

/**
 * 插件（可选能力模块）管理页：列出注册表内的内置模块，开关直接联动
 * [PetOptions] 对应偏好；关闭的模块不构造对象、不持有窗口与线程。
 */
@Composable
fun PluginSection(options: PetOptions, onChanged: (PetOptions) -> Unit) {
    SectionTitle(R.string.nav_plugins)
    PluginRow(
        titleRes = R.string.plugin_wander, descRes = R.string.plugin_wander_desc,
        enabled = options.wander) { onChanged(options.copy(wander = it)) }
    PluginRow(
        titleRes = R.string.plugin_speech, descRes = R.string.plugin_speech_desc,
        enabled = options.speech) { onChanged(options.copy(speech = it)) }
    PluginRow(
        titleRes = R.string.plugin_click_sound, descRes = R.string.plugin_click_sound_desc,
        enabled = options.clickSound) { onChanged(options.copy(clickSound = it)) }
    PluginRow(
        titleRes = R.string.plugin_companion, descRes = R.string.plugin_companion_desc,
        enabled = options.companions) { onChanged(options.copy(companions = it)) }
    PluginRow(
        titleRes = R.string.plugin_collision, descRes = R.string.plugin_collision_desc,
        enabled = options.collision) { onChanged(options.copy(collision = it)) }
    PluginRow(
        titleRes = R.string.plugin_divination, descRes = R.string.plugin_divination_desc,
        enabled = options.divination) { onChanged(options.copy(divination = it)) }
}

@Composable
private fun PluginRow(
    titleRes: Int,
    descRes: Int,
    enabled: Boolean,
    onChanged: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(descRes), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = enabled, onCheckedChange = onChanged)
    }
}
