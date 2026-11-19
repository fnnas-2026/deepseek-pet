package com.ferry.pet.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ferry.pet.BuildConfig
import com.ferry.pet.ChatOptions
import com.ferry.pet.ClickAudio
import com.ferry.pet.PetOptions
import com.ferry.pet.PetOverlayService
import com.ferry.pet.PetPreferences
import com.ferry.pet.PetRuntime
import com.ferry.pet.R
import com.ferry.pet.phone.AccessibilityBridge
import com.ferry.pet.RuntimeState
import com.ferry.pet.update.Changelog
import com.ferry.pet.update.UpdateChecker
import com.ferry.pet.update.Updater
import kotlinx.coroutines.launch

/** 二级菜单页面标识。 */
enum class SettingsPage { HOME, APPEARANCE, INTERACTION, PLUGINS, CHAT, PERSONA, CHANNEL, MESSAGE, PERMISSION, UPDATE, AGREEMENT }

/**
 * 设置主页 + 二级菜单导航。主页只展示分组入口卡片，各分组内容进入对应二级页面，
 * 避免所有配置堆在一个长页面。
 */
@Composable
fun SettingsNav(
    options: PetOptions,
    chatOptions: ChatOptions,
    overlayAllowed: Boolean,
    runtime: RuntimeState,
    audioName: String?,
    builtinCount: Int,
    previewState: ClickAudio.State,
    audioError: Boolean,
    onSave: (PetOptions) -> Unit,
    onSaveChat: (ChatOptions) -> Unit,
    onPreviewSound: () -> Unit,
    onPickAudio: () -> Unit,
    onReset: () -> Unit,
) {
    var page by remember { mutableStateOf(SettingsPage.HOME) }
    var agreementType by remember { mutableStateOf(AgreementType.USER) }
    val context = LocalContext.current
    val preferences = remember { PetPreferences(context) }

    when (page) {
        SettingsPage.HOME -> HomePage(
            runtime = runtime,
            overlayAllowed = overlayAllowed,
            onStartStop = {
                if (runtime.running) PetOverlayService.command(context,
                    if (runtime.paused) PetOverlayService.RESUME else PetOverlayService.PAUSE)
                else PetOverlayService.command(context, PetOverlayService.START)
            },
            onStop = { PetOverlayService.command(context, PetOverlayService.STOP) },
            onOpen = { page = it },
        )
        SettingsPage.APPEARANCE -> SubPage(titleRes = R.string.nav_appearance, onBack = { page = SettingsPage.HOME }) {
            AppearanceSection(options) { onSave(it) }
        }
        SettingsPage.INTERACTION -> SubPage(titleRes = R.string.nav_interaction, onBack = { page = SettingsPage.HOME }) {
            WanderSection(options) { onSave(it) }
            SpeechSection(options, builtinCount) { onSave(it) }
            ClickSoundSection(options, audioName, { onSave(it) },
                onPick = onPickAudio, onPreview = onPreviewSound,
                loading = previewState == ClickAudio.State.LOADING,
                failed = audioError || previewState == ClickAudio.State.ERROR)
            CompanionSection(options, runtime.companions) { onSave(it) }
            CollisionSection(options) { onSave(it) }
            DivinationSection(options) { onSave(it) }
        }
        SettingsPage.PLUGINS -> SubPage(titleRes = R.string.nav_plugins, onBack = { page = SettingsPage.HOME }) {
            PluginSection(options) { onSave(it) }
        }
        SettingsPage.CHAT -> SubPage(titleRes = R.string.nav_chat, onBack = { page = SettingsPage.HOME }) {
            MenuCard(R.string.nav_persona, R.string.nav_persona_desc) { page = SettingsPage.PERSONA }
            MenuCard(R.string.nav_channel, R.string.nav_channel_desc) { page = SettingsPage.CHANNEL }
            MenuCard(R.string.nav_message, R.string.nav_message_desc) { page = SettingsPage.MESSAGE }
        }
        SettingsPage.PERSONA -> SubPage(titleRes = R.string.nav_persona, onBack = { page = SettingsPage.CHAT }) {
            PersonaSection(chatOptions) { onSaveChat(it) }
        }
        SettingsPage.CHANNEL -> SubPage(titleRes = R.string.nav_channel, onBack = { page = SettingsPage.CHAT }) {
            ChannelSection(chatOptions) { onSaveChat(it) }
        }
        SettingsPage.MESSAGE -> SubPage(titleRes = R.string.nav_message, onBack = { page = SettingsPage.CHAT }) {
            MessageSection(preferences = preferences)
        }
        SettingsPage.PERMISSION -> SubPage(titleRes = R.string.nav_permission, onBack = { page = SettingsPage.HOME }) {
            PermissionPage(overlayAllowed = overlayAllowed)
        }
        SettingsPage.UPDATE -> SubPage(titleRes = R.string.nav_update, onBack = { page = SettingsPage.HOME }) {
            UpdatePage(preferences = preferences, onReset = onReset,
                onResetEndpoint = { preferences.resetUpdateEndpoint() })
            AboutSection(runtime = runtime) { agreementType = it; page = SettingsPage.AGREEMENT }
        }
        SettingsPage.AGREEMENT -> SubPage(
            titleRes = when (agreementType) {
                AgreementType.USER -> R.string.agreement_user
                AgreementType.PRIVACY -> R.string.agreement_privacy
                AgreementType.SECURITY -> R.string.agreement_security
            },
            onBack = { page = SettingsPage.UPDATE }) {
            AgreementPage(agreementType)
        }
    }
}

