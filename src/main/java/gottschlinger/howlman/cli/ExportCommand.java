package gottschlinger.howlman.cli;

import gottschlinger.howlman.HowlMan;
import gottschlinger.howlman.service.ImportExportService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "export",
        mixinStandardHelpOptions = true,
        description = "Export collections to a file or directory"
)
public class ExportCommand implements Callable<Integer> {

    @ParentCommand
    HowlMan parent;

    @Option(names = {"--collection", "-c"}, description = "Name of the collection to export")
    String collection;

    @Option(names = "--all", description = "Export all collections to a directory")
    boolean all;

    @Option(names = {"--output", "-o"}, required = true,
            description = "Output file path (single collection) or directory (--all)")
    Path output;

    @Override
    public Integer call() {
        if (collection == null && !all) {
            System.err.println("Error: specify --collection <name> or --all");
            return 1;
        }
        if (collection != null && all) {
            System.err.println("Error: --collection and --all are mutually exclusive");
            return 1;
        }

        ImportExportService service = new ImportExportService();
        try {
            if (all) {
                List<String> names = parent.storage.listCollectionNames();
                if (names.isEmpty()) {
                    System.out.println("No collections to export.");
                    return 0;
                }
                service.exportAll(parent.storage, output);
                System.out.println("Exported " + names.size() + " collection(s) to: " + output);
            } else {
                service.exportCollection(parent.storage, collection, output);
                System.out.println("Exported '" + collection + "' to: " + output);
            }
            return 0;
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }
}
