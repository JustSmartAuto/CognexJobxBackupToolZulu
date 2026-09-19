package com.cognex.insight.model;

import java.util.List;

public class HmiSessionInfo {
    public String $type = "HmiSessionInfo";
    public String sheetName = "Inspection";
    public List<String> cellNames;
    public boolean ignoreEditorAttached = true;
    public boolean enableQueuedResults = false;
    public boolean includeCustomView = true;

    public HmiSessionInfo() {
    }

    public HmiSessionInfo(List<String> cellNames) {
        this.cellNames = cellNames;
    }
}
