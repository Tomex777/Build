package com.night.spotui.source.api

object MusicSourceContract {
    const val API_VERSION = 1

    const val ACTION_BIND_SOURCE = "com.night.spotui.source.BIND"

    const val META_SOURCE_ID = "spotui.source.id"
    const val META_SOURCE_NAME = "spotui.source.name"
    const val META_API_VERSION = "spotui.source.apiVersion"

    const val MSG_REQUEST = 2001
    const val MSG_RESPONSE = 2002

    const val KEY_REQUEST_ID = "requestId"
    const val KEY_METHOD = "method"
    const val KEY_PAYLOAD_JSON = "payloadJson"
    const val KEY_OK = "ok"
    const val KEY_RESULT_JSON = "resultJson"
    const val KEY_ERROR = "error"

    object Method {
        const val MANIFEST = "manifest"
        const val BROWSE = "browse"
        const val SEARCH = "search"
        const val STREAMS = "streams"
        const val BROWSER_SESSION = "browserSession"
        const val STORE_SESSION = "storeSession"
    }
}
