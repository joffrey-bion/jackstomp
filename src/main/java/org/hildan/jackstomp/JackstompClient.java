package org.hildan.jackstomp;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.springframework.context.SmartLifecycle;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

/**
 * A simple wrapper around a {@link WebSocketStompClient} which preconfigures websocket transports, a Jackson converter
 * for messages, and allows for the creation of {@link JackstompSession}s.
 */
public class JackstompClient implements SmartLifecycle {

    /**
     * The default timeout for new server connections.
     */
    private static final long DEFAULT_TIMEOUT_SEC = 15;

    private final WebSocketStompClient client;

    /**
     * Creates a {@code JackstompClient} with convenient defaults. It creates a pre-configured {@link
     * WebSocketStompClient} using a Spring {@link SockJsClient} and a Jackson message converter. <p> Only the {@link
     * WebSocketTransport} is used for the {@link SockJsClient}. For more transports options, use {@link
     * #JackstompClient(List)}.
     */
    public JackstompClient() {
        this(createWsTransports());
    }

    /**
     * Creates a {@code JackstompClient} with convenient defaults. It creates a pre-configured {@link
     * WebSocketStompClient} using a Spring {@link SockJsClient} on the given transports and a Jackson message
     * converter.
     *
     * @param transports
     *         the transports to use for the inner {@link SockJsClient}
     */
    public JackstompClient(List<Transport> transports) {
        this(createWebSocketClient(transports));
    }

    /**
     * Creates a {@code JackstompClient} with convenient defaults. It creates a pre-configured {@link
     * WebSocketStompClient} using the given {@link WebSocketClient} and a Jackson message converter.
     *
     * @param webSocketClient
     *         the {@link WebSocketClient} to use from the {@link WebSocketStompClient}
     */
    public JackstompClient(WebSocketClient webSocketClient) {
        this(createStompClient(webSocketClient));
    }

    /**
     * Creates a {@code JackstompClient} based on the given {@link WebSocketStompClient}. This allows you to configure
     * as you please the actual client used by Jackstomp.
     *
     * @param client
     *         the websocket client this {@code JackstompClient} should use instead of the default
     */
    public JackstompClient(WebSocketStompClient client) {
        this.client = client;
    }

    private static List<Transport> createWsTransports() {
        return Collections.singletonList(new WebSocketTransport(new StandardWebSocketClient()));
    }

    private static WebSocketClient createWebSocketClient(List<Transport> transports) {
        return new SockJsClient(transports);
    }

    private static WebSocketStompClient createStompClient(WebSocketClient webSocketClient) {
        WebSocketStompClient stompClient = new WebSocketStompClient(webSocketClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter()); // for custom object exchanges
        stompClient.setTaskScheduler(createTaskScheduler()); // for heartbeats
        return stompClient;
    }

