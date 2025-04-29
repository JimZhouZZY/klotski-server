package io.github.jimzhouzzy.klotski.server;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.google.gson.Gson;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameSaveHandler implements HttpHandler {
    private static final String SAVE_DIRECTORY = "gameSaves";
    private static final Gson gson = new Gson();
    private static final Map<String, List<GameSave>> userSaves = new ConcurrentHashMap<>();

    static {
        // Ensure the save directory exists
        File saveDir = new File(SAVE_DIRECTORY);
        if (!saveDir.exists()) {
            saveDir.mkdir();
        }

        // Load existing saves from disk
        for (File userDir : saveDir.listFiles()) {
            if (userDir.isDirectory()) {
                String username = userDir.getName();
                List<GameSave> saves = new ArrayList<>();
                for (File saveFile : userDir.listFiles()) {
                    try (FileReader reader = new FileReader(saveFile)) {
                        GameSave save = gson.fromJson(reader, GameSave.class);
                        saves.add(save);
                    } catch (IOException e) {
                        System.err.println("Failed to load save file: " + saveFile.getName());
                    }
                }
                userSaves.put(username, saves);
            }
        }
        System.out.println("Loaded user saves from disk: " + userSaves);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if ("POST".equals(method) && path.equals("/gameSave/uploadSave")) {
            handleUploadSave(exchange);
        } else if ("GET".equals(method) && path.equals("/gameSave/getSaves")) {
            handleGetSaves(exchange);
        } else {
            exchange.sendResponseHeaders(404, -1); // Not Found
        }
    }

    private void handleUploadSave(HttpExchange exchange) throws IOException {
        // Parse request body
        String requestBody = new String(exchange.getRequestBody().readAllBytes());
        GameSave save = gson.fromJson(requestBody, GameSave.class);

        // Validate save
        if (!validateSave(save)) {
            String response = gson.toJson(Map.of("code", 400, "message", "Invalid save data"));
            exchange.sendResponseHeaders(400, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
            return;
        }

        boolean autoSave = save.getAutoSave();
        String saveFileName;

        // Save the game save
        String userDir = SAVE_DIRECTORY + "/" + save.getUsername();
        File userDirFile = new File(userDir);
        if (!userDirFile.exists()) {
            userDirFile.mkdir();
        }

        if (autoSave) {
            // Delete previous autosave if it exists
            File[] files = userDirFile.listFiles((dir, name) -> name.startsWith("Autosave-"));
            if (files != null) {
                for (File file : files) {
                    file.delete();
                    System.out.println("Deleted previous autosave: " + file.getName());
                }
            }

            saveFileName = userDir + "/" + "Autosave-" + save.getDate() + ".json";
            System.out.println("Autosave uploaded for user: " + save.getUsername());
        } else {
            saveFileName = userDir + "/" + save.getDate() + ".json";

            // Log the save
            List<GameSave> saves = userSaves.computeIfAbsent(save.getUsername(), k -> new ArrayList<>());
            saves.add(save);

            // Limit to 10 saves for non-autosave
            if (saves.size() > 3) {
                saves.sort(Comparator.comparing(GameSave::getDate));
                GameSave oldestSave = saves.remove(0);

                // Delete the corresponding file from disk
                String oldestSaveFileName = userDir + "/" + oldestSave.getDate() + ".json";
                File oldestSaveFile = new File(oldestSaveFileName);
                if (oldestSaveFile.exists()) {
                    oldestSaveFile.delete();
                }
            }
        }

        try (FileWriter writer = new FileWriter(saveFileName)) {
            gson.toJson(save, writer);
        }

        // Respond to the client
        String response = gson.toJson(Map.of("code", 200, "message", "Save uploaded successfully"));
        exchange.sendResponseHeaders(200, response.length());
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();

        System.out.println("Game saved for user: " + save.getUsername());
    }

    private void handleGetSaves(HttpExchange exchange) throws IOException {
        // Parse query parameters
        String query = exchange.getRequestURI().getQuery();
        Map<String, String> params = parseQuery(query);
        String username = params.get("username");
        System.out.println("Query parameters: " + params);
        System.out.println("Username: " + username);
        System.out.println("User saves: " + userSaves);
        System.out.println("User saves for " + username + ": " + userSaves.get(username));
        System.out.println(userSaves.containsKey(username));

        if (username == null || !userSaves.containsKey(username)) {
            String response = gson.toJson(Map.of("code", 404, "message", "No saves found for user"));
            exchange.sendResponseHeaders(404, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
            System.out.println("No saves for user: " + username);
            return;
        }

        // Retrieve saves
        List<GameSave> saves = userSaves.get(username);
        saves.sort(Comparator.comparing(GameSave::getDate).reversed()); // Sort by date (latest first)
        System.out.println("Saves for user " + username + ": " + saves);

        // Respond with saves
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        String response = gson.toJson(Map.of("code", 200, "saves", saves));
        byte[] responseBytes = response.getBytes("UTF-8");
        exchange.sendResponseHeaders(200, responseBytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(responseBytes);
        os.close();

        System.out.println("Saves retrieved for user: " + username);
    }

    private boolean validateSave(GameSave save) {
        // NOT IMPLEMENTED YET
        return true;
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query != null) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    params.put(keyValue[0], keyValue[1]);
                }
            }
        }
        return params;
    }
}