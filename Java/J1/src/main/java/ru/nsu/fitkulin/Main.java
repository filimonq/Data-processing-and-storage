package ru.nsu.fitkulin;

/**
 * Запуск сервера:
 * Usage: java ru.nsu.fitkulin.Main <port> <generatorThreads>
 *
 * Пример: java ru.nsu.fitkulin.Main 8888 4
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java ru.nsu.fitkulin.Main <port> <generatorThreads>");
            System.exit(1);
        }

        int port;
        int threads;
        try {
            port = Integer.parseInt(args[0]);
            threads = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("Port and generatorThreads must be integers.");
            System.exit(2);
            return;
        }

        try {
            Server server = new Server(port, threads);
            server.start();
        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
            System.exit(3);
        }
    }
}
