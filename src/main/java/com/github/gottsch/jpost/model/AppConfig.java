package com.github.gottsch.jpost.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AppConfig {

    private String activeEnvironment;
    private String defaultCollection = "default";

    public AppConfig() {}

    public String getActiveEnvironment() { return activeEnvironment; }
    public void setActiveEnvironment(String activeEnvironment) { this.activeEnvironment = activeEnvironment; }

    public String getDefaultCollection() { return defaultCollection; }
    public void setDefaultCollection(String defaultCollection) { this.defaultCollection = defaultCollection; }
}
