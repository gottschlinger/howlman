package com.github.gottsch.jpost;

import com.github.gottsch.jpost.cli.CollectionCommand;
import com.github.gottsch.jpost.cli.EnvCommand;
import com.github.gottsch.jpost.cli.RequestCommand;
import com.github.gottsch.jpost.cli.RunCommand;
import com.github.gottsch.jpost.service.StorageService;
import com.github.gottsch.jpost.util.ConfigPaths;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.util.concurrent.Callable;

@Command(
        name = "jpost",
        version = "1.0.0",
        mixinStandardHelpOptions = true,
        subcommands = {
                RunCommand.class,
                RequestCommand.class,
                EnvCommand.class,
                CollectionCommand.class,
                CommandLine.HelpCommand.class
        },
        description = "Lightweight CLI HTTP client"
)
public class JPost implements Callable<Integer> {

    public StorageService storage;

    @Override
    public Integer call() {
        new CommandLine(this).usage(System.out);
        return 0;
    }

    public static void main(String[] args) {
        JPost app = new JPost();
        app.storage = new StorageService(new ConfigPaths());
        try {
            app.storage.init();
        } catch (IOException e) {
            System.err.println("Error: failed to initialize data directory: " + e.getMessage());
            System.exit(1);
        }
        CommandLine commandLine = new CommandLine(app);
        commandLine.registerConverter(com.github.gottsch.jpost.model.HttpMethod.class,
                s -> com.github.gottsch.jpost.model.HttpMethod.valueOf(s.toUpperCase()));
        commandLine.registerConverter(com.github.gottsch.jpost.model.BodyType.class,
                s -> com.github.gottsch.jpost.model.BodyType.valueOf(s.toUpperCase()));
        commandLine.registerConverter(com.github.gottsch.jpost.model.AuthType.class,
                s -> com.github.gottsch.jpost.model.AuthType.valueOf(s.toUpperCase()));
        int exit = commandLine.execute(args);
        System.exit(exit);
    }
}
