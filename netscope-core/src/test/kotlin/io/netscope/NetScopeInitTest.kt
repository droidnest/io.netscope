package io.netscope

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetScopeInitTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = NetScope.resetForTests()

    @After
    fun tearDown() = NetScope.resetForTests()

    @Test
    fun `interceptor DSL after ensureInitialized replaces the default config`() {
        NetScope.ensureInitialized(context)
        assertTrue("x-api-key" !in NetScope.config.redactedLowercase)

        val interceptor = NetScope.interceptor(context) { redactHeaders("X-Api-Key") }

        assertTrue("x-api-key" in NetScope.config.redactedLowercase)
        assertTrue("x-api-key" in interceptor.config.redactedLowercase)

        runBlocking { NetScope.repository.clearAll() }
        val terminal = Interceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body("{}".toResponseBody()).build()
        }
        OkHttpClient.Builder().addInterceptor(interceptor).addInterceptor(terminal).build()
            .newCall(Request.Builder().url("https://example.com/").header("X-Api-Key", "secret").build())
            .execute().close()

        val stored = runBlocking { NetScope.repository.getAllWithBodies() }.single()
        val header = stored.requestHeaders.single { it.name.equals("X-Api-Key", ignoreCase = true) }
        assertTrue(header.isRedacted)
        assertEquals("██████", header.value)
    }

    @Test
    fun `ensureInitialized after interceptor DSL keeps the DSL config`() {
        val interceptor = NetScope.interceptor(context) { redactHeaders("X-Api-Key") }

        NetScope.ensureInitialized(context)

        assertTrue("x-api-key" in NetScope.config.redactedLowercase)
        assertTrue("x-api-key" in interceptor.config.redactedLowercase)
    }

    @Test
    fun `concurrent ensureInitialized never overrides the DSL config`() {
        repeat(50) {
            NetScope.resetForTests()
            val start = CountDownLatch(1)
            val dslDone = AtomicBoolean(false)
            val hammer = thread {
                start.await()
                // Keep racing until well after the DSL call returned.
                var extra = 0
                while (!dslDone.get() || extra++ < 100) NetScope.ensureInitialized(context)
            }
            val dsl = thread {
                start.await()
                val interceptor = NetScope.interceptor(context) { redactHeaders("X-Api-Key") }
                assertTrue("x-api-key" in interceptor.config.redactedLowercase)
                dslDone.set(true)
            }
            start.countDown()
            dsl.join(TimeUnit.SECONDS.toMillis(10))
            hammer.join(TimeUnit.SECONDS.toMillis(10))

            assertTrue(dslDone.get())
            assertTrue("x-api-key" in NetScope.config.redactedLowercase)
        }
    }
}
