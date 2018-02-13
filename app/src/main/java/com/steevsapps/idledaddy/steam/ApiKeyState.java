package com.steevsapps.idledaddy.steam;

public enum ApiKeyState {
    REGISTERED(1),
    UNREGISTERED(2),
    ACCESS_DENIED(-1),
    ERROR(-2);

    private final int levelCode;
    private String apiKey;

    ApiKeyState(int levelCode) {
        this.levelCode = levelCode;
    }

    public int getLevelCode() {
        return levelCode;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiKey() {
        return apiKey;
    }
}