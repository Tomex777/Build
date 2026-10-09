#!/usr/bin/env python3
"""Offline JVM check of generated types using real operation schemas, no Android SDK.

Requires local JDK, kotlinc, and a locally available org.json jar (no downloads).
The Android capability implementation is stubbed; definition declarations are real.
"""
from pathlib import Path
import os, subprocess, sys, tempfile
ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT/'app/src/main/java/com/tomex777/annie'

BACKEND = '''package com.tomex777.annie
import java.io.File
import org.json.JSONObject
internal interface AndroidCapabilityBackend {
    suspend fun speak(ownerPackageId: String, text: String, languageTag: String?, queueMode: String): JSONObject
    suspend fun ttsStatus(ownerPackageId: String, utteranceId: String): JSONObject
    suspend fun stopSpeech(ownerPackageId: String): JSONObject
    suspend fun recognizeText(imageFile: File): JSONObject
    suspend fun listen(languageTag: String?, prompt: String?): JSONObject
    suspend fun pickTextDocument(mimeType: String): JSONObject
    suspend fun inspectMedia(mediaFile: File): JSONObject
    suspend fun postNotification(ownerPackageId: String, key: String?, title: String, text: String): JSONObject
    suspend fun updateNotification(ownerPackageId: String, key: String, title: String, text: String): JSONObject
    suspend fun cancelNotification(ownerPackageId: String, key: String): JSONObject
}
'''
CONSTS = '''package com.tomex777.annie
internal const val MAX_ANDROID_BRIDGE_INPUT_BYTES = 16384
internal const val MAX_ANDROID_BRIDGE_OUTPUT_BYTES = 65536
internal const val ANDROID_DEVICE_INFO_CAPABILITY = "android.device.info"
internal const val ANDROID_DEVICE_INFO_PERMISSION = "android.device.info"
internal const val ANDROID_TTS_CAPABILITY = "android.tts"
internal const val ANDROID_TTS_PERMISSION = "android.tts.speak"
internal const val ANDROID_TTS_CONTROL_PERMISSION = "android.tts.control"
internal const val ANDROID_OCR_CAPABILITY = "android.ocr"
internal const val ANDROID_OCR_PERMISSION = "android.ocr.recognize"
internal const val ANDROID_STT_CAPABILITY = "android.stt"
internal const val ANDROID_STT_PERMISSION = "android.stt.listen"
internal const val ANDROID_DOCUMENTS_CAPABILITY = "android.documents"
internal const val ANDROID_DOCUMENTS_PERMISSION = "android.documents.pick"
internal const val ANDROID_MEDIA_CAPABILITY = "android.media"
internal const val ANDROID_MEDIA_PERMISSION = "android.media.inspect"
internal const val ANDROID_NOTIFICATIONS_CAPABILITY = "android.notifications"
internal const val ANDROID_NOTIFICATIONS_PERMISSION = "android.notifications.post"
internal const val ANDROID_NOTIFICATIONS_MANAGE_PERMISSION = "android.notifications.manage"
internal const val DOWNLOADS_CAPABILITY = "downloads"
internal const val NETWORK_ACCESS_CAPABILITY = "network"
internal const val NETWORK_ACCESS_PERMISSION = "network.access"
internal const val DOWNLOADS_START_PERMISSION = "downloads.start"
internal const val DOWNLOADS_CONTROL_PERMISSION = "downloads.control"
'''

def static_definition(file: str, start: str, end: str) -> str:
    content = (MAIN / file).read_text()
    startidx = content.index(start)
    endidx = content.index(end, startidx)
    return content[startidx:endidx]

def main():
    if len(sys.argv)!=2:
        print('usage: check-annie-generated-jvm.py /path/to/org.json.jar', file=sys.stderr)
        return 2
    jar = Path(sys.argv[1]).resolve()
    if not jar.is_file():
        print('MISSING org.json jar', file=sys.stderr)
        return 2
    with tempfile.TemporaryDirectory(prefix='annie-jvm-') as dir:
        temp = Path(dir)
        def file(name,contents):
            p=temp/name; p.parent.mkdir(parents=True,exist_ok=True); p.write_text(contents); return str(p)
        backend=file('Backend.kt',BACKEND)
        constants=file('Constants.kt',CONSTS)
        build=file('android/os/Build.kt','package android.os\nobject Build { object VERSION { const val SDK_INT = 36 } }\n')
        download=file('DownloadDefinitions.kt', 'package com.tomex777.annie\n'+ static_definition('ScriptDownloads.kt','internal const val DOWNLOADS_PROVIDER_ID', 'internal class PackageDownloadOperationProvider'))
        message=file('MessageDefinitions.kt','package com.tomex777.annie\ninternal const val MESSAGES_PROVIDER_ID = \"messages\"\ninternal const val MESSAGES_CAPABILITY = \"messages\"\ninternal const val MESSAGES_POST_PERMISSION = \"messages.post\"\ninternal const val MAX_MESSAGE_BYTES = 49152\n'+static_definition('ScriptMessages.kt','internal fun messageOperationDefinitions()', 'internal class ScriptMessageOperationProvider'))
        sources=[str(MAIN/s) for s in ('OperationRegistry.kt','OperationTypings.kt','AndroidOperationProvider.kt','ApiCompatibility.kt')]
        sources+= [backend,constants,build,download,message,str(ROOT/'scripts/tests/GenerateTypingsMain.kt')]
        out=str(temp/'build')
        command=['kotlinc','-classpath',str(jar),'-d',out,*sources]
        print('Compiling Android-free operation definitions with real JDK/Kotlin... ', flush=True)
        compile=subprocess.run(command, capture_output=True, text=True)
        if compile.returncode:
            print(compile.stdout,compile.stderr,file=sys.stderr)
            return compile.returncode
        result=subprocess.run(['kotlin','-classpath', f'{out}{os.pathsep}{jar}', 'com.tomex777.annie.GenerateTypingsMainKt',str(ROOT/'docs/generated/annie.generated.d.ts')],capture_output=True,text=True)
        print(result.stdout,result.stderr,sep='',end='')
        return result.returncode

if __name__ == '__main__': raise SystemExit(main())
