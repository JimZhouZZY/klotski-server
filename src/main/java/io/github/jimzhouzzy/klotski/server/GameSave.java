package io.github.jimzhouzzy.klotski.server;

public class GameSave {
    private String username;
    private String date; // ISO 8601 format (e.g., "2025-04-28T12:34:56Z")
    private String hash; // Hash to validate the save
    private String saveData; // JSON string of the save data

    public String getUsername() {
        return username;
    }

    public String getDate() {
        return date;
    }

    public String getHash() {
        return hash;
    }

    public String getSaveData() {
        return saveData;
    }
}