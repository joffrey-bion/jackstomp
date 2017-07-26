package org.hildan.jackstomp;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;

import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;

/**
 * An implementation of {@link StompFrameHandler} that expects messages of type T. It enqueues objects as messages
 * arrive, to be able to actively track the receptions (usually in a {@link Channel}).
 *
 * @param <T>
 *         the target type for the conversion of the received messages' payload
 */
class QueuedStompFrameHandler<T> implements StompFrameHandler {

    private final BlockingQueue<T> blockingQueue;

    private final Class<T> payloadType;

    /**
     * Creates an {@code EmptyMsgStompFrameHandler} adding the received messages the given queue.
     *
     * @param blockingQueue
     *         the queue to add messages to
     * @param payloadType
     *         the target type for the conversion of the received messages' payload
     */
    QueuedStompFrameHandler(BlockingQueue<T> blockingQueue, Class<T> payloadType) {
        this.blockingQueue = blockingQueue;
        this.payloadType = payloadType;
    }

    @Override
    public Type getPayloadType(StompHeaders stompHeaders) {
        return payloadType;
    }

    @Override
    public void handleFrame(StompHeaders stompHeaders, Object payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Unsupported null payloads in this type of frame handler");
        }
        blockingQueue.offer(payloadType.cast(payload));
    }
}
