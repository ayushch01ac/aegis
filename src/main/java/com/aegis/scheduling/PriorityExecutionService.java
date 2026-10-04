package com.aegis.scheduling;

import com.aegis.route.RoutePriority;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** Bounds concurrent downstream work and queues higher-priority routes ahead of lower-priority work. */
@Component
@EnableConfigurationProperties(ExecutionProperties.class)
public class PriorityExecutionService {
    private final ExecutionProperties properties;
    private final AtomicLong sequence = new AtomicLong();
    private final ThreadPoolExecutor executor;

    public PriorityExecutionService(ExecutionProperties properties) {
        this.properties = properties;
        this.executor = new ThreadPoolExecutor(
                properties.maxWorkers(), properties.maxWorkers(), 0, TimeUnit.MILLISECONDS,
                new BoundedPriorityBlockingQueue<>(properties.queueCapacity()),
                new ThreadPoolExecutor.AbortPolicy()) {
            @Override protected <T> java.util.concurrent.RunnableFuture<T> newTaskFor(java.util.concurrent.Callable<T> callable) {
                return new PrioritizedFutureTask<>(callable, RoutePriority.NORMAL, sequence.getAndIncrement());
            }
        };
    }

    public <T> T execute(RoutePriority priority, Supplier<T> work) {
        if (!properties.enabled()) return work.get();
        PrioritizedFutureTask<T> task = new PrioritizedFutureTask<>(work::get, priority, sequence.getAndIncrement());
        try {
            executor.execute(task);
        } catch (java.util.concurrent.RejectedExecutionException exception) {
            throw new OverloadedException();
        }
        try {
            return task.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OverloadedException();
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("Proxy task failed", exception.getCause());
        }
    }

    @PreDestroy
    void shutdown() { executor.shutdown(); }

    int queuedTasks() { return executor.getQueue().size(); }

    private static final class PrioritizedFutureTask<T> extends FutureTask<T> implements Comparable<PrioritizedFutureTask<?>> {
        private final RoutePriority priority;
        private final long sequence;
        PrioritizedFutureTask(java.util.concurrent.Callable<T> work, RoutePriority priority, long sequence) {
            super(work); this.priority = priority; this.sequence = sequence;
        }
        @Override public int compareTo(PrioritizedFutureTask<?> other) {
            int byPriority = Integer.compare(priority.ordinal(), other.priority.ordinal());
            return byPriority != 0 ? byPriority : Long.compare(sequence, other.sequence);
        }
    }
}
