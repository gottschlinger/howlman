package gottschlinger.howlman.service;

import gottschlinger.howlman.model.Environment;
import gottschlinger.howlman.model.RequestCollection;

import java.util.ArrayList;
import java.util.List;

public class ImportResult {

    private final List<RequestCollection> collections = new ArrayList<>();
    private final List<Environment> environments = new ArrayList<>();

    public List<RequestCollection> getCollections() { return collections; }
    public List<Environment> getEnvironments() { return environments; }
}
