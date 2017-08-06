package org.hildan.jackstomp;

import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;

public class LoggingStompSessionHandler extends StompSessionHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(LoggingStompSessionHandler.class);

    private final BiConsumer<StompSession, StompHeaders> afterConnectedHandler;

    LoggingStompSessionHandler() {
        this.afterConnectedHandler = null;
    }

    LoggingStompSessionHandler(BiConsumer<StompSession, StompHeaders> afterConnectedHandler) {
        this.afterConnectedHandler = afterConnectedHandler;
    }

    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
        if (afterConnectedHandler != null) {
            afterConnectedHandler.accept(session, connectedHeaders);
        }
    }

    @Override
    public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload,
                                Throwable exception) {
        logger.error("Exception thrown in session " + session.getSessionId(), exception);
    }

    @Override
    public void handleTransportError(StompSession session, Throwable exception) {
        logger.error("Transport exception thrown in session " + session.getSessionId(), exception);
    }
}
