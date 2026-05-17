package gottschlinger.howlman.cli;

import gottschlinger.howlman.HowlMan;
import gottschlinger.howlman.model.RequestCollection;
import gottschlinger.howlman.model.Environment;
import gottschlinger.howlman.service.*;
import gottschlinger.howlman.service.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "import",
        mixinStandardHelpOptions = true,
        description = "Import a collection or environment from a file"
)
public class ImportCommand implements Callable<Integer> {

    @ParentCommand
    HowlMan parent;

    @Parameters(index = "0", description = "File to import")
    Path file;

    @Option(names = "--format", defaultValue = "native",
            description = "Import format: native (default), postman, insomnia")
    String format;

    @Option(names = {"--collection", "-c"},
            description = "Override the collection name from the file")
    String collectionOverride;

    @Option(names = "--force",
            description = "Overwrite existing collections/environments instead of auto-renaming")
    boolean force;

    @Override
    public Integer call() {
        try {
            ImportResult result = parse();
            saveAll(result);
            return 0;
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private ImportResult parse() throws IOException {
        switch (format.toLowerCase()) {
            case "postman":  return new PostmanImporter().importFile(file, collectionOverride);
            case "insomnia": return new InsomniaImporter().importFile(file, collectionOverride);
            case "native":   return new NativeImporter().importFile(file, collectionOverride);
            default:
                throw new IOException("Unknown format '" + format + "'. Use: native, postman, insomnia");
        }
    }

    private void saveAll(ImportResult result) throws IOException {
        ImportExportService service = new ImportExportService();
        List<String> existingCols = parent.storage.listCollectionNames();
        List<String> existingEnvs = parent.storage.listEnvironmentNames();

        for (RequestCollection c : result.getCollections()) {
            String saveName = force
                    ? c.getName()
                    : service.nextAvailableName(c.getName(), existingCols);
            if (!saveName.equals(c.getName())) {
                System.out.println("Collection '" + c.getName() + "' already exists â€” saving as '" + saveName + "'");
                c.setName(saveName);
            }
            existingCols.add(saveName);
            parent.storage.saveCollection(c);
            System.out.println("Imported collection: " + saveName);
        }

        for (Environment e : result.getEnvironments()) {
            String saveName = force
                    ? e.getName()
                    : service.nextAvailableName(e.getName(), existingEnvs);
            if (!saveName.equals(e.getName())) {
                System.out.println("Environment '" + e.getName() + "' already exists â€” saving as '" + saveName + "'");
                e.setName(saveName);
            }
            existingEnvs.add(saveName);
            parent.storage.saveEnvironment(e);
            System.out.println("Imported environment: " + saveName);
        }
    }
}
