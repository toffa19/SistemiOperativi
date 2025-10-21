package it.unibo.labso.p2p.peer;

import java.nio.file.*;
import java.io.*;
import java.util.*;

final class ResourceManager {
    private final Path root;
    ResourceManager(String dir){ this.root = Path.of(dir); }
    Path resolve(String name){ return root.resolve(name); }
    java.util.List<String> list() throws IOException {
        if (!Files.exists(root)) return List.of();
        try (var s = Files.list(root)) {
            return s.filter(Files::isRegularFile).map(p -> p.getFileName().toString()).toList();
        }
    }
    void add(String name, String content) throws IOException {
        Files.createDirectories(root);
        Files.writeString(root.resolve(name), content);
    }
}
