from pathlib import Path

path = Path('sora-overlay/app/src/main/java/com/night/sora/ui/SoraApp.kt')
text = path.read_text()
old = '    DisposableEffect(musicPlayer) { onDispose { musicPlayer.release() } }\n'
if old in text:
    text = text.replace(old, '', 1)
elif 'musicPlayer.release()' in text:
    raise SystemExit('Unexpected musicPlayer.release() shape; refusing blind edit')
path.write_text(text)
print('Sora activity no longer releases the process-wide music runtime.')
