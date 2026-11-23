package com.cooking.app.repositories

import android.content.Context
import com.cooking.app.BuildConfig
import com.cooking.app.models.Filters
import com.cooking.app.network.CookingApi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class CookingRepository(context: Context) {
    private val preferences = context.getSharedPreferences("bep_nha_session", Context.MODE_PRIVATE)

    var token: String?
        get() = preferences.getString("access", null)
        set(value) { preferences.edit().putString("access", value).apply() }

    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .addInterceptor { chain ->
            val request = chain.request()
            val access = token
            val authenticated = if (!access.isNullOrBlank() && !request.url.encodedPath.startsWith("/api/auth/")) {
                request.newBuilder().header("Authorization", "Bearer $access").build()
            } else request
            chain.proceed(authenticated)
        }.build()

    val api: CookingApi = Retrofit.Builder().baseUrl(BuildConfig.API_URL).client(client)
        .addConverterFactory(GsonConverterFactory.create()).build().create(CookingApi::class.java)

    suspend fun recipes(scope: String = "catalog", query: String? = null, filters: Filters = Filters(),
                        sort: String = "new", page: Int = 0) =
        api.recipes(scope, query?.takeIf(String::isNotBlank), filters.category, filters.difficulty,
            filters.min, filters.max, sort, page)
}
