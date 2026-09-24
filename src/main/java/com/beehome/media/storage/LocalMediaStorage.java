package com.beehome.media.storage;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.stereotype.Component;

@Component
public class LocalMediaStorage implements MediaStorage {
    private final Path root;
    public LocalMediaStorage(@Value("${media.storage-directory}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
    }
    private Path path(String key) throws IOException {
        if (!key.matches("[0-9a-fA-F-]{36}/[0-9a-fA-F-]{36}")) throw new IOException("Invalid storage key");
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) throw new IOException("Storage path escaped root");
        return resolved;
    }
    private void prepare(Path target) throws IOException {
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root)) throw new IOException("Storage root is symbolic link");
        Files.createDirectories(target.getParent());
        if (Files.isSymbolicLink(target.getParent()) || Files.isSymbolicLink(target)) throw new IOException("Storage path is symbolic link");
        try {
            Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
            Files.setPosixFilePermissions(target.getParent(), PosixFilePermissions.fromString("rwx------"));
        } catch (UnsupportedOperationException ignored) { /* Non-POSIX filesystems use their configured ACLs. */ }
    }
    @Override public void store(String key, byte[] bytes) throws IOException {
        Path target = path(key); prepare(target);
        try {
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try { Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-------")); }
            catch (UnsupportedOperationException ignored) { /* See directory ACL fallback above. */ }
        }
        catch (IOException e) {
            try { Files.deleteIfExists(target); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
    }
    @Override public Resource load(String key) throws IOException {
        Path target = path(key); prepare(target);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Stored content missing");
        return new FileSystemResource(target);
    }
    @Override public void delete(String key) throws IOException {
        Path target = path(key); prepare(target);
        Files.deleteIfExists(target);
    }
}
