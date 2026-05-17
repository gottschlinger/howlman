package gottschlinger.howlman.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RequestCollection {

    private String name;
    private List<SavedRequest> requests = new ArrayList<>();
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<RequestFolder> folders = new ArrayList<>();

    public RequestCollection() {}

    public RequestCollection(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<SavedRequest> getRequests() { return requests; }
    public void setRequests(List<SavedRequest> requests) { this.requests = requests; }

    public List<RequestFolder> getFolders() { return folders; }
    public void setFolders(List<RequestFolder> folders) { this.folders = folders; }
}
