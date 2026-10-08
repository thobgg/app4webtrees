package de.bgghome.webtrees.nativ.api

import okhttp3.OkHttpClient

/** Android vertraut von sich aus dem Systemspeicher - nichts zu tun. */
actual fun systemVertrauen(builder: OkHttpClient.Builder) {}
