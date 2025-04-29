package io.github.jimzhouzzy.klotski.server;

public class GameSave {
    private String username;
    private String date; // ISO 8601 format
    private String saveData; // JSON string of the save data
    private boolean autoSave;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getSaveData() {
        return saveData;
    }

    public void setSaveData(String saveData) {
        this.saveData = saveData;
    }

    public void setAutoSave(boolean autoSave) {
        this.autoSave = autoSave;
    }

    public boolean getAutoSave() {
        return autoSave;
    }
}