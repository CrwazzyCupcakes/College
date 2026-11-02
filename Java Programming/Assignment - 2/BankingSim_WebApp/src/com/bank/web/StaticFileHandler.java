package com.bank.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Serves the HTML / CSS / JS files from the web/ folder (NIO Path + Files). */
public class StaticFileHandler implements HttpHandler {
    private final Path root;

    public StaticFileHandler(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String requestPath = exchange.getRequestURI().getPath();
            if (requestPath.equals("/")) {
                requestPath = "/index.html";
            }
            Path file = root.resolve(requestPath.substring(1)).normalize();

            // block paths like /../../secret that try to escape the web folder
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                byte[] msg = "404 - Not found".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                exchange.sendResponseHeaders(404, msg.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(msg);
                }
                return;
            }

            byte[] data = Files.readAllBytes(file);
            exchange.getResponseHeaders().set("Content-Type", contentType(file.getFileName().toString()));
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        } finally {
            exchange.close();
        }
    }

    private String contentType(String name) {
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".ico")) return "image/x-icon";
        if (name.endsWith(".json")) return "application/json; charset=utf-8";
        return "application/octet-stream";
    }
}