@Composable
private fun HomePage(
    runtime: RuntimeState,
    overlayAllowed: Boolean,
    onStartStop: () -> Unit,
    onStop: () -> Unit,
    onOpen: (SettingsPage) -> Unit,
) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            androidx.compose.foundation.Image(
                painterResource(R.drawable.pet_icon), null, Modifier.size(84.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(statusText(runtime)),
                    color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        runtime.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onStop, enabled = runtime.running, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.stop))
            }
            androidx.compose.material3.Button(onClick = onStartStop, enabled = overlayAllowed,
                modifier = Modifier.weight(1f)) {
                Text(stringResource(if (!runtime.running) R.string.start else
                    if (runtime.paused) R.string.resume else R.string.pause))
            }
        }

        GroupLabel(stringResource(R.string.group_pet))
        MenuCard(R.string.nav_appearance, R.string.nav_appearance_desc, R.drawable.ic_appearance) { onOpen(SettingsPage.APPEARANCE) }
        MenuCard(R.string.nav_interaction, R.string.nav_interaction_desc, R.drawable.ic_interaction) { onOpen(SettingsPage.INTERACTION) }
        MenuCard(R.string.nav_plugins, R.string.nav_plugins_desc, R.drawable.ic_plugins) { onOpen(SettingsPage.PLUGINS) }

        GroupLabel(stringResource(R.string.group_smart))
        MenuCard(R.string.nav_chat, R.string.nav_chat_desc, R.drawable.ic_chat) { onOpen(SettingsPage.CHAT) }

        GroupLabel(stringResource(R.string.group_system))
        MenuCard(R.string.nav_permission, R.string.nav_permission_desc, R.drawable.ic_permission) { onOpen(SettingsPage.PERMISSION) }
        MenuCard(R.string.nav_update, R.string.nav_update_desc, R.drawable.ic_update) { onOpen(SettingsPage.UPDATE) }

        Text(stringResource(R.string.version, BuildConfig.VERSION_NAME),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp))
}

