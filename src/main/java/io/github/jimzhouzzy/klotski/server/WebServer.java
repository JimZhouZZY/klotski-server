package io.github.jimzhouzzy.klotski.server;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class WebServer {
    private final HttpServer server;

    public WebServer(int port) throws IOException {
        // Ensure the "web" folder exists
        ensureWebFolder();

        server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);

        // Serve static files from the "web" directory
        server.createContext("/", this::handleRequest);

        server.start();
        System.out.println("HTTP server started on http://0.0.0.0:" + port);
    }

    private void ensureWebFolder() throws IOException {
        File webDir = new File("web");
        if (!webDir.exists()) {
            System.out.println("Web directory not found. Extracting from JAR...");
            extractWebFolderFromJar();
        } else {
            System.out.println("Web directory already exists.");
        }
    }

    private void extractWebFolderFromJar() throws IOException {
        try (InputStream jarStream = getClass().getProtectionDomain().getCodeSource().getLocation().openStream();
             ZipInputStream zipInputStream = new ZipInputStream(jarStream)) {

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entryName.startsWith("web/")) {
                    File file = new File(entryName);
                    if (entry.isDirectory()) {
                        file.mkdirs();
                    } else {
                        file.getParentFile().mkdirs();
                        try (OutputStream outputStream = new FileOutputStream(file)) {
                            byte[] buffer = new byte[1024];
                            int bytesRead;
                            while ((bytesRead = zipInputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, bytesRead);
                            }
                        }
                    }
                }
            }
        }
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        String uriPath = exchange.getRequestURI().getPath();
        String username = null;

        // Check if the path is for a specific user
        if (uriPath.startsWith("/") && !uriPath.equals("/")) {
            username = uriPath.substring(1); // Extract username from URI
            if (!username.isEmpty()) {
                System.out.println("Serving content for user: " + username);
                uriPath = "/index.html"; // Default to index.html
            }
        }

        if (uriPath.equals("/")) {
            uriPath = "/index.html"; // Default to index.html
        }

        String filePath = "web" + uriPath; // Map URI to file path
        File file = new File(filePath);

        System.out.println("Requested file path: " + filePath);

        if (file.exists() && !file.isDirectory()) {
            String contentType = Files.probeContentType(Paths.get(filePath));
            if (contentType == null) {
                contentType = "application/octet-stream"; // Default content type
            }

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, file.length());

            try (OutputStream os = exchange.getResponseBody();
                 FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
        } else {
            String errorMessage = "404 - File Not Found";
            exchange.sendResponseHeaders(404, errorMessage.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(errorMessage.getBytes());
            }
        }
    }

    public void close() {
        server.stop(0);
    }

    public static void main(String[] args) {
        // For testing purposes only
        try {
            int port = 8013;
            new WebServer(port);  // Create an instance of WebServer to start the server
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}