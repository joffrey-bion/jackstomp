package org.hildan.jackstomp;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;

/**
 * A wrapper around {@link StompSession} that provides additional subscription features. For instance, it can return a
 * {@link Channel} upon subscription for a given payload type, which can then be queried actively. This is particularly
 * useful for unit tests.
 */
public class JackstompSession implements StompSession {

    private final StompSession stompSession;

    /**
     * The default timeout to receive a response in calls to {@link #request(Object, Class, String, String)}.
     */
    private int defaultRequestTimeout = 10;

    /**
     * The default timeout's unit.
     */
    private TimeUnit defaultRequestTimeoutUnit = TimeUnit.SECONDS;

    /**
     * Creates a new {@code JackstompSession} wrapping and delegating to the given {@link StompSession}.
     *
     * @param stompSession
     *         the session to wrap
     */
    public JackstompSession(StompSession stompSession) {
        this.stompSession = stompSession;
    }

    /**
     * Sets the default timeout to wait for messages to arrive. This is used by the
     * {@link #request(Object, Class, String, String)} method that doesn't take a timeout argument.
     *
     * @param timeout
     *         how long to wait for messages before giving up, in units of {@code unit}
     * @param unit
     *         a {@code TimeUnit} determining how to interpret the {@code timeout} parameter
     */
    public void setDefaultRequestTimeout(int timeout, TimeUnit unit) {
        this.defaultRequestTimeout = timeout;
        this.defaultRequestTimeoutUnit = unit;
    }

    @Override
    public Receiptable send(String destination, Object payload) {
        return stompSession.send(destination, payload);
    }

    @Override
    public Receiptable send(StompHeaders headers, Object payload) {
        return stompSession.send(headers, payload);
    }

    @Override
    public Subscription subscribe(String destination, StompFrameHandler handler) {
        return stompSession.subscribe(destination, handler);
    }

    @Override
    public Subscription subscribe(StompHeaders headers, StompFrameHandler handler) {
        return stompSession.subscribe(headers, handler);
    }

    /**
     * Subscribes to the given destination by sending a SUBSCRIBE frame, and handles received messages with the
     * specified handler.
     *
     * @param destination
     *         the destination to subscribe to
     * @param payloadType
     *         the expected type for received messages, for Jackson deserialization
     * @param handler
     *         the handler for received messages
     * @param <T>
     *         the java type that received messages should be deserialized to
     *
     * @return a handle to use to unsubscribe and/or track receipts
     */
    public <T> Subscription subscribe(String destination, Class<T> payloadType, Consumer<T> handler) {
        StompFrameHandler frameHandler = new SingleTypeFrameHandler<>(payloadType, handler);
        return stompSession.subscribe(destination, frameHandler);
    }

    /**
     * Subscribes to the given destination by sending a SUBSCRIBE frame, and handles received messages with the
     * specified handler.
     *
     * @param headers
     *         the headers for the SUBSCRIBE message frame
     * @param payloadType
     *         the expected type for received messages, for Jackson deserialization
     * @param handler
     *         the handler for received messages
     * @param <T>
     *         the java type that received messages should be deserialized to
     *
     * @return a handle to use to unsubscribe and/or track receipts
     */
    public <T> Subscription subscribe(StompHeaders headers, Class<T> payloadType, Consumer<T> handler) {
        StompFrameHandler frameHandler = new SingleTypeFrameHandler<>(payloadType, handler);
        return stompSession.subscribe(headers, frameHandler);
    }

    /**
     * Subscribes to the given destination by sending a SUBSCRIBE frame, and queues received messages in the returned
     * {@link Channel}.
     *
     * @param destination
     *         the destination to subscribe to
     * @param payloadType
     *         the expected type for received messages, for Jackson deserialization
     * @param <T>
     *         the java type that received messages should be deserialized to
     *
     * @return a new channel to use to actively check for received values, unsubscribe, or track receipts
     */
    public <T> Channel<T> subscribe(String destination, Class<T> payloadType) {
        BlockingQueue<T> blockingQueue = new LinkedBlockingDeque<>();
        StompFrameHandler frameHandler = new QueuedStompFrameHandler<>(blockingQueue, payloadType);
        Subscription sub = stompSession.subscribe(destination, frameHandler);
        return new Channel<>(sub, blockingQueue);
    }

    /**
     * Subscribe to the given destination by sending a SUBSCRIBE frame and queue received messages in the returned
     * {@link Channel}. The messages are expected to have no body, and empty {@link Object}s are queued to be able to
     * track reception events.
     *
     * @param destination
     *         the destination to subscribe to
     *
     * @return a new channel to use to actively check for received events, unsubscribe, or track receipts
     */
    public Channel<Object> subscribeEmptyMsgs(String destination) {
        BlockingQueue<Object> blockingQueue = new LinkedBlockingDeque<>();
        StompFrameHandler frameHandler = new EmptyMsgStompFrameHandler(blockingQueue);
        Subscription sub = stompSession.subscribe(destination, frameHandler);
        return new Channel<>(sub, blockingQueue);
    }

