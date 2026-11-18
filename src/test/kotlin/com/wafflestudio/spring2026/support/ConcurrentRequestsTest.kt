package com.wafflestudio.spring2026.support

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ConcurrentRequestsTest {
    @Test
    fun `풀보다 많은 작업을 겹쳐 실행하고 모든 결과를 제출 순서로 회수한다`() {
        val barrier = CyclicBarrier(2)

        val results = runConcurrently((1..6).toList(), parallelism = 2) { input ->
            barrier.await(5, TimeUnit.SECONDS)
            input * 2
        }

        assertEquals(listOf(2, 4, 6, 8, 10, 12), results)
    }

    @Test
    fun `작업 스레드의 예외를 정상적인 실패 응답으로 숨기지 않는다`() {
        val failure = assertFailsWith<AssertionError> {
            runConcurrently(listOf(1)) {
                throw IllegalArgumentException("worker failed")
            }
        }

        assertIs<IllegalArgumentException>(failure.cause)
        assertEquals("worker failed", failure.cause?.message)
    }

    @Test
    fun `끝나지 않는 작업을 제한 시간에 실패시키고 중단한다`() {
        val release = CountDownLatch(1)

        try {
            val failure = assertFailsWith<AssertionError> {
                runConcurrently(listOf(1), timeout = Duration.ofSeconds(2)) {
                    release.await()
                }
            }
            assertIs<java.util.concurrent.TimeoutException>(failure.cause)
        } finally {
            release.countDown()
        }
    }
}
