package ru.nsu.fitkulin;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Client {

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: java ru.nsu.fitkulin.Client <host> <port> <name>");
            System.exit(1);
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String name = args[2];

        for (char c : name.toCharArray()) {
            if (c > 0x7F) {
                System.err.println("Error: name must contain only ASCII characters");
                System.exit(2);
            }
        }

        try (Socket socket = new Socket(host, port)) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            out.write(name.getBytes(StandardCharsets.US_ASCII));
            out.write(0);
            out.flush();

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.US_ASCII))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line).append('\n');
                }
            }

            String data = response.toString();

            // parse PEM blocks
            String privBegin = "-----BEGIN PRIVATE KEY-----";
            String privEnd   = "-----END PRIVATE KEY-----";
            String pubBegin  = "-----BEGIN PUBLIC KEY-----";
            String pubEnd    = "-----END PUBLIC KEY-----";
            String certBegin = "-----BEGIN CERTIFICATE-----";
            String certEnd   = "-----END CERTIFICATE-----";

            int privStart = data.indexOf(privBegin);
            int privStop  = data.indexOf(privEnd);
            int pubStart  = data.indexOf(pubBegin);
            int pubStop   = data.indexOf(pubEnd);
            int certStart = data.indexOf(certBegin);
            int certStop  = data.indexOf(certEnd);

            if (privStart < 0 || privStop < 0 || pubStart < 0 || pubStop < 0 || certStart < 0 || certStop < 0) {
                throw new IOException("Invalid response from server: missing PEM blocks");
            }

            String privPem = data.substring(privStart, privStop + privEnd.length()).trim() + "\n";
            String pubPem  = data.substring(pubStart,  pubStop  + pubEnd.length()).trim() + "\n";
            String certPem = data.substring(certStart, certStop + certEnd.length()).trim() + "\n";

            writeFile(name + ".key", privPem);
            writeFile(name + ".pub", pubPem);
            writeFile(name + ".crt", certPem);

            System.out.println("✅ Saved:");
            System.out.println("  " + name + ".key");
            System.out.println("  " + name + ".pub");
            System.out.println("  " + name + ".crt");

        } catch (Exception e) {
            System.err.println("Client error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void writeFile(String filename, String content) throws IOException {
        try (FileWriter writer = new FileWriter(filename)) {
            writer.write(content);
        }
    }
}
