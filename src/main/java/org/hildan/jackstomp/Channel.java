package org.hildan.jackstomp;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.springframework.messaging.simp.stomp.StompSession.Subscription;

/**
 * Enhanced subscription that gives access to a queue of received messages. Messages are of type T, deserialized from
 * JSON using a Jackson converter.
 *
 * @param <T>
 *         the type of messages in the queue
 */
public class Channel<T> implements Subscription {

    private final BlockingQueue<T> messageQueue;

    private final Subscription subscription;

    /**
     * The default timeout for message queue blocking retrieval.
     */
    private int defaultTimeout = 10;

    /**
     * The default timeout's unit.
     */
    private TimeUnit defaultTimeoutUnit = TimeUnit.SECONDS;

    /**
     * Creates a channel encapsulating the given {@link Subscription} and appending to the given queue.
     *
     * @param subscription
     *         the subscription to encapsulate and delegate to
     * @param messageQueue
     *         the queue to append messages to
     */
    Channel(Subscription subscription, BlockingQueue<T> messageQueue) {
        this.subscription = subscription;
        this.messageQueue = messageQueue;
    }

    /**
     * Sets the default timeout to wait for messages to arrive. This is used by the {@link #next()} method that
     * doesn't take a timeout argument.
     *
     * @param timeout
     *         how long to wait for messages before giving up, in units of {@code unit}
     * @param unit
     *         a {@code TimeUnit} determining how to interpret the {@code timeout} parameter
     */
    public void setDefaultTimeout(int timeout, TimeUnit unit) {
        this.defaultTimeout = timeout;
        this.defaultTimeoutUnit = unit;
    }

    /**
     * Removes and retrieves the first of the received messages, waiting up to a default timeout if necessary for a
     * message to arrive. The default timeout can be set using {@link #setDefaultTimeout(int, TimeUnit)}.
     *
     * @return the next message of this channel, or {@code null} if the waiting time elapsed before a message arrives.
     *
     * @throws InterruptedException
     *         if the current thread was interrupted while waiting for a message to arrive
     */
    public T next() throws InterruptedException {
        return messageQueue.poll(defaultTimeout, defaultTimeoutUnit);
    }

    /**
     * Removes and retrieves the first of the received messages, waiting up to the given amount of time if necessary for
     * a message to arrive.
     *
     * @param timeout
     *         how long to wait before giving up, in units of {@code unit}
     * @param unit
     *         a {@code TimeUnit} determining how to interpret the {@code timeout} parameter
     *
     * @return the next message of this channel, or {@code null} if the specified waiting time elapsed before a message
     * arrives.
     *
     * @throws InterruptedException
     *         if the current thread was interrupted while waiting for a message to arrive
     */
    public T next(long timeout, TimeUnit unit) throws InterruptedException {
        return messageQueue.poll(timeout, unit);
    }

    @Override
    public String getSubscriptionId() {
        return subscription.getSubscriptionId();
    }

    @Override
    public void unsubscribe() {
        subscription.unsubscribe();
    }

    @Override
    public String getReceiptId() {
        return subscription.getReceiptId();
    }

    @Override
    public void addReceiptTask(Runnable runnable) {
        subscription.addReceiptTask(runnable);
    }

    @Override
    public void addReceiptLostTask(Runnable runnable) {
        subscription.addReceiptLostTask(runnable);
    }
}
