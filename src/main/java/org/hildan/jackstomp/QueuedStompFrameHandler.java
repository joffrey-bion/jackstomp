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
 *         the expected type of the received messages, converted from JSON
 */
class QueuedStompFrameHandler<T> implements StompFrameHandler {

    private final BlockingQueue<T> blockingQueue;

    private final Class<T> type;

    /**
     * Creates an {@code EmptyMsgStompFrameHandler} adding the received messages the given queue.
     *
     * @param blockingQueue
     *         the queue to add messages to
     */
    QueuedStompFrameHandler(BlockingQueue<T> blockingQueue, Class<T> type) {
        this.blockingQueue = blockingQueue;
        this.type = type;
    }

    @Override
    public Type getPayloadType(StompHeaders stompHeaders) {
        return type;
    }

    @Override
    public void handleFrame(StompHeaders stompHeaders, Object o) {
        if (o == null) {
            throw new IllegalArgumentException("Unsupported null payloads in this type of frame handler");
        }
        blockingQueue.offer(type.cast(o));
    }
}
