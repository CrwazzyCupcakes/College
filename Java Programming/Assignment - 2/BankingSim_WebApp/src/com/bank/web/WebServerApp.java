package com.bank.web;

import com.bank.service.BankService;
import com.bank.service.InterestScheduler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

/**
 * Starts the web version of the bank.
 * Usage: java com.bank.web.WebServerApp [port] [webFolder]
 */
public class WebServerApp {
    private static final long INTEREST_INTERVAL_MS = 10_000; // 10 seconds = 1 "month" in the demo

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        Path webRoot = Paths.get(args.length > 1 ? args[1] : "web");
        if (!Files.isDirectory(webRoot)) {
            System.out.println("Cannot find the web folder: " + webRoot.toAbsolutePath());
            System.out.println("Run the server from the project folder (the one that contains 'web').");
            return;
        }

        final BankService bank = new BankService();
        final InterestScheduler scheduler = new InterestScheduler(bank, INTEREST_INTERVAL_MS);
        SessionManager sessions = new SessionManager();

        // Loopback only: the demo uses plain HTTP, so it is reachable from this computer only.
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext("/api/", new ApiHandler(bank, sessions, scheduler));
        server.createContext("/", new StaticFileHandler(webRoot));
        server.setExecutor(Executors.newFixedThreadPool(8)); // 8 requests can be served at the same time
        server.start();

        // Save everything when the server is stopped with Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                scheduler.stop();
                bank.save();
                System.out.println("Data saved. Server stopped.");
            }
        }));

        System.out.println("==========================================");
        System.out.println("  Banking web app running");
        System.out.println("  Open:  http://localhost:" + port);
        System.out.println("  Stop:  press Ctrl+C");
        System.out.println("==========================================");
    }
}
