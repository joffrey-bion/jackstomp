package org.hildan.jackstomp;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;

import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;

/**
 * An implementation of {@link StompFrameHandler} that expects no body in the received messages. It enqueues empty
 * objects as messages arrive, to be able to actively track the receptions, for instance in a {@link Channel}.
 */
class EmptyMsgStompFrameHandler implements StompFrameHandler {

    private final BlockingQueue<Object> blockingQueue;

    /**
     * Creates an {@code EmptyMsgStompFrameHandler} adding the received messages the given queue.
     *
     * @param blockingQueue
     *         the queue to add messages to
     */
    EmptyMsgStompFrameHandler(BlockingQueue<Object> blockingQueue) {
        this.blockingQueue = blockingQueue;
    }

    @Override
    public Type getPayloadType(StompHeaders stompHeaders) {
        return Object.class;
    }

    @Override
    public void handleFrame(StompHeaders stompHeaders, Object payload) {
        if (payload != null) {
            throw new IllegalArgumentException("Non-null payload in EmptyMsgStompFrameHandler");
        }
        blockingQueue.offer(new Object());
    }
}