    /**
     * An overloaded version of {@link #request(Object, Class, String, String, int, TimeUnit)} using the default
     * timeout. The default timeout can be modified using {@link #setDefaultRequestTimeout(int, TimeUnit)}.
     *
     * @param payload
     *         the payload to send on the given requestDestination
     * @param responseType
     *         the type of the response to expect on the responseDestination
     * @param requestDestination
     *         the destination to send payload to
     * @param responseDestination
     *         the destination to expect a response on
     * @param <T>
     *         the type of object to receive as a response
     *
     * @return the response object, deserialized from the JSON received on the responseDestination, or null if no
     * response was received before timeout.
     * @throws InterruptedException
     *         if the current thread was interrupted while waiting for the response
     */
    public <T> T request(Object payload, Class<T> responseType, String requestDestination, String responseDestination)
            throws InterruptedException {
        return request(payload, responseType, requestDestination, responseDestination, defaultRequestTimeout,
                defaultRequestTimeoutUnit);
    }

    /**
     * Makes a synchronous request/response call via a send and a one-time subscription. <p> A SUBSCRIBE frame is first
     * sent to the server to be able to receive the response, and then the payload is sent via a SEND frame. The
     * response is expected to be received on the subscribed channel with a default timeout as specified by {@link
     * Channel#next()}, and then the subscription is ended whether or not a response has been received. The return value
     * is the received object, or null if none were received.
     *
     * @param payload
     *         the payload to send on the given requestDestination
     * @param responseType
     *         the type of the response to expect on the responseDestination
     * @param requestDestination
     *         the destination to send payload to
     * @param responseDestination
     *         the destination to expect a response on
     * @param timeout
     *         how long to wait for messages before giving up, in units of {@code unit}
     * @param timeoutUnit
     *         a {@code TimeUnit} determining how to interpret the {@code timeout} parameter
     * @param <T>
     *         the type of object to receive as a response
     *
     * @return the response object, deserialized from the JSON received on the responseDestination, or null if no
     * response was received before timeout.
     * @throws InterruptedException
     *         if the current thread was interrupted while waiting for the response
     */
    public <T> T request(Object payload, Class<T> responseType, String requestDestination, String responseDestination,
            int timeout, TimeUnit timeoutUnit) throws InterruptedException {
        Channel<T> channel = subscribe(responseDestination, responseType);
        send(requestDestination, payload);
        T msg = channel.next(timeout, timeoutUnit);
        channel.unsubscribe();
        return msg;
    }

    /**
     * An overloaded version of {@link #request(Object, String, String, int, TimeUnit)} using the default timeout.
     * The default timeout can be modified using {@link #setDefaultRequestTimeout(int, TimeUnit)}.
     *
     * @param payload
     *         the payload to send on the given requestDestination
     * @param requestDestination
     *         the destination to send payload to
     * @param responseDestination
     *         the destination to expect a response on
     *
     * @return true if the event message was received before timeout, false otherwise
     * @throws InterruptedException
     *         if the current thread was interrupted while waiting for the response
     */
    public boolean request(Object payload, String requestDestination, String responseDestination)
            throws InterruptedException {
        return request(payload, requestDestination, responseDestination, defaultRequestTimeout,
                defaultRequestTimeoutUnit);
    }

    /**
     * An overloaded version of {@link #request(Object, Class, String, String, int, TimeUnit)} that does not expect any
     * value as response, just an event message with no body.
     *
     * @param payload
     *         the payload to send on the given requestDestination
     * @param requestDestination
     *         the destination to send payload to
     * @param responseDestination
     *         the destination to expect a response on
     * @param timeout
     *         how long to wait for messages before giving up, in units of {@code unit}
     * @param timeoutUnit
     *         a {@code TimeUnit} determining how to interpret the {@code timeout} parameter
     *
     * @return true if the event message was received before timeout, false otherwise
     * @throws InterruptedException
     *         if the current thread was interrupted while waiting for the response
     */
    public boolean request(Object payload, String requestDestination, String responseDestination, int timeout,
            TimeUnit timeoutUnit) throws InterruptedException {
        Channel<Object> channel = subscribeEmptyMsgs(responseDestination);
        send(requestDestination, payload);
        Object msg = channel.next(timeout, timeoutUnit);
        channel.unsubscribe();
        return msg != null;
    }

    @Override
    public Receiptable acknowledge(String messageId, boolean consumed) {
        return stompSession.acknowledge(messageId, consumed);
    }

    @Override
    public String getSessionId() {
        return stompSession.getSessionId();
    }

    @Override
    public boolean isConnected() {
        return stompSession.isConnected();
    }

    @Override
    public void setAutoReceipt(boolean enabled) {
        stompSession.setAutoReceipt(enabled);
    }

    @Override
    public void disconnect() {
        stompSession.disconnect();
    }
}
