package io.github.jimzhouzzy.klotski.server;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.java_websocket.WebSocket;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class LoginServer {
    private static final String USER_DATABASE_FILE = "userDatabase.json";
    public static final Map<String, String> userDatabase = new ConcurrentHashMap<>();
    private static final Gson gson = new Gson();
    private static GameWebSocketServer gameWebSocketServer;
    private static final Map<String, TokenInfo> tokenDatabase = new ConcurrentHashMap<>();

    private static class TokenInfo {
        String username;
        long expiryTime;

        TokenInfo(String username, long expiryTime) {
            this.username = username;
            this.expiryTime = expiryTime;
        }
    }

    public static void main(String[] args) throws IOException {
        // Load user database from JSON file
        loadUserDatabase();

        // Create WebSocket server
        gameWebSocketServer = new GameWebSocketServer(8002);
        gameWebSocketServer.start();

        // Create HTTP server
        HttpServer server = HttpServer.create(new InetSocketAddress(8001), 0);
        server.createContext("/login", new LoginHandler());
        server.createContext("/signup", new SignupHandler());
        server.createContext("/gameSave", new GameSaveHandler());
        server.setExecutor(null); // Use default executor
        server.start();
        System.out.println("Server started on port 8001");

        // Create WebServer for serving static files
        WebServer webServer = new WebServer(8013);
        
        // Add shutdown hook to clean up resources
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down servers...");

            // Stop the WebSocket server
            if (gameWebSocketServer != null) {
                gameWebSocketServer.close();
            }

            // Stop the HTTP server
            if (server != null) {
                server.stop(0);
            }

            // Stop the WebServer
            if (webServer != null) {
                webServer.close();
            }

            System.out.println("All servers shut down successfully.");
        }));
    }

    private static void loadUserDatabase() {
        try (FileReader reader = new FileReader(USER_DATABASE_FILE)) {
            Map<String, String> data = gson.fromJson(reader, new TypeToken<Map<String, String>>() {}.getType());
            if (data != null) {
                userDatabase.putAll(data);
            }
        } catch (IOException e) {
            System.out.println("No existing user database found. Starting fresh.");
        }
    }

    private static void saveUserDatabase() {
        try (FileWriter writer = new FileWriter(USER_DATABASE_FILE)) {
            gson.toJson(userDatabase, writer);
        } catch (IOException e) {
            System.err.println("Failed to save user database: " + e.getMessage());
        }
    }

    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                // Parse request body
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                String[] parts = requestBody.split("&");
                String username = null, password = null, token = null;
                for (String part : parts) {
                    String[] keyValue = part.split("=");
                    if (keyValue[0].equals("username")) {
                        username = keyValue[1];
                    } else if (keyValue[0].equals("password")) {
                        password = keyValue[1];
                    } else if (keyValue[0].equals("token")) {
                        token = keyValue[1];
                    }
                }
    
                String response;
                if (token != null) {
                    // Token-based login
                    String validatedUsername = validateToken(token);
                    if (validatedUsername != null) {
                        response = "success:" + token;
                        System.out.println("Token login successful for user: " + validatedUsername);
                    } else {
                        response = "failure: invalid or expired token";
                    }
                } else {
                    // Username/password login
                    if (!basicValidation(username, password)) {
                        response = "failure: invalid input";
                    } else {
                        boolean success = userDatabase.containsKey(username) && userDatabase.get(username).equals(password);
                        if (success) {
                            // Generate token
                            String newToken = generateToken();
                            long expiryTime = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000; // 1 month
                            tokenDatabase.put(newToken, new TokenInfo(username, expiryTime));
                            response = "success:" + newToken;
                            System.out.println("User " + username + " logged in with token: " + newToken);
                        } else {
                            response = "failure";
                        }
                    }
                }
    
                // Send response
                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } else {
                exchange.sendResponseHeaders(405, -1); // Method not allowed
            }
        }
    }

    static class SignupHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                // Parse request body
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                String[] parts = requestBody.split("&");
                String username = null, password = null;
                for (String part : parts) {
                    String[] keyValue = part.split("=");
                    if (keyValue[0].equals("username")) {
                        username = keyValue[1];
                    } else if (keyValue[0].equals("password")) {
                        password = keyValue[1];
                    }
                }

                // Perform basic validation
                String response;
                if (!basicValidation(username, password)) {
                    response = "failure: invalid input";
                } else if (userDatabase.containsKey(username)) {
                    // Check if user already exists
                    response = "failure: user already exists";
                } else {
                    // Add new user and save to database
                    userDatabase.put(username, password);
                    saveUserDatabase();
                    response = "success";
                }

                // Send response
                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } else {
                exchange.sendResponseHeaders(405, -1); // Method not allowed
            }
        }
    }

    private static boolean basicValidation(String username, String password) {
        // Check if the username is valid (not empty)
        if (username == null || username.isEmpty()) {
            return false;
        }
        // Check if the password is valid (not empty)
        if (password == null || password.isEmpty()) {
            return false;
        }
        // Check if the username is too long
        if (username.length() > 20) {
            return false;
        }
        // Check if the password is too long
        if (password.length() > 20) {
            return false;
        }
        // Check if the username contains invalid characters
        if (!username.matches("[a-zA-Z0-9_]+")) {
            return false;
        }

        return true;
    }

    private static String generateToken() {
        return java.util.UUID.randomUUID().toString();
    }

    private static String validateToken(String token) {
        TokenInfo tokenInfo = tokenDatabase.get(token);
        if (tokenInfo != null && tokenInfo.expiryTime > System.currentTimeMillis()) {
            return tokenInfo.username; // 返回用户名
        }
        return null; // Token 无效或已过期
    }
}
