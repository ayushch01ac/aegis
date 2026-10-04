package com.aegis.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aegis.route.RoutePriority;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PriorityExecutionServiceTest {
    private PriorityExecutionService service;

    @AfterEach
    void tearDown() { if (service != null) service.shutdown(); }

    @Test
    void rejectsWhenWorkerAndBoundedQueueAreOccupied() throws Exception {
        service = new PriorityExecutionService(new ExecutionProperties(true, 1, 1));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            callers.submit(() -> service.execute(RoutePriority.NORMAL, () -> {
                started.countDown();
                try { release.await(); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                return "first";
            }));
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            callers.submit(() -> service.execute(RoutePriority.LOW, () -> "queued"));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (service.queuedTasks() != 1 && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertThat(service.queuedTasks()).isEqualTo(1);
            assertThatThrownBy(() -> service.execute(RoutePriority.CRITICAL, () -> "rejected"))
                    .isInstanceOf(OverloadedException.class);
        } finally {
            release.countDown();
            callers.shutdownNow();
        }
    }
}
