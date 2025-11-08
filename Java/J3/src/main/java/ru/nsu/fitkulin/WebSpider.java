package ru.nsu.fitkulin;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public class WebSpider {
    private final HttpClient client;
    private final Set<String> visitedUrls;
    private final Queue<String> messages;
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final ExecutorService virtualThreadExecutor;
    private final Phaser phaser;

    public record ServerResponse(
            String message,
            @JsonProperty("successors") List<String> successors
    ) {}

    public WebSpider(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        this.visitedUrls = ConcurrentHashMap.newKeySet();
        this.messages = new ConcurrentLinkedQueue<>();
        this.objectMapper = new ObjectMapper();
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.phaser = new Phaser(1);

        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    private static String normalizePath(String p) {
        if (p == null || p.isBlank() || "/".equals(p)) return "/";
        String withLeading = p.startsWith("/") ? p : "/" + p;
        return withLeading.replaceAll("/{2,}", "/");
    }

    public CompletableFuture<Void> crawlAsync(String rawPath) {
        final String path = normalizePath(rawPath);

        if (!visitedUrls.add(path)) {
            return CompletableFuture.completedFuture(null);
        }

        phaser.register();

        return CompletableFuture
                .supplyAsync(() -> fetchUrl(path), virtualThreadExecutor)
                .thenCompose(response -> {
                    if (response == null) return CompletableFuture.completedFuture(null);

                    if (response.message() != null) {
                        messages.add(response.message());
                    }

                    List<String> succ = response.successors();
                    if (succ == null || succ.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }

                    List<CompletableFuture<Void>> futures = new ArrayList<>(succ.size());
                    for (String s : succ) {
                        futures.add(crawlAsync(s));
                    }
                    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                })
                .whenComplete((r, t) -> {
                    phaser.arriveAndDeregister();
                    if (t != null) {
                        System.err.println("Error processing " + path + ": " + t.getMessage());
                    }
                });
    }

    private ServerResponse fetchUrl(String rawPath) {
        try {
            String path = normalizePath(rawPath);
            String fullUrl = baseUrl + path;
            System.out.println("Fetching URL: " + fullUrl);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("HTTP " + response.statusCode() + " for URL: " + fullUrl);
                return null;
            }
            return objectMapper.readValue(response.body(), ServerResponse.class);

        } catch (Exception e) {
            System.err.println("Failed to fetch " + rawPath + ": " + e.getMessage());
            return null;
        }
    }

    public List<String> getSortedMessages() {
        return messages.stream()
                .filter(Objects::nonNull)
                .sorted()
                .toList();
    }

    public void waitForCompletion() {
        phaser.arriveAndAwaitAdvance();
    }

    public void shutdown() {
        virtualThreadExecutor.shutdown();
    }

    public static void main(String[] args) {
        String studentId = "FitkulinIldar";
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        String baseUrl = "http://localhost:" + port;

        System.out.println("Starting WebSpider for student: " + studentId);
        System.out.println("Server URL: " + baseUrl);

        WebSpider spider = new WebSpider(baseUrl);
        long startTime = System.currentTimeMillis();

        try {
            spider.crawlAsync("/");
            spider.waitForCompletion();

            List<String> result = spider.getSortedMessages();

            System.out.println("\n=== COLLECTED MESSAGES ===");
            result.forEach(System.out::println);

            System.out.println("\n=== STATISTICS ===");
            System.out.println("Total messages: " + result.size());
            System.out.println("Visited URLs: " + spider.visitedUrls.size());
            System.out.println("Execution time: " + (System.currentTimeMillis() - startTime) + " ms");

        } finally {
            spider.shutdown();
        }
    }
}
