package com.cognex.insight.cogsocket;

import com.google.gson.JsonElement;

public class CogSocketMessage {
    public String type;
    public int id;
    public int error;
    public String path;
    public JsonElement body;

    public CogSocketMessage() {
    }

    public CogSocketMessage(String type, int id, String path, JsonElement body) {
        this.type = type;
        this.id = id;
        this.path = path;
        this.body = body;
    }
}
