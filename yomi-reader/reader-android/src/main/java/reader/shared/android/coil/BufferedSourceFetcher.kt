/*
 * Adapted from Mihon v0.19.9, commit 7a917968e3bf71c4a665e6655a550877d81ead1d.
 * Copyright (C) Mihon Open Source Project contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package reader.shared.android.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.BufferedSource

class BufferedSourceFetcher(
    private val data: BufferedSource,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult = SourceFetchResult(
        source = ImageSource(source = data, fileSystem = options.fileSystem),
        mimeType = null,
        dataSource = DataSource.MEMORY,
    )

    class Factory : Fetcher.Factory<BufferedSource> {
        override fun create(data: BufferedSource, options: Options, imageLoader: ImageLoader): Fetcher =
            BufferedSourceFetcher(data, options)
    }
}
