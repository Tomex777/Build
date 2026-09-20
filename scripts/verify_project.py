#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
errors=[]
passes=[]

def check(name, cond, detail=""):
    (passes if cond else errors).append((name, detail))

def text(path):
    return (root/path).read_text(encoding="utf-8")

xml_files=list(root.rglob("*.xml"))
for p in xml_files:
    try:
        ET.parse(p)
    except Exception as e:
        errors.append((f"XML parses: {p.relative_to(root)}", repr(e)))
check("all XML parses", not any(n.startswith("XML parses:") for n,_ in errors), f"{len(xml_files)} files")

layout=text("core/model/src/main/java/com/night/keyboard/model/KeyboardLayoutFactory.kt")
check("123 mode key exists independently of number row", 'KeySpec("numbers", "123", special = SpecialKey.NUMBERS' in layout)
check("symbol pane can return to ABC", 'KeySpec("letters", "ABC", special = SpecialKey.LETTERS' in layout)
check("third symbol pane exists", "moreSymbolRows" in layout and "MORE_SYMBOLS" in layout)

models=text("core/model/src/main/java/com/night/keyboard/model/KeyboardModels.kt")
check("per-key sizing model exists", "widthScale: Float?" in models and "heightDp: Float?" in models)
check("global keyboard density model exists", all(x in models for x in ["keyHeightDp", "horizontalGapDp", "verticalGapDp"]))

ime=text("app/src/main/java/com/night/keyboard/ime/ImeKeyboard.kt")
icons=text("app/src/main/java/com/night/keyboard/ime/KeyboardIcons.kt")
emoji_art=text("app/src/main/java/com/night/keyboard/ime/EmojiArtwork.kt")
repeat_backspace=text("app/src/main/java/com/night/keyboard/ime/RepeatBackspaceKey.kt")
editor=text("app/src/main/java/com/night/keyboard/ui/screens/EditorScreen.kt")
editor_vm=text("app/src/main/java/com/night/keyboard/ui/screens/EditorViewModel.kt")
codec=text("core/data/src/main/java/com/night/keyboard/data/theme/ThemeCodec.kt")
theme_dao=text("core/data/src/main/java/com/night/keyboard/data/theme/ThemeDao.kt")
theme_repo=text("core/data/src/main/java/com/night/keyboard/data/theme/ThemeRepository.kt")
check(
    "spacebar cursor uses long-press drag",
    "awaitEachGesture" in ime
    and "longPressTimeoutMillis" in ime
    and "trackpadActive" in ime
    and "onCursor(direction)" in ime,
)
check(
    "shift first tap is immediate before double-tap caps lock",
    "lastShiftTapAt" in ime
    and "ViewConfiguration.getDoubleTapTimeout()" in ime
    and "onDoubleClick" not in ime,
)
check("backspace has stationary hold-repeat behavior", "RepeatBackspaceKey" in ime and "delay(380)" in repeat_backspace and "delay(55)" in repeat_backspace)
check("haptic preference reaches key paths", "hapticsEnabled" in ime and "hapticsEnabled" in repeat_backspace)
check("autocorrect and undo path exist", "SuggestionEngine.autocorrect" in ime and "undoAutocorrect" in ime)
check("one-handed layout affects IME width", "OneHandedMode.LEFT" in ime and "fillMaxWidth(widthFraction)" in ime)
check("toolbar exposes focused AI trio", all(x in ime for x in ["Editor", "Tone", "Contextual Research"]))
check("toolbar exposes clipboard and emoji", "ToolPanel.CLIPBOARD" in ime and "ToolPanel.EMOJI" in ime)
check("IME uses Keyboard-owned vector family", "KeyboardIcons.Clipboard" in ime and "KeyboardIcons.Backspace" in repeat_backspace and "androidx.compose.material.icons" not in ime)
check("custom icon family uses common optical geometry", "strokeLineWidth = 1.8f" in icons and "viewportWidth = 24f" in icons and "StrokeCap.Round" in icons)
check("SVG icon master exists", (root/"design/icons/keyboard-icons.svg").exists())
check("emoji picker renders Keyboard-owned artwork", "KeyboardEmojiSamples.forEach" in ime and "KeyboardEmojiArtwork(entry.art" in ime)
check("emoji picker does not render its entries through Text", "Text(emoji" not in ime and "Text(entry.output" not in ime)
check("emoji artwork is vector drawn", "Canvas(modifier)" in emoji_art and "EmojiArtKind" in emoji_art)
check("emoji SVG source master exists", (root/"design/emoji/keyboard-emoji-samples.svg").exists())
check("emoji samples retain Unicode commit outputs", "EmojiArtEntry(\"🙂\"" in emoji_art and "controller.commit(entry.output)" in ime)
check("per-key sizing affects real IME", "effectiveWeight" in ime and "style.heightDp ?: theme.keyHeightDp" in ime)
check("editor exposes per-key sizing", "Selected key width" in editor and "Selected key height" in editor)
check("density controls affect editor preview", "Base key height" in editor and "horizontalGapDp" in editor and "verticalGapDp" in editor)
check("theme codec persists sizing", all(x in codec for x in ["widthScale", "heightDp", "keyHeightDp", "horizontalGapDp", "verticalGapDp"]))
check("editor updates working state synchronously", "edits.value = snapshot" in editor_vm and "MutableStateFlow<ThemeSnapshot?>" in editor_vm)
check("editor slider saves are coalesced", "debounce(120)" in editor_vm and "BufferOverflow.DROP_OLDEST" in editor_vm)
check("active theme replacement is transactional", "@Transaction" in theme_dao and "replaceActive" in theme_dao and "dao.replaceActive(entity)" in theme_repo)
check("theme saves are serialized", "Mutex()" in theme_repo and "withLock" in theme_repo)

