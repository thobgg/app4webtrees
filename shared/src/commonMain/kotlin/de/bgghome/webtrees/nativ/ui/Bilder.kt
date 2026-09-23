package de.bgghome.webtrees.nativ.ui

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import de.bgghome.webtrees.nativ.api.WtClient

/** Bilder sind signierte webtrees-Routen und brauchen dieselbe Sitzung (Cookie) wie die API - also deren OkHttp-Client. */
fun wtImageLoader(context: PlatformContext, client: WtClient): ImageLoader =
    ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { client.http })) }
        .crossfade(true)
        .build()
