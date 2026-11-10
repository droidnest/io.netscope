package io.netscope.shake

import org.junit.Assert.assertEquals
import org.junit.Test

class ShakePeaksTest {

    private val peaks = ShakePeaks()

    @Test
    fun `should not report a shake for a single jolt`() {
        // When
        val result = peaks.isShake(nowMs = 0L)

        // Then
        assertEquals(false, result)
    }

    @Test
    fun `should report a shake on the third jolt within the window`() {
        // When
        val results = listOf(0L, 300L, 600L).map { peaks.isShake(nowMs = it) }

        // Then
        assertEquals(listOf(false, false, true), results)
    }

    @Test
    fun `should count readings of one jolt only once`() {
        // When
        val results = listOf(0L, 20L, 40L, 60L, 80L).map { peaks.isShake(nowMs = it) }

        // Then
        assertEquals(List(size = 5) { false }, results)
    }

    @Test
    fun `should not report a shake when the jolts are spread over more than the window`() {
        // When
        val results = listOf(0L, 700L, 1_400L).map { peaks.isShake(nowMs = it) }

        // Then
        assertEquals(listOf(false, false, false), results)
    }

    @Test
    fun `should ignore jolts during the cooldown after a shake`() {
        // Given
        listOf(0L, 300L, 600L).forEach { peaks.isShake(nowMs = it) }

        // When
        val results = listOf(900L, 1_200L, 1_500L).map { peaks.isShake(nowMs = it) }

        // Then
        assertEquals(listOf(false, false, false), results)
    }

    @Test
    fun `should forget earlier jolts when reset`() {
        // Given
        listOf(0L, 300L).forEach { peaks.isShake(nowMs = it) }
        peaks.reset()

        // When
        val result = peaks.isShake(nowMs = 600L)

        // Then
        assertEquals(false, result)
    }
}
