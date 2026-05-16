package gottsch.howlman.cli;

import gottsch.howlman.HowlMan;
import gottsch.howlman.model.AppConfig;
import gottsch.howlman.model.Environment;
import gottsch.howlman.service.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
        name = "env",
        mixinStandardHelpOptions = true,
        description = "Manage environments",
        subcommands = {
                EnvCommand.CreateCommand.class,
                EnvCommand.UseCommand.class,
                EnvCommand.SetCommand.class,
                EnvCommand.ListCommand.class,
                EnvCommand.RemoveCommand.class,
                EnvCommand.DeleteCommand.class,
                CommandLine.HelpCommand.class
        }
)
public class EnvCommand implements Callable<Integer> {

    @ParentCommand
    HowlMan parent;

    StorageService storage() {
        return parent.storage;
    }

    @Override
    public Integer call() {
        new CommandLine(this).usage(System.out);
        return 0;
    }

    // ── create ───────────────────────────────────────────────────────────────

    @Command(name = "create", description = "Create a new environment")
    static class CreateCommand implements Callable<Integer> {

        @ParentCommand EnvCommand env;

        @Parameters(index = "0", description = "Environment name")
        String name;

        @Override
        public Integer call() {
            try {
                List<String> existing = env.storage().listEnvironmentNames();
                if (existing.contains(name)) {
                    System.err.println("Error: environment already exists: " + name);
                    return 1;
                }
                env.storage().saveEnvironment(new Environment(name));
                System.out.println("Created environment: " + name);
                return 0;
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }

    // ── use ──────────────────────────────────────────────────────────────────

    @Command(name = "use", description = "Set the active environment")
    static class UseCommand implements Callable<Integer> {

        @ParentCommand EnvCommand env;

        @Parameters(index = "0", description = "Environment name")
        String name;

        @Override
        public Integer call() {
            try {
                // verify it exists
                env.storage().loadEnvironment(name);
                AppConfig config = env.storage().loadConfig();
                config.setActiveEnvironment(name);
                env.storage().saveConfig(config);
                System.out.println("Active environment set to: " + name);
                return 0;
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }

    // ── set ──────────────────────────────────────────────────────────────────

    @Command(name = "set", description = "Set a variable in an environment")
    static class SetCommand implements Callable<Integer> {

        @ParentCommand EnvCommand env;

        @Parameters(index = "0", description = "Environment name") String envName;
        @Parameters(index = "1", description = "Variable key")     String key;
        @Parameters(index = "2", description = "Variable value")   String value;

        @Override
        public Integer call() {
            try {
                Environment environment = env.storage().loadEnvironment(envName);
                environment.getVariables().put(key, value);
                env.storage().saveEnvironment(environment);
                System.out.println("Set " + key + "=" + value + " in environment: " + envName);
                return 0;
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }

    // ── list ─────────────────────────────────────────────────────────────────

    @Command(name = "list", description = "List all environments, or variables in one environment")
    static class ListCommand implements Callable<Integer> {

        @ParentCommand EnvCommand env;

        @Parameters(index = "0", description = "Environment name (optional)", arity = "0..1")
        String envName;

        @Override
        public Integer call() {
            try {
                if (envName == null) {
                    List<String> names = env.storage().listEnvironmentNames();
                    if (names.isEmpty()) {
                        System.out.println("No environments defined.");
                    } else {
                        names.forEach(System.out::println);
                    }
                } else {
                    Environment environment = env.storage().loadEnvironment(envName);
                    Map<String, String> vars = environment.getVariables();
                    if (vars.isEmpty()) {
                        System.out.println("No variables in environment: " + envName);
                    } else {
                        vars.forEach((k, v) -> System.out.println(k + "=" + v));
                    }
                }
                return 0;
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }

    // ── remove ────────────────────────────────────────────────────────────────

    @Command(name = "remove", description = "Remove a variable from an environment")
    static class RemoveCommand implements Callable<Integer> {

        @ParentCommand EnvCommand env;

        @Parameters(index = "0", description = "Environment name") String envName;
        @Parameters(index = "1", description = "Variable key")     String key;

        @Override
        public Integer call() {
            try {
                Environment environment = env.storage().loadEnvironment(envName);
                if (!environment.getVariables().containsKey(key)) {
                    System.err.println("Error: variable not found: " + key);
                    return 1;
                }
                environment.getVariables().remove(key);
                env.storage().saveEnvironment(environment);
                System.out.println("Removed variable '" + key + "' from environment: " + envName);
                return 0;
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Command(name = "delete", description = "Delete an entire environment")
    static class DeleteCommand implements Callable<Integer> {

        @ParentCommand EnvCommand env;

        @Parameters(index = "0", description = "Environment name")
        String name;

        @Override
        public Integer call() {
            try {
                env.storage().deleteEnvironment(name);
                // clear activeEnvironment in config if it pointed to this env
                AppConfig config = env.storage().loadConfig();
                if (name.equals(config.getActiveEnvironment())) {
                    config.setActiveEnvironment(null);
                    env.storage().saveConfig(config);
                }
                System.out.println("Deleted environment: " + name);
                return 0;
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }
}
