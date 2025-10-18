package ru.nsu.fitkulin.own;

import java.util.concurrent.atomic.AtomicLong;

final class BubbleSorter implements Runnable {
    private final LockingSinglyLinkedList list;
    private final long delayBetweenMs;
    private final long delayInsideMs;
    private final AtomicLong stepCounter;
    private volatile boolean running = true;

    BubbleSorter(LockingSinglyLinkedList list, long delayBetweenMs, long delayInsideMs, AtomicLong stepCounter) {
        this.list = list;
        this.delayBetweenMs = delayBetweenMs;
        this.delayInsideMs = delayInsideMs;
        this.stepCounter = stepCounter;
    }

    public void stop() { running = false; }

    @Override public void run() {
        try {
            while (running) {
                list.bubblePass(delayInsideMs, delayBetweenMs, stepCounter::incrementAndGet);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
