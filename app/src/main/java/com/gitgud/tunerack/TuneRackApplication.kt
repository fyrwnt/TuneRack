package com.gitgud.tunerack

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.gitgud.tunerack.data.charts.ChartRepository
import com.gitgud.tunerack.data.download.DownloadRepository
import com.gitgud.tunerack.data.library.LibraryDatabase
import com.gitgud.tunerack.data.library.LibraryRepository
import com.gitgud.tunerack.data.youtube.NewPipeDownloader
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale
import java.util.concurrent.TimeUnit

class TuneRackApplication : Application(), SingletonImageLoader.Factory {

    /** One HTTP client for YouTube requests, audio streams and artwork. */
    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val database by lazy { LibraryDatabase.create(this) }

    /** The local library; Room opens the database lazily, off the main thread. */
    val library: LibraryRepository by lazy { LibraryRepository(database.dao()) }

    val downloads: DownloadRepository by lazy { DownloadRepository(this, database.dao(), httpClient) }

    val charts: ChartRepository by lazy { ChartRepository(this, httpClient, database.dao()) }

    override fun onCreate() {
        super.onCreate()
        val locale = Locale.getDefault()
        NewPipe.init(
            NewPipeDownloader(httpClient),
            Localization.fromLocale(locale),
            ContentCountry(locale.country.ifEmpty { "US" }),
        )
    }

    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { httpClient })) }
            .crossfade(true)
            .build()
}

val Context.tuneRack: TuneRackApplication get() = applicationContext as TuneRackApplication
