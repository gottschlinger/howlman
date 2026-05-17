package gottschlinger.howlman.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class HowlManBundle {

    private RequestCollection collection;
    private List<Environment> environments = new ArrayList<>();

    public RequestCollection getCollection() { return collection; }
    public void setCollection(RequestCollection collection) { this.collection = collection; }

    public List<Environment> getEnvironments() { return environments; }
    public void setEnvironments(List<Environment> environments) { this.environments = environments; }
}
