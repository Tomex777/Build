from pathlib import Path
ROOT=Path('sora-overlay/app/src/main/java/com/night/sora')

def rep(path, old, new):
    p=Path(path); s=p.read_text()
    if old not in s: raise SystemExit(f'missing block in {p}: {old[:100]!r}')
    p.write_text(s.replace(old,new,1))

player=ROOT/'playback/MusicPlaybackController.kt'
rep(player,'''        offlineResolver?.invoke(track)?.let { path ->
            val file = File(path)
            if (file.isFile && file.length() > 0L) { playLocal(track, file); return }
        }
        val extension = extensions.firstOrNull { it.packageName == track.extensionPackage }
''','''        offlineResolver?.invoke(track)?.let { path ->
            val file = File(path)
            if (file.isFile && file.length() > 0L) { playLocal(track, file); return }
        }
        if (!isActiveMusicSource(track)) {
            isLoading = false
            sourceName = ""
            streamLabel = ""
            errorMessage = "This track belongs to a different Music source. Change the selected Music source to play it."
            return
        }
        val extension = extensions.firstOrNull { it.packageName == track.extensionPackage }
''')
rep(player,'''    private fun ExtensionMediaSelection.sameTrack(other: ExtensionMediaSelection): Boolean = identityKey() == other.identityKey()
''','''    private fun isActiveMusicSource(track: ExtensionMediaSelection): Boolean {
        val selected = appContext.getSharedPreferences("sora_preferred_sources_v1", Context.MODE_PRIVATE)
            .getString("preferred_music", null)
            ?: return true
        return selected == "${track.extensionPackage}|${track.sourceId}"
    }

    private fun ExtensionMediaSelection.sameTrack(other: ExtensionMediaSelection): Boolean = identityKey() == other.identityKey()
''')

dm=ROOT/'download/MusicDownloadManager.kt'
rep(dm,'''    fun updateExtensions(value: List<InstalledExtension>) { extensions = value }
    fun entryFor(track: ExtensionMediaSelection) = repository.downloads.firstOrNull { it.id == id(track) }
''','''    fun updateExtensions(value: List<InstalledExtension>) { extensions = value }
    fun canUseSource(track: ExtensionMediaSelection): Boolean {
        val selected = app.getSharedPreferences("sora_preferred_sources_v1", Context.MODE_PRIVATE)
            .getString("preferred_music", null)
            ?: return true
        return selected == "${track.extensionPackage}|${track.sourceId}"
    }
    fun entryFor(track: ExtensionMediaSelection) = repository.downloads.firstOrNull { it.id == id(track) }
''')
rep(dm,'''    fun download(track: ExtensionMediaSelection) {
        if (track.type != ContentType.MUSIC) return
''','''    fun download(track: ExtensionMediaSelection) {
        if (track.type != ContentType.MUSIC || !canUseSource(track)) return
''')

now=ROOT/'ui/screens/NowPlayingScreen.kt'
rep(now,'''    downloadEntry: (ExtensionMediaSelection) -> DownloadEntry?,
    onDownload: (ExtensionMediaSelection) -> Unit,
''','''    downloadEntry: (ExtensionMediaSelection) -> DownloadEntry?,
    downloadAllowed: (ExtensionMediaSelection) -> Boolean,
    onDownload: (ExtensionMediaSelection) -> Unit,
''')
rep(now,'''    val download = downloadEntry(track)
    val downloaded = download?.status == DownloadStatus.COMPLETED && !download.filePath.isNullOrBlank()
    val downloadBusy = download?.status == DownloadStatus.DOWNLOADING || download?.status == DownloadStatus.QUEUED
''','''    val download = downloadEntry(track)
    val downloaded = download?.status == DownloadStatus.COMPLETED && !download.filePath.isNullOrBlank()
    val downloadBusy = download?.status == DownloadStatus.DOWNLOADING || download?.status == DownloadStatus.QUEUED
    val sourceAllowsDownload = downloaded || downloadAllowed(track)
''')
rep(now,'''                        text = { Text(if (downloaded) "Remove download" else if (downloadBusy) "Downloading" else if (download?.status == DownloadStatus.FAILED) "Retry download" else "Download") },
                        leadingIcon = { Icon(if (downloaded) Icons.Rounded.DeleteOutline else Icons.Rounded.Download, null) },
                        enabled = !downloadBusy,
''','''                        text = { Text(if (downloaded) "Remove download" else if (!sourceAllowsDownload) "Switch Music source to download" else if (downloadBusy) "Downloading" else if (download?.status == DownloadStatus.FAILED) "Retry download" else "Download") },
                        leadingIcon = { Icon(if (downloaded) Icons.Rounded.DeleteOutline else Icons.Rounded.Download, null) },
                        enabled = !downloadBusy && sourceAllowsDownload,
''')
rep(now,'''                    downloaded -> "Downloaded"
                    downloadBusy -> "Downloading"
                    download?.status == DownloadStatus.FAILED -> "Retry"
                    else -> "Download"
                },
                enabled = !downloadBusy,
''','''                    downloaded -> "Downloaded"
                    !sourceAllowsDownload -> "Switch source"
                    downloadBusy -> "Downloading"
                    download?.status == DownloadStatus.FAILED -> "Retry"
                    else -> "Download"
                },
                enabled = !downloadBusy && sourceAllowsDownload,
''')

app=ROOT/'ui/SoraApp.kt'
rep(app,'''                downloadEntry = musicDownloads::entryFor,
                onDownload = musicDownloads::download,
''','''                downloadEntry = musicDownloads::entryFor,
                downloadAllowed = musicDownloads::canUseSource,
                onDownload = musicDownloads::download,
''')
