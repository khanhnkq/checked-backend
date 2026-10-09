package com.codegym.locketclone.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AsyncConfigTest {

    @Test
    void getAsyncExecutor_configuresThreadPoolCorrectly() throws InterruptedException {
        AsyncConfig config = new AsyncConfig();
        Executor executor = config.getAsyncExecutor();

        assertNotNull(executor);
        assertInstanceOf(ThreadPoolTaskExecutor.class, executor);

        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertEquals(4, taskExecutor.getCorePoolSize());
        assertEquals(16, taskExecutor.getMaxPoolSize());
        assertEquals(200, taskExecutor.getQueueCapacity());
        assertEquals("mail-exec-", taskExecutor.getThreadNamePrefix());

        // Verify thread execution uses configured prefix
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> threadNameRef = new AtomicReference<>();
        taskExecutor.execute(() -> {
            threadNameRef.set(Thread.currentThread().getName());
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(threadNameRef.get());
        assertTrue(threadNameRef.get().startsWith("mail-exec-"));

        taskExecutor.shutdown();
    }

    @Test
    void getAsyncUncaughtExceptionHandler_handlesExceptionGracefully() throws NoSuchMethodException {
        AsyncConfig config = new AsyncConfig();
        AsyncUncaughtExceptionHandler handler = config.getAsyncUncaughtExceptionHandler();

        assertNotNull(handler);

        Method dummyMethod = AsyncConfigTest.class.getDeclaredMethod("dummyAsyncMethod");
        assertDoesNotThrow(() -> handler.handleUncaughtException(
                new RuntimeException("Test async error"),
                dummyMethod,
                "param1"
        ));
    }

    public void dummyAsyncMethod() {
        // Dummy method for reflection test
    }
}
