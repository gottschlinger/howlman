package gottschlinger.howlman.gui.controller;

import java.util.List;

public sealed interface TreeNodeData permits TreeNodeData.Collection, TreeNodeData.Folder, TreeNodeData.Request {

    String displayName();

    record Collection(String name) implements TreeNodeData {
        public String displayName() { return name; }
    }

    record Folder(String name, String collectionName, List<String> path) implements TreeNodeData {
        public String displayName() { return name; }
    }

    record Request(String name, String collectionName, List<String> folderPath) implements TreeNodeData {
        public String displayName() { return name; }
    }
}
