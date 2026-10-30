package com.mealmind.llm;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A fake LLM provider on the loopback interface (127.0.0.1, random port). It records every request
 * and answers with scripted responses, so client tests never touch a real provider.
 */
final class FakeLlmServer implements AutoCloseable {

    /** A recorded request. Header names are lowercase. */
    record Request(String method, String path, Map<String, List<String>> headers, String body) {
        String header(String name) {
            List<String> values = headers.get(name.toLowerCase());
            return values == null || values.isEmpty() ? null : values.get(0);
        }
    }

    /** A scripted response. */
    record Response(int status, String body, long delayMillis, Map<String, String> headers) {
        static Response json(int status, String body) {
            return new Response(status, body, 0, Map.of("Content-Type", "application/json"));
        }
    }

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "fake-llm-server");
        thread.setDaemon(true);
        return thread;
    });
    private final Deque<Response> script = new ArrayDeque<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private volatile Response fallback = Response.json(500, "{\"error\":{\"message\":\"no scripted response\"}}");

    FakeLlmServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            Map<String, List<String>> headers = new java.util.HashMap<>();
            Headers requestHeaders = exchange.getRequestHeaders();
            requestHeaders.forEach((name, values) -> headers.put(name.toLowerCase(), List.copyOf(values)));
            requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    Map.copyOf(headers), new String(body, StandardCharsets.UTF_8)));
            Response response = next();
            try {
                if (response.delayMillis() > 0) {
                    Thread.sleep(response.delayMillis());
                }
                response.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
                byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(response.status(), bytes.length == 0 ? -1 : bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                // The client gave up (e.g. after a timeout); nothing to do.
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    private synchronized Response next() {
        return script.isEmpty() ? fallback : script.poll();
    }

    /** Queues a response to send to the next request. */
    synchronized FakeLlmServer enqueue(Response response) {
        script.add(response);
        return this;
    }

    /** Queues a JSON response. */
    FakeLlmServer enqueueJson(int status, String body) {
        return enqueue(Response.json(status, body));
    }

    /** The base URL, e.g. {@code http://127.0.0.1:54321}. */
    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    List<Request> requests() {
        return List.copyOf(requests);
    }

    Request lastRequest() {
        return requests.get(requests.size() - 1);
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
