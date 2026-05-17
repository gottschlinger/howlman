package gottschlinger.howlman.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gottschlinger.howlman.model.HowlManBundle;
import gottschlinger.howlman.model.RequestCollection;

import java.io.IOException;
import java.nio.file.Path;

public class NativeImporter {

    private final ObjectMapper mapper = new ObjectMapper();

    public ImportResult importFile(Path file, String nameOverride) throws IOException {
        JsonNode root = mapper.readTree(file.toFile());
        ImportResult result = new ImportResult();

        if (root.has("collection")) {
            // Bundled format: collection + environments
            HowlManBundle bundle = mapper.treeToValue(root, HowlManBundle.class);
            if (bundle.getCollection() != null) {
                if (nameOverride != null) bundle.getCollection().setName(nameOverride);
                result.getCollections().add(bundle.getCollection());
            }
            if (bundle.getEnvironments() != null) {
                result.getEnvironments().addAll(bundle.getEnvironments());
            }
        } else {
            // Plain collection (legacy format)
            RequestCollection collection = mapper.treeToValue(root, RequestCollection.class);
            if (nameOverride != null) collection.setName(nameOverride);
            result.getCollections().add(collection);
        }

        return result;
    }
}
