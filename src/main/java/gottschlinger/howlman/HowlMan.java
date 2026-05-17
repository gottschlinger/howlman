package gottschlinger.howlman;

import gottschlinger.howlman.cli.CollectionCommand;
import gottschlinger.howlman.cli.EnvCommand;
import gottschlinger.howlman.cli.ExportCommand;
import gottschlinger.howlman.cli.GuiCommand;
import gottschlinger.howlman.cli.ImportCommand;
import gottschlinger.howlman.cli.RequestCommand;
import gottschlinger.howlman.cli.RunCommand;
import gottschlinger.howlman.model.AuthType;
import gottschlinger.howlman.model.BodyType;
import gottschlinger.howlman.model.HttpMethod;
import gottschlinger.howlman.service.StorageService;
import gottschlinger.howlman.util.ConfigPaths;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.util.concurrent.Callable;

@Command(
        name = "howlman",
        version = "1.0.0",
        mixinStandardHelpOptions = true,
        subcommands = {
                RunCommand.class,
                RequestCommand.class,
                EnvCommand.class,
                CollectionCommand.class,
                ImportCommand.class,
                ExportCommand.class,
                GuiCommand.class,
                CommandLine.HelpCommand.class
        },
        description = "Lightweight CLI HTTP client"
)
public class HowlMan implements Callable<Integer> {

    public StorageService storage;

    @Override
    public Integer call() {
        new CommandLine(this).usage(System.out);
        return 0;
    }

    public static void main(String[] args) {
        HowlMan app = new HowlMan();
        app.storage = new StorageService(new ConfigPaths());
        try {
            app.storage.init();
        } catch (IOException e) {
            System.err.println("Error: failed to initialize data directory: " + e.getMessage());
            System.exit(1);
        }
        CommandLine commandLine = new CommandLine(app);
        commandLine.registerConverter(HttpMethod.class,
                s -> HttpMethod.valueOf(s.toUpperCase()));
        commandLine.registerConverter(BodyType.class,
                s -> BodyType.valueOf(s.toUpperCase()));
        commandLine.registerConverter(AuthType.class,
                s -> AuthType.valueOf(s.toUpperCase()));
        int exit = commandLine.execute(args);
        System.exit(exit);
    }
}