    private static ThreadPoolTaskScheduler createTaskScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.afterPropertiesSet();
        return taskScheduler;
    }

    /**
     * Connect to the given WebSocket URL and notify the given {@link StompSessionHandler} when connected on the STOMP
     * level after the CONNECTED frame is received.
     *
     * @param url
     *         the url to connect to
     * @param handler
     *         the session handler
     * @param uriVars
     *         URI variables to expand into the URL
     *
     * @return ListenableFuture for access to the session when ready for use
     */
    public ListenableFuture<JackstompSession> connect(String url, StompSessionHandler handler, Object... uriVars) {
        return mapToJackstomp(client.connect(url, handler, uriVars));
    }

    /**
     * An overloaded version of {@link #connect(String, StompSessionHandler, Object...)} that simplifies the session
     * handler declaration.
     *
     * @param url
     *         the url to connect to
     * @param handler
     *         the session handler
     * @param uriVars
     *         URI variables to expand into the URL
     *
     * @return ListenableFuture for access to the session when ready for use
     */
    public ListenableFuture<JackstompSession> connect(String url, BiConsumer<StompSession, StompHeaders> handler,
            Object... uriVars) {
        LoggingStompSessionHandler sessionHandler = new LoggingStompSessionHandler(handler);
        return connect(url, sessionHandler, uriVars);
    }

    /**
     * An overloaded version of {@link #connect(String, StompSessionHandler, Object...)} that simplifies the session
     * handler declaration.
     *
     * @param url
     *         the url to connect to
     * @param handler
     *         the session handler
     * @param uriVars
     *         URI variables to expand into the URL
     *
     * @return ListenableFuture for access to the session when ready for use
     */
    public ListenableFuture<JackstompSession> connect(String url, Consumer<StompSession> handler, Object... uriVars) {
        BiConsumer<StompSession, StompHeaders> afterConnected = (session, headers) -> handler.accept(session);
        return connect(url, afterConnected, uriVars);
    }

    /**
     * An overloaded version of {@link #connect(String, StompSessionHandler, Object...)} that also accepts {@link
     * WebSocketHttpHeaders} to use for the WebSocket handshake.
     *
     * @param url
     *         the url to connect to
     * @param handshakeHeaders
     *         the headers for the WebSocket handshake
     * @param handler
     *         the session handler
     * @param uriVariables
     *         URI variables to expand into the URL
     *
     * @return ListenableFuture for access to the session when ready for use
     */
    public ListenableFuture<JackstompSession> connect(String url, WebSocketHttpHeaders handshakeHeaders,
            StompSessionHandler handler, Object... uriVariables) {
        ListenableFuture<StompSession> futureSession = client.connect(url, handshakeHeaders, handler, uriVariables);
        return mapToJackstomp(futureSession);
    }

    /**
     * An overloaded version of {@link #connect(String, WebSocketHttpHeaders, StompSessionHandler, Object...)} that
     * simplifies the session handler declaration.
     *
     * @param url
     *         the url to connect to
     * @param handshakeHeaders
     *         the headers for the WebSocket handshake
     * @param handler
     *         the session handler
     * @param uriVars
     *         URI variables to expand into the URL
     *
     * @return ListenableFuture for access to the session when ready for use
     */
    public ListenableFuture<JackstompSession> connect(String url, WebSocketHttpHeaders handshakeHeaders,
            BiConsumer<StompSession, StompHeaders> handler, Object... uriVars) {
        LoggingStompSessionHandler sessionHandler = new LoggingStompSessionHandler(handler);
        return connect(url, handshakeHeaders, sessionHandler, uriVars);
    }

    /**
     * An overloaded version of {@link #connect(String, WebSocketHttpHeaders, StompSessionHandler, Object...)} that
     * simplifies the session handler declaration.
     *
     * @param url
     *         the url to connect to
     * @param handshakeHeaders
     *         the headers for the WebSocket handshake
     * @param handler
     *         the session handler
     * @param uriVars
     *         URI variables to expand into the URL
     *
     * @return ListenableFuture for access to the session when ready for use
     */
    public ListenableFuture<JackstompSession> connect(String url, WebSocketHttpHeaders handshakeHeaders,
            Consumer<StompSession> handler, Object... uriVars) {
        BiConsumer<StompSession, StompHeaders> afterConnected = (session, headers) -> handler.accept(session);
        return connect(url, handshakeHeaders, afterConnected, uriVars);
    }

    /**
     * An overloaded version of {@link #connect(String, StompSessionHandler, Object...)} that also accepts {@link
     * WebSocketHttpHeaders} to use for the WebSocket handshake and {@link StompHeaders} for the STOMP CONNECT frame.
     *
     * @param url
     *         the url to connect to
     * @param handshakeHeaders
     *         headers for the WebSocket handshake
     * @param connectHeaders
     *         headers for the STOMP CONNECT frame
     * @param handler
     *         the session handler
     * @param uriVariables
     *         URI variables to expand into the URL
     *
     * @return ListenableFuture for access to the session when ready for use
     */
    public ListenableFuture<JackstompSession> connect(String url, WebSocketHttpHeaders handshakeHeaders,
            StompHeaders connectHeaders, StompSessionHandler handler, Object... uriVariables) {
        return mapToJackstomp(client.connect(url, handshakeHeaders, connectHeaders, handler, uriVariables));
    }

    /**
     * An overloaded version of {@link #connect(String, WebSocketHttpHeaders, StompSessionHandler, Object...)} that
     * accepts a fully prepared {@link java.net.URI}.
     *
     * @param url
     *         the url to connect to
     * @param handshakeHeaders
     *         the headers for the WebSocket handshake
     * @param connectHeaders
     *         headers for the STOMP CONNECT frame
     * @param sessionHandler
     *         the STOMP session handler
     *
     * @return ListenableFuture for access to the session when ready for use
     */
    public ListenableFuture<JackstompSession> connect(URI url, WebSocketHttpHeaders handshakeHeaders,
            StompHeaders connectHeaders, StompSessionHandler sessionHandler) {
        return mapToJackstomp(client.connect(url, handshakeHeaders, connectHeaders, sessionHandler));
    }

    /**
     * Connects synchronously to the given URL. Uses a default timeout of {@value #DEFAULT_TIMEOUT_SEC} seconds.
     *
     * @param url
     *         the URL to connect to
     * @param uriVars
     *         URI variables to expand into the URL
     *
     * @return a new {@link JackstompSession} for the created connection
     * @throws InterruptedException
     *         if the current thread was interrupted while waiting for the connection
     * @throws ExecutionException
     *         if an exception was thrown while connecting
     * @throws TimeoutException
     *         if the connection took too long to establish
     */
    public JackstompSession syncConnect(String url, Object... uriVars)
            throws InterruptedException, ExecutionException, TimeoutException {
        return syncConnect(url, DEFAULT_TIMEOUT_SEC, TimeUnit.SECONDS, uriVars);
    }

    /**
     * Connects synchronously to the given URL. Uses a default timeout of {@value #DEFAULT_TIMEOUT_SEC} seconds.
     *
     * @param url
     *         the URL to connect to
     * @param timeout
     *         the maximum time to wait for the connection
     * @param unit
     *         the time unit of the timeout argument
     * @param uriVars
     *         URI variables to expand into the URL
     *
     * @return a new {@link JackstompSession} for the created connection
     * @throws InterruptedException
     *         if the current thread was interrupted while waiting for the connection
     * @throws ExecutionException
     *         if an exception was thrown while connecting
     * @throws TimeoutException
     *         if the connection took too long to establish
     */
    public JackstompSession syncConnect(String url, long timeout, TimeUnit unit, Object... uriVars)
            throws InterruptedException, ExecutionException, TimeoutException {
        ListenableFuture<StompSession> futureSession = client.connect(url, new LoggingStompSessionHandler(), uriVars);
        StompSession session = futureSession.get(timeout, unit);
        return mapToJackstomp(session);
    }

    private static ListenableFutureAdapter<JackstompSession, StompSession> mapToJackstomp(
            ListenableFuture<StompSession> futureSession) {
        return new ListenableFutureAdapter<JackstompSession, StompSession>(futureSession) {
            @Override
            protected JackstompSession adapt(StompSession session) throws ExecutionException {
                return mapToJackstomp(session);
            }
        };
    }

    private static JackstompSession mapToJackstomp(StompSession session) {
        session.setAutoReceipt(true);
        return new JackstompSession(session);
    }

    @Override
    public void start() {
        client.start();
    }

    @Override
    public void stop() {
        client.stop();
    }

    @Override
    public void stop(Runnable callback) {
        client.stop(callback);
    }

    @Override
    public boolean isRunning() {
        return client.isRunning();
    }

    @Override
    public boolean isAutoStartup() {
        return client.isAutoStartup();
    }

    @Override
    public int getPhase() {
        return client.getPhase();
    }
}
