package com.aegis.scheduling;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Priority queue with an explicit capacity; PriorityBlockingQueue alone is unbounded. */
final class BoundedPriorityBlockingQueue<E> extends PriorityBlockingQueue<E> {
    private final Semaphore permits;

    BoundedPriorityBlockingQueue(int capacity) {
        super(capacity);
        this.permits = new Semaphore(capacity);
    }

    @Override public boolean offer(E element) {
        if (!permits.tryAcquire()) return false;
        boolean added = super.offer(element);
        if (!added) permits.release();
        return added;
    }

    @Override public void put(E element) {
        permits.acquireUninterruptibly();
        boolean added = false;
        try { added = super.offer(element); } finally { if (!added) permits.release(); }
    }

    @Override public boolean offer(E element, long timeout, TimeUnit unit) {
        if (!permits.tryAcquire()) return false;
        boolean added = super.offer(element);
        if (!added) permits.release();
        return added;
    }

    @Override public E take() throws InterruptedException { E value = super.take(); permits.release(); return value; }
    @Override public E poll() { E value = super.poll(); if (value != null) permits.release(); return value; }
    @Override public E poll(long timeout, TimeUnit unit) throws InterruptedException { E value = super.poll(timeout, unit); if (value != null) permits.release(); return value; }
    @Override public boolean remove(Object value) { boolean removed = super.remove(value); if (removed) permits.release(); return removed; }
    @Override public void clear() { int count = size(); super.clear(); permits.release(count); }
    @Override public int drainTo(Collection<? super E> target) { int count = super.drainTo(target); permits.release(count); return count; }
    @Override public int drainTo(Collection<? super E> target, int max) { int count = super.drainTo(target, max); permits.release(count); return count; }
    @Override public Iterator<E> iterator() { return super.iterator(); }
}
