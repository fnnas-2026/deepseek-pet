package com.ferry.pet.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.ferry.pet.PetOptions
import com.ferry.pet.R

/** Size is committed when the finger leaves the slider (commit_policy: on_finish). */
@Composable
fun AppearanceSection(options: PetOptions, onChanged: (PetOptions) -> Unit) {
    var draft by remember(options.size) { mutableStateOf(options.size) }
    SettingSlider(label = R.string.pet_size,
        valueText = "${(draft * 100).toInt()}%",
        value = draft,
        range = .6f..1.6f,
        changed = { draft = it },
        onFinished = { onChanged(options.copy(size = draft)) })

    var speed by remember(options.animSpeed) { mutableStateOf(options.animSpeed) }
    SettingSlider(label = R.string.anim_speed,
        valueText = String.format("%.1f×", speed),
        value = speed,
        range = .5f..2f,
        changed = { speed = it },
        onFinished = { onChanged(options.copy(animSpeed = speed)) })
    Text(stringResource(R.string.anim_speed_hint),
        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        style = androidx.compose.ui.text.TextStyle(fontSize = 12f.sp))
}
