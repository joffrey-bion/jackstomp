package org.hildan.jackstomp;

import java.lang.reflect.Type;
import java.util.function.Consumer;

import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;

/**
 * An implementation of {@link StompFrameHandler} that expects messages of type T only. It converts each received
 * message's payload into an object of type T and forwards it to the given handler. This is a simple convenience class
 * to make subscriptions simpler.
 *
 * @param <T>
 *         the target type for the conversion of the received messages' payload
 */
class SingleTypeFrameHandler<T> implements StompFrameHandler {

    private final Class<T> payloadType;

    private final Consumer<T> handler;

    /**
     * Creates an {@code SingleTypeFrameHandler} forwarding messages of type T to the given handler.
     *
     * @param payloadType
     *         the target type for the conversion of the received messages' payload
     * @param handler
     *         the function to call with the converted received message
     */
    SingleTypeFrameHandler(Class<T> payloadType, Consumer<T> handler) {
        this.payloadType = payloadType;
        this.handler = handler;
    }

    @Override
    public Type getPayloadType(StompHeaders stompHeaders) {
        return payloadType;
    }

    @Override
    public void handleFrame(StompHeaders stompHeaders, Object payload) {
        handler.accept(payloadType.cast(payload));
    }
}
