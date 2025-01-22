package com.beakoninc.locusnotes.data.location


import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Job

@Singleton
class LocationService @Inject constructor(
    @ApplicationContext private val context: Context,
){
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private var searchJob: Job? = null
}
