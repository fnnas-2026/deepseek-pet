package com.ferry.pet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ferry.pet.BuildConfig
import com.ferry.pet.account.Account
import com.ferry.pet.PetRuntime
import com.ferry.pet.R
import com.ferry.pet.RuntimeState
import kotlinx.coroutines.launch

/** 关于与诊断：版本、运行状态与关键性能计数，便于排查卡顿/素材/模块问题。 */
@Composable
fun AboutSection(runtime: RuntimeState, onOpenAgreement: (AgreementType) -> Unit = {}) {
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

    AccountQuotaCard()

    AgreementEntries(onOpen = onOpenAgreement)
}

/**
 * 第三方账号 / 额度卡片：本地会话态绑定当前渠道，可查询支持公开 Bearer 接口平台的额度，
 * 不内置任何第三方测试网关，令牌仅保存在本机。
 */
@Composable
private fun AccountQuotaCard() {
    val scope = rememberCoroutineScope()
    var loggedIn by remember { mutableStateOf(Account.instance.isLoggedIn()) }
    var displayName by remember { mutableStateOf(Account.instance.displayName()) }
    var quotaLabel by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var messageError by remember { mutableStateOf(false) }
    val loginOkText = stringResource(R.string.account_login_ok)

    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.account_entry),
                    style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (loggedIn) displayName.ifBlank { stringResource(R.string.account_in) }
                    else stringResource(R.string.account_not_bound),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }

            when {
                busy -> CircularProgressIndicator(
                    modifier = Modifier.padding(horizontal = 8.dp), strokeWidth = 2.dp)
                loggedIn -> Row {
                    TextButton(onClick = {
                        scope.launch {
                            busy = true; message = ""; messageError = false
                            when (val r = Account.instance.quota()) {
                                is com.ferry.pet.account.QuotaResult.Available -> {
                                    quotaLabel = r.label
                                    message = ""
                                }
                                is com.ferry.pet.account.QuotaResult.Unavailable -> {
                                    quotaLabel = ""; message = r.message; messageError = true
                                }
                            }
                            busy = false
                        }
                    }) { Text(stringResource(R.string.account_quota_refresh)) }
                    TextButton(onClick = {
                        Account.instance.logout()
                        loggedIn = false; displayName = ""; quotaLabel = ""; message = ""
                    }) { Text(stringResource(R.string.account_logout)) }
                }
                else -> TextButton(onClick = {
                    scope.launch {
                        busy = true; message = ""; messageError = false
                        when (val r = Account.instance.login()) {
                            is com.ferry.pet.account.AccountResult.Success -> {
                                loggedIn = true; displayName = r.displayName
                                message = loginOkText
                            }
                            is com.ferry.pet.account.AccountResult.Failure -> {
                                message = r.message; messageError = true
                            }
                        }
                        busy = false
                    }
                }) { Text(stringResource(R.string.account_login)) }
            }
        }

        if (quotaLabel.isNotBlank()) {
            Text(quotaLabel, color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp))
        }
        if (message.isNotBlank()) {
            Text(message,
                color = if (messageError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp))
        }
        if (!loggedIn) {
            Text(stringResource(R.string.account_quota_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, color = MaterialTheme.colorScheme.onSurface)
    }
}
