package com.ferry.pet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ferry.pet.BuildConfig
import com.ferry.pet.PetRuntime
import com.ferry.pet.R
import com.ferry.pet.RuntimeState

/** 关于与诊断：版本、运行状态与关键性能计数，便于排查卡顿/素材/模块问题。 */
@Composable
fun AboutSection(runtime: RuntimeState) {
    SectionTitle(R.string.nav_about)

    DiagRow(stringResource(R.string.version_label), BuildConfig.VERSION_NAME)
    DiagRow(stringResource(R.string.diag_running),
        when {
            !runtime.running -> stringResource(R.string.diag_idle)
            runtime.paused -> stringResource(R.string.paused)
            else -> stringResource(R.string.running)
        })
    DiagRow(stringResource(R.string.diag_pets), runtime.companions.plus(if (runtime.running) 1 else 0).toString())
    DiagRow(stringResource(R.string.diag_windows), PetRuntime.windowCount.get().toString())
    DiagRow(stringResource(R.string.diag_draws), PetRuntime.draws.get().toString())

    val modules = runtime.modules.ifEmpty { listOf(stringResource(R.string.diag_idle)) }
    DiagRow(stringResource(R.string.diag_modules), modules.joinToString("、"))

    runtime.error?.let { Text(it, color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, color = MaterialTheme.colorScheme.onSurface)
    }
}
