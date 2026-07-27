package app.hermes.net

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * In-memory cookie jar that persists cookies across requests.
 * Used by LoginHelper for password login flow.
 */
class CookieJarImpl : CookieJar {
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        if (!cookieStore.containsKey(host)) {
            cookieStore[host] = mutableListOf()
        }
        cookieStore[host]!!.addAll(cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        return cookieStore[host]?.toList() ?: emptyList()
    }
}
