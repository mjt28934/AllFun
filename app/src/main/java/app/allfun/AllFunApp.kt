package app.allfun

import android.app.Application
import app.allfun.data.AppContainer
import app.allfun.data.net.ImageHeaderInterceptor
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder

class AllFunApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.bootstrap()
    }

    /**
     * Coil loads every cover, page and thumbnail in the app. Extension sources need their
     * own referer/agent headers on image hosts, which [ImageHeaderInterceptor] replays from
     * the registry populated while a source produced its results.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(SvgDecoder.Factory()) }
        .okHttpClient {
            container.http.client.newBuilder()
                .addInterceptor(ImageHeaderInterceptor())
                .build()
        }
        .crossfade(true)
        .build()
}