@Composable
private fun MenuCard(
    titleRes: Int,
    descRes: Int,
    iconRes: Int? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (iconRes != null) {
            androidx.compose.material3.Icon(
                painter = painterResource(iconRes), contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(descRes), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun SubPage(titleRes: Int, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
            }
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
            .padding(androidx.compose.foundation.layout.PaddingValues(
                start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            content()
        }
    }
}

@Composable
private fun PermissionPage(overlayAllowed: Boolean) {
    val context = LocalContext.current
    val notificationGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { }

    PermissionRow(
        title = stringResource(R.string.permission_overlay),
        granted = overlayAllowed,
        onJump = {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")))
        })
    PermissionRow(
        title = stringResource(R.string.permission_notification),
        granted = notificationGranted,
        onJump = {
            if (android.os.Build.VERSION.SDK_INT >= 33)
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        })
    PermissionRow(
        title = stringResource(R.string.permission_accessibility),
        granted = AccessibilityBridge.isEnabled(context),
        onJump = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
}

@Composable
private fun PermissionRow(title: String, granted: Boolean, onJump: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(if (granted) R.string.permission_granted else R.string.permission_denied),
                color = if (granted) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onJump) { Text(stringResource(R.string.permission_jump)) }
    }
}

@Composable
private fun UpdatePage(
    preferences: PetPreferences,
    onReset: () -> Unit,
    onResetEndpoint: () -> Unit,
) {
    val context = LocalContext.current
    var updateBusy by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf("") }
    var newerRelease by remember { mutableStateOf<UpdateChecker.Release?>(null) }
    var downloadPercent by remember { mutableStateOf(-1) }
    var changelogOpen by remember { mutableStateOf(false) }
    var endpoint by remember { mutableStateOf(preferences.updateEndpoint()) }
    val scope = rememberCoroutineScope()

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = {
            endpoint = preferences.updateEndpoint()
            updateBusy = true; updateMessage = ""; newerRelease = null; downloadPercent = -1
            scope.launch {
                when (val result = UpdateChecker.check(endpoint, BuildConfig.VERSION_CODE)) {
                    is UpdateChecker.Result.UpToDate -> updateMessage = context.getString(R.string.update_up_to_date)
                    is UpdateChecker.Result.Newer -> { newerRelease = result.release
                        updateMessage = context.getString(R.string.update_found, result.release.versionName) }
                    is UpdateChecker.Result.Failure -> updateMessage = result.message
                }
                updateBusy = false
            }
        }) { Text(stringResource(R.string.check_update)) }
        TextButton(onClick = { changelogOpen = true }) { Text(stringResource(R.string.changelog)) }
    }
    Text(stringResource(R.string.update_endpoint_hint), color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp))
    if (updateBusy) CircularProgressIndicator(modifier = Modifier.padding(8.dp), strokeWidth = 2.dp)
    if (updateMessage.isNotBlank()) Text(updateMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (downloadPercent in 0..100) Text(context.getString(R.string.update_downloading, downloadPercent),
        color = MaterialTheme.colorScheme.onSurfaceVariant)

    HorizontalDivider()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = {
            onResetEndpoint()
            endpoint = preferences.updateEndpoint()
            updateMessage = ""
        }) { Text(stringResource(R.string.reset)) }
        TextButton(onClick = onReset) { Text("恢复全部默认") }
    }
    Text(stringResource(R.string.version, BuildConfig.VERSION_NAME),
        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)

    newerRelease?.let { release ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { newerRelease = null },
            title = { Text(context.getString(R.string.update_found_title, release.versionName)) },
            text = { Text(release.changelog.ifBlank { context.getString(R.string.update_no_notes) }) },
            confirmButton = {
                TextButton(onClick = {
                    updateBusy = true; downloadPercent = 0
                    scope.launch {
                        when (val d = Updater.download(context, release.apkUrls, release.sha256, release.size) { downloadPercent = it }) {
                            is Updater.Download.Done -> { Updater.install(context, d.file); updateBusy = false; downloadPercent = -1; newerRelease = null }
                            is Updater.Download.Failure -> { updateMessage = d.message; updateBusy = false; downloadPercent = -1 }
                            else -> {}
                        }
                    }
                }) { Text(stringResource(R.string.update_download)) }
            },
            dismissButton = { TextButton(onClick = { newerRelease = null }) { Text(stringResource(R.string.cancel)) } })
    }

    if (changelogOpen) androidx.compose.material3.AlertDialog(
        onDismissRequest = { changelogOpen = false },
        title = { Text(stringResource(R.string.changelog)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Changelog.entries.forEach { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${entry.version}　${entry.date}",
                            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        entry.notes.forEach { note -> Text("· $note", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { changelogOpen = false }) { Text(stringResource(R.string.cancel)) } })
}

private fun statusText(runtime: RuntimeState): Int = when {
    !runtime.running -> R.string.not_running
    runtime.paused -> R.string.paused
    else -> R.string.running
}

