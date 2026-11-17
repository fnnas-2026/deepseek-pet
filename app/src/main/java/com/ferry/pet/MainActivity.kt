package com.ferry.pet

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ferry.pet.security.IntegrityGuard
import com.ferry.pet.ui.SettingsNav

class MainActivity : ComponentActivity() {
    private lateinit var preferences: PetPreferences
    private var overlayAllowed by mutableStateOf(false)
    private var options by mutableStateOf(PetOptions())
    private var chatOptions by mutableStateOf(ChatOptions())
    private var audioName by mutableStateOf<String?>(null)
    private var builtinCount by mutableStateOf(0)
    private var preview: ClickAudio? = null
    private var previewState by mutableStateOf(ClickAudio.State.IDLE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        preferences = PetPreferences(this)
        if (!BuildConfig.DEBUG && IntegrityGuard.suspectTampers(this)) finish()
        options = preferences.options()
        chatOptions = preferences.chatOptions()
        audioName = resolveAudioName(options.clickSoundUri)
        builtinCount = runCatching { AssetCatalog.load(assets).phrases.size }.getOrDefault(0)
        setContent { AppTheme() }
    }

    override fun onResume() {
        super.onResume()
        overlayAllowed = Settings.canDrawOverlays(this)
        if (::preferences.isInitialized) {
            options = preferences.options()
            chatOptions = preferences.chatOptions()
            audioName = resolveAudioName(options.clickSoundUri)
        }
    }

    override fun onStop() {
        preview?.close()
        preview = null
        super.onStop()
    }

    @androidx.compose.runtime.Composable
    private fun AppTheme() {
        val runtime by PetRuntime.state.collectAsState()
        val audioError by PetRuntime.audioError.collectAsState()
        val context = LocalContext.current
        val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                save(options.copy(clickSoundUri = uri.toString()))
                audioName = resolveAudioName(uri.toString())
            }
        }
        // 深蓝底 + 暖橙主色。
        val scheme = darkColorScheme(
            primary = Color(0xFFE8A87C), onPrimary = Color(0xFF3A1E0E),
            background = Color(0xFF0E2233), surface = Color(0xFF143044),
            onBackground = Color(0xFFEDE7DF), onSurface = Color(0xFFEDE7DF),
            secondaryContainer = Color(0xFF1E3E53), onSecondaryContainer = Color(0xFFF3C9A6),
            surfaceVariant = Color(0xFF1E3E53), outlineVariant = Color(0xFF3E5C6E),
            tertiary = Color(0xFF76D5BD))
        MaterialTheme(colorScheme = scheme) {
            Surface(modifier = Modifier.fillMaxSize(), color = scheme.background) {
                SettingsNav(
                    options = options,
                    chatOptions = chatOptions,
                    overlayAllowed = overlayAllowed,
                    runtime = runtime,
                    audioName = audioName,
                    builtinCount = builtinCount,
                    previewState = previewState,
                    audioError = audioError,
                    onSave = { save(it) },
                    onSaveChat = { saveChat(it) },
                    onPreviewSound = ::previewSound,
                    onPickAudio = { audioPicker.launch(arrayOf("audio/*")) },
                    onReset = ::resetAll,
                )
            }
        }
    }

    private fun previewSound() {
        preview?.close()
        preview = ClickAudio({ uri, changed -> AndroidClickOutput(this, uri, changed) },
            SystemClock::elapsedRealtime, { previewState = it }).also {
            it.configure(options.clickSoundUri, options.clickSoundVolume)
            it.press()
        }
    }

    private fun resolveAudioName(uri: String?): String? {
        if (uri == null) return null
        return runCatching {
            contentResolver.query(Uri.parse(uri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
                }
        }.getOrNull()
    }

    /** One writer for preferences: the editor state and the store never diverge. */
    private fun save(next: PetOptions) {
        if (!next.clickSound || next.clickSoundUri != options.clickSoundUri) {
            preview?.close(); preview = null
        } else preview?.configure(next.clickSoundUri, next.clickSoundVolume)
        options = next
        preferences.save(next)
    }

    private fun saveChat(next: ChatOptions) {
        chatOptions = next
        preferences.saveChat(next)
    }

    private fun resetAll() {
        preferences.reset()
        options = preferences.options()
        chatOptions = preferences.chatOptions()
        preview?.close(); preview = null
        audioName = null
        if (PetRuntime.state.value.running) PetOverlayService.command(this, PetOverlayService.RESET)
    }
}
