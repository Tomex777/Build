from pathlib import Path

path = Path("sora-overlay/app/src/main/java/com/night/sora/ui/screens/MediaDetailScreen.kt")
text = path.read_text()


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    text = text.replace(old, new, 1)

replace_once(
    "import com.night.sora.extension.isCatalogProvider\n",
    "import com.night.sora.extension.isCatalogProvider\nimport com.night.sora.extension.isDiagnosticProvider\n",
    "diagnostic import",
)

replace_once(
    "        if (ext.error != null) emptyList()\n        else ext.descriptor?.sources.orEmpty()\n",
    "        if (ext.error != null || ext.isDiagnosticProvider()) emptyList()\n        else ext.descriptor?.sources.orEmpty()\n",
    "exclude diagnostics from source options",
)

replace_once(
    "    val ext = extensions.firstOrNull { it.packageName == selection.extensionPackage } ?: return false\n    val source = ext.descriptor?.sources?.firstOrNull { it.id == selection.sourceId } ?: return !ext.isCatalogProvider()\n",
    "    val ext = extensions.firstOrNull { it.packageName == selection.extensionPackage } ?: return false\n    if (ext.isDiagnosticProvider()) return false\n    val source = ext.descriptor?.sources?.firstOrNull { it.id == selection.sourceId } ?: return !ext.isCatalogProvider()\n",
    "reject diagnostic saved selections",
)

path.write_text(text)
print("Diagnostic source filter applied to", path)