service=text("app/src/main/java/com/night/keyboard/ime/KeyboardInputMethodService.kt")
online_client=text("app/src/main/java/com/night/keyboard/ime/KeyboardOnlineClient.kt")
prefs=text("core/data/src/main/java/com/night/keyboard/data/prefs/KeyboardPreferences.kt")
check("sensitive fields suppress IME private features", "sensitiveFieldFlow" in service and "privateMode" in ime)
check("incognito suppresses clipboard capture", "incognitoMode" in service and "setIncognito" in prefs)
check("payment and OTP metadata are treated as private", all(x in service for x in ["credit card", "cvv", "otp", "security code"]))
check("online text requires explicit send action", "Nothing is sent until you tap Send." in ime and "KeyboardOnlineClient" in ime)
check("online client requires HTTPS", 'protocol.equals("https"' in online_client)

home=text("app/src/main/java/com/night/keyboard/ui/screens/HomeScreen.kt")
check("setup is conditional on real system state", "if (!setup.complete)" in home)
check("setup checks enabled IME list", "enabledInputMethodList" in home)
check("setup checks selected DEFAULT_INPUT_METHOD", "DEFAULT_INPUT_METHOD" in home)

clipboard=text("app/src/main/java/com/night/keyboard/ui/screens/ClipboardScreen.kt")
clipboard_vm=text("app/src/main/java/com/night/keyboard/ui/screens/ClipboardViewModel.kt")
repo=text("core/data/src/main/java/com/night/keyboard/data/clipboard/ClipboardRepository.kt")
check("clipboard search exists", 'label = { Text("Search clipboard") }' in clipboard)
check("clipboard swipe delete exists", "SwipeToDismissBox" in clipboard)
check("clipboard undo exists", 'actionLabel = "Undo"' in clipboard and "undoDelete" in clipboard)
check("clipboard reorder gesture exists", "detectDragGesturesAfterLongPress" in clipboard and "onMove" in clipboard)
check("clipboard kind filters cover text links numbers and addresses", all(x in clipboard for x in ["ClipboardKind.TEXT", "ClipboardKind.LINK", "ClipboardKind.PHONE", "ClipboardKind.ADDRESS"]))
check("per-item retention exists", "RetentionPreset.entries" in clipboard and "setRetention" in repo)
check("custom retention input is bounded and persisted", "Custom expiry" in clipboard and "525_600L" in clipboard and "setCustomRetention" in repo)
check("batch clear is undoable", "Clear unpinned" in clipboard and "clearUnpinnedForUndo" in clipboard_vm and "undoClear" in clipboard_vm and "clearUnpinnedWithBackup" in repo and "restoreAll" in repo)
check("pin removes expiry", "if (pinned)" in repo and "null" in repo)

all_kt="\n".join(p.read_text(encoding="utf-8") for p in root.rglob("*.kt"))
removed_terms=["streak", "achievement", "vibe mode", "marketplace"]
check("removed product systems stay removed", not any(term in all_kt.lower() for term in removed_terms), ", ".join(t for t in removed_terms if t in all_kt.lower()))

manifest=text("app/src/main/AndroidManifest.xml")
check("InputMethodService permission declared", "android.permission.BIND_INPUT_METHOD" in manifest)
check("IME metadata declared", 'android:name="android.view.im"' in manifest)
check("cleartext traffic disabled", 'android:usesCleartextTraffic="false"' in manifest)

print(f"STATIC CHECKS: {len(passes)} PASS / {len(errors)} FAIL")
for name,detail in passes:
    print(f"PASS  {name}" + (f" — {detail}" if detail else ""))
for name,detail in errors:
    print(f"FAIL  {name}" + (f" — {detail}" if detail else ""))
if errors:
    sys.exit(1)
