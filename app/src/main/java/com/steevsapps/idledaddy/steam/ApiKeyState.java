package com.steevsapps.idledaddy.steam;

public enum ApiKeyState {
    REGISTERED,
    UNREGISTERED,
    ACCESS_DENIED,
    ERROR;

    private String apiKey;

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiKey() {
        return apiKey;
    }
}
