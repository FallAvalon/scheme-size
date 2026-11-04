package org.schemetools.shared;

import arc.util.serialization.Jval;
import org.schemetools.Edition;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;

/** Explicit user-requested GitHub release lookup and bounded staging. Never loads classes from a candidate. */
public final class UpdateInstaller {
    public static final int MAX_JAR = 32 * 1024 * 1024;
    public record Release(String repository, String tag, String url, String sha256, long size) { }
    public record Candidate(String id, String version, String sha256) { }
    private UpdateInstaller() { }
    public static String repository(String input) throws IOException {
        if (input == null || input.length() > 160 || !input.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,99}/[A-Za-z0-9][A-Za-z0-9_.-]{0,99}"))
            throw new IOException("st.update.error.source");
        return input;
    }
    public static Release latest(String repository, Edition edition) throws IOException {
        String source = repository(repository);
        Jval data;
        try { data = Jval.read(new String(download("https://api.github.com/repos/" + source + "/releases/latest", 1024 * 1024), StandardCharsets.UTF_8)); }
        catch (RuntimeException failure) { throw new IOException("st.update.error.data", failure); }
        try {
            String assetName = edition.modId + ".jar";
            String tag = data.getString("tag_name");
            if (tag.length() > 100) throw new IOException("st.update.error.data");
            for (Jval asset : data.get("assets").asArray()) if (asset.getString("name").equals(assetName)) {
                String digest = asset.getString("digest", "");
                long size = asset.get("size").asLong();
                if (size < 1 || size > MAX_JAR || !digest.matches("sha256:[0-9a-fA-F]{64}")) throw new IOException("st.update.error.digest");
                String url = asset.getString("browser_download_url");
                URI uri = allowed(url);
                if (!"github.com".equals(uri.getHost()) || !uri.getPath().startsWith("/" + source + "/releases/download/"))
                    throw new IOException("st.update.error.source");
                return new Release(source, tag, url, digest.substring(7).toLowerCase(Locale.ROOT), size);
            }
            throw new IOException("st.update.error.asset");
        } catch (RuntimeException failure) { throw new IOException("st.update.error.data", failure); }
    }
    public static Path stage(Release release, Edition edition, Path directory) throws IOException {
        if (release == null || release.size < 1 || release.size > MAX_JAR || !release.sha256.matches("[0-9a-f]{64}"))
            throw new IOException("st.update.error.data");
        byte[] bytes = download(release.url, (int)release.size);
        if (bytes.length != release.size || !sha(bytes).equals(release.sha256)) throw new IOException("st.update.error.digest");
        Files.createDirectories(directory);
        Path file = Files.createTempFile(directory, "candidate-", ".jar");
        try {
            Files.write(file, bytes); inspect(file, edition); return file;
        } catch (IOException | RuntimeException failure) { Files.deleteIfExists(file); throw failure; }
    }
    public static Candidate inspect(Path file, Edition edition) throws IOException {
        if (file == null || Files.isSymbolicLink(file) || !Files.isRegularFile(file) || Files.size(file) > MAX_JAR)
            throw new IOException("st.update.error.file");
        try (ZipFile zip = new ZipFile(file.toFile())) {
            Set<String> names = new HashSet<>();
            long total = 0; int count = 0;
            for (var entries = zip.entries(); entries.hasMoreElements();) {
                var entry = entries.nextElement(); String name = entry.getName();
                total += Math.max(0, entry.getSize());
                if (++count > 10000 || total > 128L * 1024 * 1024 || entry.getSize() > MAX_JAR
                        || entry.getSize() < 0 || name.chars().anyMatch(Character::isISOControl)
                        || name.startsWith("/") || name.contains("\\") || name.contains(":") || Arrays.asList(name.split("/")).contains("..")
                        || !names.add(name) || name.startsWith("mindustry/") || name.startsWith("arc/")) throw new IOException("st.update.error.file");
                if (!entry.isDirectory()) {
                    long expanded = 0; var crc = new java.util.zip.CRC32(); byte[] buffer = new byte[8192]; int n;
                    try (var input = zip.getInputStream(entry)) {
                        while ((n = input.read(buffer)) != -1) {
                            expanded += n;
                            if (expanded > entry.getSize() || expanded > MAX_JAR) throw new IOException("st.update.error.file");
                            crc.update(buffer, 0, n);
                        }
                    }
                    if (expanded != entry.getSize() || crc.getValue() != entry.getCrc()) throw new IOException("st.update.error.file");
                }
            }
            var metadata = zip.getEntry("mod.hjson");
            if (metadata == null || metadata.getSize() > 16384) throw new IOException("st.update.error.file");
            byte[] bytes;
            try (var input = zip.getInputStream(metadata)) { bytes = input.readNBytes(16385); }
            if (bytes.length > 16384) throw new IOException("st.update.error.file");
            Jval data = Jval.read(bytes);
            String main = edition == Edition.PLUS ? "org.schemetools.SchemeSizePlusMod" : "org.schemetools.SchemeSizeGodMod";
            String version = data.getString("version");
            if (!data.getString("name").equals(edition.modId) || !main.equals(data.getString("main"))
                    || zip.getEntry(main.replace('.', '/') + ".class") == null || version.isBlank() || version.length() > 80
                    || !(data.get("minGameVersion").isNumber() ? data.get("minGameVersion").asInt() == 160
                        : data.getString("minGameVersion").equals("160"))) throw new IOException("st.update.error.edition");
            byte[] full;
            try (var input = Files.newInputStream(file)) { full = input.readNBytes(MAX_JAR + 1); }
            if (full.length > MAX_JAR) throw new IOException("st.update.error.size");
            return new Candidate(edition.modId, version, sha(full));
        } catch (RuntimeException failure) { throw new IOException("st.update.error.file", failure); }
    }
    private static URI allowed(String value) throws IOException {
        try {
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null || uri.getFragment() != null
                    || uri.getPort() != -1 && uri.getPort() != 443 || !Set.of("github.com", "api.github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com").contains(uri.getHost()))
                throw new IOException("st.update.error.source");
            return uri;
        } catch (IllegalArgumentException failure) { throw new IOException("st.update.error.source", failure); }
    }
    private static byte[] download(String url, int limit) throws IOException {
        for (int redirect = 0; redirect < 5; redirect++) {
            var connection = (HttpURLConnection)allowed(url).toURL().openConnection();
            connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(5000); connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", "Scheme-Size-explicit-update-check");
            try {
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) { url = allowed(allowed(url).resolve(connection.getHeaderField("Location")).toString()).toString(); continue; }
                if (status != 200 || connection.getContentLengthLong() > limit) throw new IOException("st.update.error.network");
                try (var input = connection.getInputStream()) {
                    long deadline = System.nanoTime() + 60_000_000_000L;
                    var output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
                    while ((count = input.read(buffer)) != -1) {
                        if ((long)output.size() + count > limit) throw new IOException("st.update.error.size");
                        if (System.nanoTime() > deadline) throw new IOException("st.update.error.network");
                        output.write(buffer, 0, count);
                    }
                    return output.toByteArray();
                }
            } finally { connection.disconnect(); }
        }
        throw new IOException("st.update.error.network");
    }
    private static String sha(byte[] data) throws IOException {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch (java.security.NoSuchAlgorithmException failure) { throw new IOException(failure); }
    }
}
