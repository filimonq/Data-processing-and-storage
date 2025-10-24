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
import java.util.concurrent.atomic.AtomicInteger;

public class WebSpider {
    private final HttpClient client;
    private final Set<String> visitedUrls;
    private final List<String> messages;
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final ExecutorService virtualThreadExecutor;
    private final AtomicInteger activeTasks;
    private final Phaser phaser;

    // Record для парсинга JSON ответа
    public record ServerResponse(
            String message,
            @JsonProperty("successors") List<String> successors
    ) {}

    public WebSpider(String baseUrl) {
        this.baseUrl = baseUrl;
        this.visitedUrls = ConcurrentHashMap.newKeySet();
        this.messages = Collections.synchronizedList(new ArrayList<>());
        this.objectMapper = new ObjectMapper();
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.activeTasks = new AtomicInteger(0);
        this.phaser = new Phaser(1); // Регистрируем главный поток

        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .executor(virtualThreadExecutor)
                .build();
    }

    public CompletableFuture<Void> crawlAsync(String path) {
        if (!visitedUrls.add(path)) {
            return CompletableFuture.completedFuture(null);
        }

        phaser.register(); // Регистрируем новую задачу
        activeTasks.incrementAndGet();

        return CompletableFuture
                .supplyAsync(() -> fetchUrl(path), virtualThreadExecutor)
                .thenCompose(response -> {
                    if (response != null) {
                        synchronized (messages) {
                            messages.add(response.message());
                        }

                        // Асинхронно обходим всех потомков
                        List<CompletableFuture<Void>> futures = new ArrayList<>();
                        for (String successor : response.successors()) {
                            futures.add(crawlAsync(successor));
                        }

                        if (futures.isEmpty()) {
                            return CompletableFuture.completedFuture(null);
                        }

                        return CompletableFuture.allOf(
                                futures.toArray(new CompletableFuture[0])
                        );
                    }
                    return CompletableFuture.completedFuture(null);
                })
                .whenComplete((result, throwable) -> {
                    activeTasks.decrementAndGet();
                    phaser.arriveAndDeregister(); // Задача завершена

                    if (throwable != null) {
                        System.err.println("Error processing " + path + ": " + throwable.getMessage());
                    }
                });
    }

    private ServerResponse fetchUrl(String path) {
        try {
            if (!path.startsWith("/")) {
                path = "/" + path;
            }

            String fullUrl = baseUrl + path;
            System.out.println("Fetching URL: " + fullUrl);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), ServerResponse.class);
            } else {
                System.err.println("HTTP " + response.statusCode() + " for URL: " + fullUrl);
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch " + path + ": " + e.getMessage());
        }
        return null;
    }


    public List<String> getSortedMessages() {
        return messages.stream()
                .sorted()
                .toList();
    }

    public void shutdown() {
        virtualThreadExecutor.shutdown();
    }

    // Метод для ожидания завершения всех задач
    public void waitForCompletion() {
        phaser.arriveAndAwaitAdvance(); // Ждем завершения всех зарегистрированных задач
    }

    public static void main(String[] args) {
        String studentId = "FitkulinIldar"; // Замени на свои данные
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        String baseUrl = "http://localhost:" + port;

        System.out.println("Starting WebSpider for student: " + studentId);
        System.out.println("Server URL: " + baseUrl);

        WebSpider spider = new WebSpider(baseUrl);
        long startTime = System.currentTimeMillis();

        try {
            // Запускаем обход с корня
            CompletableFuture<Void> crawlFuture = spider.crawlAsync("/");

            // Ждем завершения через Phaser
            spider.waitForCompletion();

            // Альтернативно можно использовать join
            crawlFuture.join();

            List<String> result = spider.getSortedMessages();

            System.out.println("\n=== COLLECTED MESSAGES ===");
            result.forEach(System.out::println);

            System.out.println("\n=== STATISTICS ===");
            System.out.println("Total messages: " + result.size());
            System.out.println("Visited URLs: " + spider.visitedUrls.size());
            System.out.println("Execution time: " +
                    (System.currentTimeMillis() - startTime) + "ms");

        } catch (Exception e) {
            System.err.println("Crawling failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            spider.shutdown();
        }
    }
}