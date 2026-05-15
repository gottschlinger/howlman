package com.github.gottsch.jpost.cli;

import com.github.gottsch.jpost.JPost;
import com.github.gottsch.jpost.model.Collection;
import com.github.gottsch.jpost.service.StorageService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "collection",
        mixinStandardHelpOptions = true,
        description = "Manage collections",
        subcommands = {
                CollectionCommand.CreateCommand.class,
                CollectionCommand.ListCommand.class,
                CollectionCommand.RemoveCommand.class,
                CommandLine.HelpCommand.class
        }
)
public class CollectionCommand implements Callable<Integer> {

    @ParentCommand
    JPost parent;

    StorageService storage() {
        return parent.storage;
    }

    @Override
    public Integer call() {
        new CommandLine(this).usage(System.out);
        return 0;
    }

    // ── create ───────────────────────────────────────────────────────────────

    @Command(name = "create", description = "Create a new collection")
    static class CreateCommand implements Callable<Integer> {

        @ParentCommand CollectionCommand col;

        @Parameters(index = "0", description = "Collection name")
        String name;

        @Override
        public Integer call() {
            try {
                List<String> existing = col.storage().listCollectionNames();
                if (existing.contains(name)) {
                    System.err.println("Error: collection already exists: " + name);
                    return 1;
                }
                col.storage().saveCollection(new Collection(name));
                System.out.println("Created collection: " + name);
                return 0;
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }

    // ── list ─────────────────────────────────────────────────────────────────

    @Command(name = "list", description = "List all collections")
    static class ListCommand implements Callable<Integer> {

        @ParentCommand CollectionCommand col;

        @Override
        public Integer call() {
            try {
                List<String> names = col.storage().listCollectionNames();
                if (names.isEmpty()) {
                    System.out.println("No collections defined.");
                } else {
                    names.forEach(System.out::println);
                }
                return 0;
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }

    // ── remove ────────────────────────────────────────────────────────────────

    @Command(name = "remove", description = "Remove a collection")
    static class RemoveCommand implements Callable<Integer> {

        @ParentCommand CollectionCommand col;

        @Parameters(index = "0", description = "Collection name")
        String name;

        @Option(names = "--force", description = "Delete even if the collection contains requests")
        boolean force;

        @Override
        public Integer call() {
            try {
                Collection collection = col.storage().loadCollection(name);
                if (!force && !collection.getRequests().isEmpty()) {
                    System.err.println("Error: collection '" + name + "' contains "
                            + collection.getRequests().size()
                            + " request(s). Use --force to delete anyway.");
                    return 1;
                }
                col.storage().deleteCollection(name);
                System.out.println("Removed collection: " + name);
                return 0;
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }
}
