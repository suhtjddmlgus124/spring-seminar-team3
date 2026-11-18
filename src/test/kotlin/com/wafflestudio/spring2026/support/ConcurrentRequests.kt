package com.wafflestudio.spring2026.support

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal fun <T, R> runConcurrently(
    inputs: List<T>,
    parallelism: Int = 32,
    timeout: Duration = Duration.ofSeconds(90),
    action: (T) -> R,
): List<R> {
    require(parallelism > 0)
    require(!timeout.isNegative && !timeout.isZero)
    if (inputs.isEmpty()) return emptyList()

    val workers = minOf(parallelism, inputs.size)
    val ready = CountDownLatch(workers)
    val start = CountDownLatch(1)
    val deadline = System.nanoTime() + timeout.toNanos()
    val executor = Executors.newFixedThreadPool(workers) { runnable ->
        Thread(runnable, "api-concurrency-test").apply { isDaemon = true }
    }
    val futures = inputs.map { input ->
        executor.submit<R> {
            ready.countDown()
            start.await()
            action(input)
        }
    }

    try {
        check(ready.await(maxOf(0L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
            "동시 요청 작업이 제한 시간 안에 시작 준비를 마치지 못했습니다."
        }
        start.countDown()

        return futures.mapIndexed { index, future ->
            try {
                future.get(maxOf(0L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)
            } catch (exception: ExecutionException) {
                throw AssertionError("동시 요청 #$index 실행 중 예상하지 못한 예외가 발생했습니다.", exception.cause)
            } catch (exception: TimeoutException) {
                throw AssertionError("동시 요청이 제한 시간 $timeout 안에 완료되지 않았습니다.", exception)
            }
        }
    } finally {
        start.countDown()
        futures.forEach { it.cancel(true) }
        executor.shutdownNow()
        executor.awaitTermination(5, TimeUnit.SECONDS)
    }
}
