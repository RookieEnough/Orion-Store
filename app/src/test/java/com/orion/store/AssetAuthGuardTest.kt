package com.orion.store

import com.orion.store.platform.AssetAuth
import com.orion.store.platform.AssetAuthGuard
import com.sun.net.httpserver.HttpServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress

/** A credential must stay on the host it belongs to, also through a redirect: a release asset link often bounces to a CDN or to another site. */
class AssetAuthGuardTest {
    private fun server(handler: (Map<String, String>) -> Pair<Int, String?>): Pair<HttpServer, MutableList<Map<String, String>>> {
        val seen = mutableListOf<Map<String, String>>()
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/") { ex ->
            val h = ex.requestHeaders.entries.associate { it.key.lowercase() to it.value.first() }; seen += h
            val (code, location) = handler(h)
            if (location != null) ex.responseHeaders.add("Location", location)
            ex.sendResponseHeaders(code, -1); ex.close()
        }
        s.start(); return s to seen
    }

    @Test fun credentialsStayOnTheirHost() {
        // "localhost" and "127.0.0.1" are different hosts to OkHttp, which is all a redirect to another site needs to be
        val (other, otherSeen) = server { 200 to null }
        val (owner, ownerSeen) = server { 302 to "http://localhost:${other.address.port}/file" }
        try {
            val client = OkHttpClient.Builder().addNetworkInterceptor(AssetAuthGuard()).build()
            val auth = AssetAuth("127.0.0.1", mapOf("PRIVATE-TOKEN" to "secret", "Authorization" to "token secret"))
            val req = Request.Builder().url("http://127.0.0.1:${owner.address.port}/asset")
                .header("PRIVATE-TOKEN", "secret").header("Authorization", "token secret").header("Accept", "application/octet-stream")
                .tag(AssetAuth::class.java, auth).build()
            client.newCall(req).execute().use { assertEquals(200, it.code) }
            assertEquals("secret", ownerSeen.single()["private-token"])
            assertEquals("token secret", ownerSeen.single()["authorization"])
            // Followed to the other host: the credentials are gone, the rest of the request is not
            assertNull(otherSeen.single()["private-token"])
            assertNull(otherSeen.single()["authorization"])
            assertEquals("application/octet-stream", otherSeen.single()["accept"])
        } finally { owner.stop(0); other.stop(0) }
    }

    @Test fun untaggedRequestsAreLeftAlone() {
        val (s, seen) = server { 200 to null }
        try {
            val client = OkHttpClient.Builder().addNetworkInterceptor(AssetAuthGuard()).build()
            client.newCall(Request.Builder().url("http://127.0.0.1:${s.address.port}/x").header("Authorization", "Bearer kept").build()).execute().close()
            assertEquals("Bearer kept", seen.single()["authorization"])
        } finally { s.stop(0) }
    }
}
