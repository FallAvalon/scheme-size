package org.schemetools.schematic;

import mindustry.game.Schematic;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Private common-edition library. Import always creates a copy; original files are never renamed. */
public final class SchematicStore {
    public record Entry(Path file, Schematic schematic) { }
    public record Listing(List<Entry> entries, List<String> unreadable) { }
    private final Path directory;
    public SchematicStore(Path directory) { this.directory = directory.toAbsolutePath().normalize(); }
    public Listing list() throws IOException {
        directory();
        List<Entry> entries = new ArrayList<>();
        List<String> unreadable = new ArrayList<>();
        int count = 0;
        long bytesRead = 0;
        int tilesRead = 0;
        try (var files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                if (!file.getFileName().toString().matches("[0-9a-f-]{36}\\.(msch|mtls)")) continue;
                if (++count > 256) throw new IOException("st.schematic.error.library-full");
                try {
                    if (bytesRead > 64L * 1024 * 1024 || tilesRead > 1_000_000) { unreadable.add(file.getFileName().toString()); continue; }
                    bytesRead += Files.size(file);
                    Schematic schematic = readFile(file);
                    tilesRead += schematic.tiles.size;
                    entries.add(new Entry(file, schematic));
                }
                catch (IOException failure) { unreadable.add(file.getFileName().toString()); }
            }
        }
        entries.sort(Comparator.comparing(e -> e.schematic.tags.get("name", ""), String.CASE_INSENSITIVE_ORDER));
        return new Listing(List.copyOf(entries), List.copyOf(unreadable));
    }
    public Path saveCopy(Schematic schematic) throws IOException {
        directory();
        int count = 0;
        try (var files = Files.newDirectoryStream(directory)) {
            for (Path file : files) if (file.getFileName().toString().matches("[0-9a-f-]{36}\\.(msch|mtls)") && ++count >= 256)
                throw new IOException("st.schematic.error.library-full");
        }
        byte[] bytes = SchematicCodec.write(schematic);
        Path target = directory.resolve(UUID.randomUUID() + "." + extension(schematic));
        Path temporary = directory.resolve(".import-" + UUID.randomUUID() + ".tmp");
        try {
            Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temporary); }
        return target;
    }
    public void delete(Entry entry) throws IOException {
        directory();
        Path file = entry.file.toAbsolutePath().normalize();
        if (!directory.equals(file.getParent()) || !file.getFileName().toString().matches("[0-9a-f-]{36}\\.(msch|mtls)"))
            throw new IOException("st.schematic.error.file");
        readFile(file); // Refuse symlinks/corrupt replacements and leave those files for recovery.
        Files.delete(file);
    }
    public static Schematic readFile(Path file) throws IOException {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || Files.size(file) > SchematicCodec.MAX_FILE_BYTES) throw new IOException("st.schematic.error.file");
        try (var input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(SchematicCodec.MAX_FILE_BYTES + 1);
            if (bytes.length > SchematicCodec.MAX_FILE_BYTES) throw new IOException("st.schematic.error.size");
            return SchematicCodec.read(bytes);
        }
    }
    public static void exportNew(Schematic schematic, Path file) throws IOException {
        byte[] bytes = SchematicCodec.write(schematic);
        // CREATE_NEW also refuses existing symlinks and never truncates a user's file.
        Files.write(file, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }
    public static String extension(Schematic schematic) {
        return schematic.width > 128 || schematic.height > 128 || schematic.tiles.size > 16384 ? "mtls" : "msch";
    }
    private void directory() throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("st.schematic.error.file");
    }
}
