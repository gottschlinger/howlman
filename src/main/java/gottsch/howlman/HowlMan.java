package gottsch.howlman;

import gottsch.howlman.cli.CollectionCommand;
import gottsch.howlman.cli.EnvCommand;
import gottsch.howlman.cli.GuiCommand;
import gottsch.howlman.cli.RequestCommand;
import gottsch.howlman.cli.RunCommand;
import gottsch.howlman.service.StorageService;
import gottsch.howlman.util.ConfigPaths;
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
        commandLine.registerConverter(gottsch.howlman.model.HttpMethod.class,
                s -> gottsch.howlman.model.HttpMethod.valueOf(s.toUpperCase()));
        commandLine.registerConverter(gottsch.howlman.model.BodyType.class,
                s -> gottsch.howlman.model.BodyType.valueOf(s.toUpperCase()));
        commandLine.registerConverter(gottsch.howlman.model.AuthType.class,
                s -> gottsch.howlman.model.AuthType.valueOf(s.toUpperCase()));
        int exit = commandLine.execute(args);
        System.exit(exit);
    }
}
