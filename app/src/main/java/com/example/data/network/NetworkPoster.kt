package com.example.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

data class NetResult(val ok: Boolean, val body: String, val httpCode: Int, val error: String)

/**
 * Esegue una POST vincolata esplicitamente a una rete con internet REALE.
 *
 * Perche': in background su dati mobili, la "default network" del processo
 * puo' non avere internet utilizzabile (Doze / Data Saver / OEM). Richiedendo
 * esplicitamente una rete con NET_CAPABILITY_INTERNET e aprendo la
 * connessione tramite network.openConnection(), la POST parte sempre.
 *
 * Si usa HttpURLConnection (non OkHttp) perche' OkHttp.socketFactory() NON
 * rispetta il binding di rete dal 3.5.0 (square/okhttp issue #3736).
 *
 * IMPORTANTE: deve essere chiamato da un thread in background (Dispatchers.IO),
 * perche' usa CountDownLatch.await() che blocca il thread.
 */
object NetworkPoster {

    fun post(
        context: Context,
        urlString: String,
        body: String,
        connectTimeoutMs: Int = 15000,
        readTimeoutMs: Int = 20000
    ): NetResult {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // NB: NET_CAPABILITY_VALIDATED NON puo' essere richiesta (la assegna il
        // sistema, non l'app) -> richiederla causa "Cannot request network with
        // VALIDATED". Si richiede solo INTERNET.
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val networkRef = AtomicReference<Network?>(null)
        val latch = CountDownLatch(1)

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                networkRef.set(network)
                latch.countDown()
            }
            override fun onUnavailable() {
                latch.countDown()
            }
        }

        var registered = false
        return try {
            // requestNetwork con timeout e' API 26+. Sotto, versione senza timeout.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                cm.requestNetwork(request, callback, 20000)
            } else {
                cm.requestNetwork(request, callback)
            }
            registered = true

            val gotNetwork = latch.await(20, TimeUnit.SECONDS)
            val network = networkRef.get()

            if (!gotNetwork || network == null) {
                // Fallback: connessione di default
                doPostDefault(urlString, body, connectTimeoutMs, readTimeoutMs)
            } else {
                doPostOnNetwork(network, urlString, body, connectTimeoutMs, readTimeoutMs)
            }
        } catch (e: Exception) {
            NetResult(false, "", -1, e.localizedMessage ?: "Errore rete")
        } finally {
            if (registered) {
                try { cm.unregisterNetworkCallback(callback) } catch (_: Exception) {}
            }
        }
    }

    private fun doPostOnNetwork(
        network: Network,
        urlString: String,
        body: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): NetResult {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = network.openConnection(url) as HttpURLConnection
            writeAndRead(conn, body, connectTimeoutMs, readTimeoutMs)
        } catch (e: Exception) {
            NetResult(false, "", -1, e.localizedMessage ?: "Errore connessione")
        } finally {
            conn?.disconnect()
        }
    }

    private fun doPostDefault(
        urlString: String,
        body: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): NetResult {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = url.openConnection() as HttpURLConnection
            writeAndRead(conn, body, connectTimeoutMs, readTimeoutMs)
        } catch (e: Exception) {
            NetResult(false, "", -1, e.localizedMessage ?: "Errore connessione")
        } finally {
            conn?.disconnect()
        }
    }

    private fun writeAndRead(
        conn: HttpURLConnection,
        body: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): NetResult {
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8")

        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        conn.setFixedLengthStreamingMode(bytes.size)
        val os: OutputStream = conn.outputStream
        os.write(bytes)
        os.flush()
        os.close()

        val code = conn.responseCode
        val stream = if (code in 200..399) conn.inputStream else conn.errorStream
        val text = stream?.let {
            BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).use { r -> r.readText() }
        } ?: ""

        return NetResult(code in 200..399, text, code, "")
    }
}
