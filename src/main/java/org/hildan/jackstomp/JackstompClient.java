package org.hildan.jackstomp;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.context.SmartLifecycle;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

/**
 * A simple wrapper around a {@link WebSocketStompClient} which preconfigures websocket transports, a Jackson
 * converter for messages, and allows for the creation of {@link JackstompSession}s.
 */
public class JackstompClient implements SmartLifecycle {

    /**
     * The default timeout for new server connections.
     */
    private static final long DEFAULT_TIMEOUT_SEC = 15;

    private final WebSocketStompClient client;

    /**
     * Creates a {@code JackstompClient} with convenient defaults. It creates a pre-configured
     * {@link WebSocketStompClient} using a Spring {@link SockJsClient} and a Jackson message converter.
     * <p>
     * Only the {@link WebSocketTransport} is used for the {@link SockJsClient}. For more transports options, use
     * {@link #JackstompClient(List)}.
     */
    public JackstompClient() {
        this(createWsTransports());
    }

    /**
     * Creates a {@code JackstompClient} with convenient defaults. It creates a pre-configured
     * {@link WebSocketStompClient} using a Spring {@link SockJsClient} on the given transports and a Jackson message
     * converter.
     *
     * @param transports
     *         the transports to use for the inner {@link SockJsClient}
     */
    public JackstompClient(List<Transport> transports) {
        this(createWebSocketClient(transports));
    }

    /**
     * Creates a {@code JackstompClient} with convenient defaults. It creates a pre-configured
     * {@link WebSocketStompClient} using the given {@link WebSocketClient} and a Jackson message converter.
     *
     * @param webSocketClient
     *         the {@link WebSocketClient} to use from the {@link WebSocketStompClient}
     */
    public JackstompClient(WebSocketClient webSocketClient) {
        this(createStompClient(webSocketClient));
    }

    /**
     * Creates a {@code JackstompClient} based on the given {@link WebSocketStompClient}. This allows you to
     * configure as you please the actual client used by Jackstomp.
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
     * Connects synchronously to the given URL. Uses a default timeout of {@value #DEFAULT_TIMEOUT_SEC} seconds.
     *
     * @param url
     *         the URL to connect to
     *
     * @return a new {@link JackstompSession} for the created connection
     *
     * @throws InterruptedException
     *         if the current thread was interrupted while waiting for the connection
     * @throws ExecutionException
     *         if an exception was thrown while connecting
     * @throws TimeoutException
     *         if the connection took too long to establish
     */
    public JackstompSession connect(String url) throws InterruptedException, ExecutionException, TimeoutException {
        return connect(url, DEFAULT_TIMEOUT_SEC, TimeUnit.SECONDS);
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
     *
     * @return a new {@link JackstompSession} for the created connection
     *
     * @throws InterruptedException
     *         if the current thread was interrupted while waiting for the connection
     * @throws ExecutionException
     *         if an exception was thrown while connecting
     * @throws TimeoutException
     *         if the connection took too long to establish
     */
    public JackstompSession connect(String url, long timeout, TimeUnit unit) throws InterruptedException,
            ExecutionException, TimeoutException {
        StompSession session = client.connect(url, new LoggingStompSessionHandler()).get(timeout, unit);
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
