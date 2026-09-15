package com.night.keyboard.ime

import android.content.ClipboardManager
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.*
import androidx.savedstate.*
import com.night.keyboard.data.clipboard.ClipboardRepository
import com.night.keyboard.data.prefs.KeyboardPreferences
import com.night.keyboard.data.theme.ThemeRepository
import com.night.keyboard.ui.theme.KeyboardTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.*

@AndroidEntryPoint
class KeyboardInputMethodService : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    @Inject lateinit var clipboardRepository: ClipboardRepository
    @Inject lateinit var preferences: KeyboardPreferences
    @Inject lateinit var themeRepository: ThemeRepository
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var clipboard: ClipboardManager
    private var sensitiveField = false
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (sensitiveField) return@OnPrimaryClipChangedListener
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        if (text.isNotBlank()) serviceScope.launch(Dispatchers.IO) { clipboardRepository.capture(text) }
    }

    override fun onCreate() {
        savedStateController.performAttach()
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.addPrimaryClipChangedListener(clipListener)
    }

    override fun onCreateInputView(): View {
        // A ComposeView hosted by InputMethodService is attached beneath the IME
        // dialog's decor tree rather than an Activity. WindowRecomposer resolves
        // lifecycle/saved-state owners from that tree, so installing owners only on
        // the child ComposeView is too late and crashes on first attachment.
        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeViewModelStoreOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }

        val controller = KeyboardController(this)
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@KeyboardInputMethodService)
            setViewTreeViewModelStoreOwner(this@KeyboardInputMethodService)
            setViewTreeSavedStateRegistryOwner(this@KeyboardInputMethodService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            // Android's gestural navigation area is drawn inside the IME window.
            // Keep the bottom keyboard row above that reserved touch region instead
            // of letting the spacebar sit beneath the home gesture pill.
            setOnApplyWindowInsetsListener { view, insets ->
                val navigation = WindowInsetsCompat
                    .toWindowInsetsCompat(insets, view)
                    .getInsets(WindowInsetsCompat.Type.navigationBars())
                view.setPadding(0, 0, 0, navigation.bottom)
                insets
            }

            setContent {
                KeyboardTheme {
                    ImeKeyboard(
                        controller,
                        themeRepository.activeTheme,
                        preferences.state,
                        clipboardRepository.items,
                    )
                }
            }
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        sensitiveField = attribute?.let(::isSensitive) ?: false
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        clipboard.removePrimaryClipChangedListener(clipListener)
        serviceScope.cancel()
        store.clear()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    private fun isSensitive(info: EditorInfo): Boolean {
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        val klass = info.inputType and InputType.TYPE_MASK_CLASS
        return when (klass) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }
}
