package app.hermes.net

import android.content.Context
import app.hermes.auth.AuthManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class ApiClientFactory(private val context: Context) {

    private val authManager = AuthManager(context)

    suspend fun getAuthStatus() = authManager.getAuthStatus()

    fun createOkHttpClient(authMode: String, baseUrl: String): OkHttpClient {
        return OkHttpClient.Builder()
            .cookieJar(CookieJarImpl())
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun createRetrofitClient(baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl("$baseUrl/")
            .client(createOkHttpClient("password", baseUrl))
            .addConverterFactory(
                MoshiConverterFactory.create(
                    Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                )
            )
            .build()
    }
}
