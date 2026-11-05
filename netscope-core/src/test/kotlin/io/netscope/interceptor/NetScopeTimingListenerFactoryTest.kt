package io.netscope.interceptor

import io.netscope.fake.FakeTransactionRepository
import io.netscope.fake.aCall
import io.netscope.fake.aRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NetScopeTimingListenerFactoryTest {

    @Test
    fun `should measure ttfb from the last request when the call was redirected`() {
        // Given
        var now = 0L
        val repository = FakeTransactionRepository()
        val sut = NetScopeTimingListenerFactory(repository, UnconfinedTestDispatcher()) { now }
        val call = aCall()
        val request = aRequest()
        sut.register(call = call, transactionId = 1L)
        val listener = sut.create(call)
        listener.requestHeadersStart(call)
        listener.requestHeadersEnd(call, request)
        now = 10L
        listener.responseHeadersStart(call)

        // When
        now = 1_000L
        listener.requestHeadersStart(call)
        listener.requestHeadersEnd(call, request)
        now = 1_050L
        listener.responseHeadersStart(call)
        listener.callEnd(call)

        // Then
        assertEquals(50L, repository.ttfbs[1L])
    }
}
