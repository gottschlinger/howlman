package com.github.gottsch.jpost.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Collection {

    private String name;
    private List<SavedRequest> requests = new ArrayList<>();

    public Collection() {}

    public Collection(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<SavedRequest> getRequests() { return requests; }
    public void setRequests(List<SavedRequest> requests) { this.requests = requests; }
}
