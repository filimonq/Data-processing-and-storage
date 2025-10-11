package ru.nsu.fitkulin.DTO;

import java.util.concurrent.CompletableFuture;

public record Task(String name, CompletableFuture<ClientKey> client) {
}

