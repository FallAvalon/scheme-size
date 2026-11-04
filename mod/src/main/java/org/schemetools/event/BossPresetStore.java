package org.schemetools.event;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Local, filesystem-only preset store. It has no Mindustry UI or client dependencies. */
public final class BossPresetStore {
    public static final int MAX_PRESETS = 128;
    public static final int MAX_JSON_BYTES = 16 * 1024;

    private static final String FILE_PREFIX = "preset-";
    private static final String FILE_SUFFIX = ".json";
    private static final String FILE_PATTERN = "preset-*.json";

    private final Path directory;

    /**
     * @param directory caller-selected local mod data directory; no game-global path is assumed
     */
    public BossPresetStore(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
    }

    /** Stores or replaces a preset with the same case-insensitive name. */
    public void save(BossRoundPreset preset) throws IOException {
        Objects.requireNonNull(preset, "preset");
        ensureDirectory();
        Path target = pathFor(preset.name());
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            BossRoundPreset current = readPreset(target);
            ensureExpectedName(target, current);
            if (!current.key().equals(preset.key()))
                throw new PresetFormatException("preset filename does not match its name: " + target.getFileName());
        } else if (list().size() >= MAX_PRESETS) {
            throw new IOException("preset limit reached (" + MAX_PRESETS + ")");
        }

        byte[] bytes = encode(preset).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_JSON_BYTES) throw new IOException("serialized preset exceeds size limit");
        Path temporary = directory.resolve(".boss-preset-" + UUID.randomUUID() + ".tmp");
        try {
            Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            if (Files.isSymbolicLink(target)) throw new IOException("refusing to replace symbolic link: " + target.getFileName());
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic preset replacement is not supported by this filesystem", exception);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Loads a preset by name; names are matched case-insensitively after Unicode normalization. */
    public Optional<BossRoundPreset> load(String name) throws IOException {
        String key = keyForLookup(name);
        ensureDirectory();
        Path file = pathFor(name);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        BossRoundPreset preset = readPreset(file);
        ensureExpectedName(file, preset);
        if (!preset.key().equals(key)) throw new PresetFormatException("preset name hash mismatch: " + file.getFileName());
        return Optional.of(preset);
    }

    /**
     * Lists presets in a stable order. A malformed, unsupported, oversized or symlinked preset causes
     * an IOException; the offending file is left untouched so a later version can recover it.
     */
    public List<BossRoundPreset> list() throws IOException {
        ensureDirectory();
        List<BossRoundPreset> presets = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, FILE_PATTERN)) {
            for (Path file : files) {
                if (presets.size() >= MAX_PRESETS)
                    throw new IOException("preset limit exceeded (" + MAX_PRESETS + ")");
                if (!file.getFileName().toString().matches("preset-[0-9a-f]{64}\\.json"))
                    throw new PresetFormatException("unexpected preset filename: " + file.getFileName());
                BossRoundPreset preset = readPreset(file);
                ensureExpectedName(file, preset);
                presets.add(preset);
            }
        }
        presets.sort(Comparator.comparing(BossRoundPreset::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(BossRoundPreset::name));
        return List.copyOf(presets);
    }

    /** Deletes only a valid preset whose embedded name agrees with its filename. */
    public boolean delete(String name) throws IOException {
        keyForLookup(name);
        ensureDirectory();
        Path file = pathFor(name);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return false;
        BossRoundPreset preset = readPreset(file);
        ensureExpectedName(file, preset);
        if (!preset.key().equals(keyForLookup(name)))
            throw new PresetFormatException("preset name hash mismatch: " + file.getFileName());
        return Files.deleteIfExists(file);
    }

    private void ensureDirectory() throws IOException {
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("preset path is not a regular directory: " + directory);
        } else {
            Files.createDirectories(directory);
            if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("preset path is not a regular directory: " + directory);
        }
    }

    private Path pathFor(String name) {
        return directory.resolve(FILE_PREFIX + sha256(keyForLookup(name)) + FILE_SUFFIX);
    }

    private static String keyForLookup(String name) {
        // Reuse model validation to make lookup/save/delete agree on accepted names.
        BossRoundPreset check = new BossRoundPreset(name, "", 0, 1,
            BossRound.BossDeath.HUNTERS_WIN, 0, BossRound.Disconnect.PAUSE, 1, 2);
        return check.key();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("SHA-256 is required by the Java platform", impossible);
        }
    }

    private void ensureExpectedName(Path file, BossRoundPreset preset) throws PresetFormatException {
        String expected = FILE_PREFIX + sha256(preset.key()) + FILE_SUFFIX;
        if (!file.getFileName().toString().equals(expected))
            throw new PresetFormatException("preset filename does not match its name: " + file.getFileName());
    }

    private static BossRoundPreset readPreset(Path file) throws IOException {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("refusing to read non-regular preset file: " + file.getFileName());
        long size = Files.size(file);
        if (size > MAX_JSON_BYTES) throw new PresetFormatException("preset file exceeds size limit: " + file.getFileName());

        byte[] bytes;
        try (InputStream input = Files.newInputStream(file)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream((int)Math.max(0, size));
            byte[] buffer = new byte[1024];
            int total = 0;
            for (int read; (read = input.read(buffer)) != -1;) {
                total += read;
                if (total > MAX_JSON_BYTES)
                    throw new PresetFormatException("preset file exceeds size limit: " + file.getFileName());
                output.write(buffer, 0, read);
            }
            bytes = output.toByteArray();
        }

        final String json;
        try {
            json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new PresetFormatException("preset is not valid UTF-8: " + file.getFileName(), exception);
        }
        return decode(json, file);
    }

    public static String encode(BossRoundPreset preset) {
        BossCombatSettings c = preset.combat();
        return "{\n"
            + "  \"formatVersion\": " + BossRoundPreset.FORMAT_VERSION + ",\n"
            + "  \"name\": " + quote(preset.name()) + ",\n"
            + "  \"description\": " + quote(preset.description()) + ",\n"
            + "  \"preparationSeconds\": " + Double.toString(preset.preparationSeconds()) + ",\n"
            + "  \"durationSeconds\": " + Double.toString(preset.durationSeconds()) + ",\n"
            + "  \"deathPolicy\": " + quote(preset.deathPolicy().name()) + ",\n"
            + "  \"deathPenaltySeconds\": " + Double.toString(preset.deathPenaltySeconds()) + ",\n"
            + "  \"disconnectPolicy\": " + quote(preset.disconnectPolicy().name()) + ",\n"
            + "  \"bossTeamId\": " + preset.bossTeamId() + ",\n"
            + "  \"hunterTeamId\": " + preset.hunterTeamId() + ",\n"
            + "  \"combat\": {"
            + "\"bossUnit\":" + quote(c.bossUnit()) + ",\"bossEffect\":" + quote(c.bossEffect())
            + ",\"hunterEffect\":" + quote(c.hunterEffect()) + ",\"bossHealth\":" + c.bossHealth()
            + ",\"bossDamage\":" + c.bossDamage() + ",\"hunterHealth\":" + c.hunterHealth()
            + ",\"hunterDamage\":" + c.hunterDamage() + ",\"startingItem\":" + quote(c.startingItem())
            + ",\"bossItems\":" + c.bossItems() + ",\"hunterItems\":" + c.hunterItems()
            + ",\"respawnDelay\":" + c.respawnDelay() + ",\"huntersEliminatedWins\":" + c.huntersEliminatedWins()
            + ",\"restoreTeams\":" + c.restoreTeams() + "}\n"
            + "}\n";
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (ch < 0x20) result.append(String.format(Locale.ROOT, "\\u%04x", (int)ch));
                    else result.append(ch);
                }
            }
        }
        return result.append('"').toString();
    }

    public static BossRoundPreset decode(String json) throws IOException {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES)
            throw new PresetFormatException("preset exceeds size limit");
        return decode(json, Path.of("import.json"));
    }

    private static BossRoundPreset decode(String json, Path file) throws IOException {
        try {
            Object value = new JsonParser(json).parse();
            if (!(value instanceof Map<?, ?> raw)) throw new PresetFormatException("preset root must be an object");
            Map<String, Object> object = stringKeyMap(raw);
            List<String> fields = new ArrayList<>(List.of("formatVersion", "name", "description", "preparationSeconds",
                "durationSeconds", "deathPolicy", "deathPenaltySeconds", "disconnectPolicy", "bossTeamId", "hunterTeamId"));
            int version = exactInt(object.get("formatVersion"), "formatVersion");
            if (version != 1 && version != BossRoundPreset.FORMAT_VERSION)
                throw new PresetFormatException("unsupported preset format version " + version + ": " + file.getFileName());
            if (version == 2) fields.add("combat");
            if (object.size() != fields.size() || !object.keySet().containsAll(fields))
                throw new PresetFormatException("preset fields do not match format version " + version);
            return new BossRoundPreset(
                string(object.get("name"), "name"),
                string(object.get("description"), "description"),
                decimal(object.get("preparationSeconds"), "preparationSeconds"),
                decimal(object.get("durationSeconds"), "durationSeconds"),
                enumValue(BossRound.BossDeath.class, object.get("deathPolicy"), "deathPolicy"),
                decimal(object.get("deathPenaltySeconds"), "deathPenaltySeconds"),
                enumValue(BossRound.Disconnect.class, object.get("disconnectPolicy"), "disconnectPolicy"),
                exactInt(object.get("bossTeamId"), "bossTeamId"),
                exactInt(object.get("hunterTeamId"), "hunterTeamId"),
                version == 1 ? BossCombatSettings.defaults() : combat(object.get("combat"))
            );
        } catch (PresetFormatException exception) {
            throw exception;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new PresetFormatException("invalid preset contents: " + file.getFileName(), exception);
        }
    }

    private static BossCombatSettings combat(Object value) throws PresetFormatException {
        if (!(value instanceof Map<?, ?> raw)) throw new PresetFormatException("combat must be an object");
        Map<String, Object> c = stringKeyMap(raw);
        List<String> fields = List.of("bossUnit", "bossEffect", "hunterEffect", "bossHealth", "bossDamage",
            "hunterHealth", "hunterDamage", "startingItem", "bossItems", "hunterItems", "respawnDelay",
            "huntersEliminatedWins", "restoreTeams");
        if (c.size() != fields.size() || !c.keySet().containsAll(fields))
            throw new PresetFormatException("combat fields do not match format version 2");
        return new BossCombatSettings(string(c.get("bossUnit"), "bossUnit"), string(c.get("bossEffect"), "bossEffect"),
            string(c.get("hunterEffect"), "hunterEffect"), decimal(c.get("bossHealth"), "bossHealth"),
            decimal(c.get("bossDamage"), "bossDamage"), decimal(c.get("hunterHealth"), "hunterHealth"),
            decimal(c.get("hunterDamage"), "hunterDamage"), string(c.get("startingItem"), "startingItem"),
            exactInt(c.get("bossItems"), "bossItems"), exactInt(c.get("hunterItems"), "hunterItems"),
            decimal(c.get("respawnDelay"), "respawnDelay"), bool(c.get("huntersEliminatedWins")), bool(c.get("restoreTeams")));
    }

    private static boolean bool(Object value) throws PresetFormatException {
        if (!(value instanceof Boolean result)) throw new PresetFormatException("expected boolean");
        return result;
    }

    private static Map<String, Object> stringKeyMap(Map<?, ?> raw) throws PresetFormatException {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) throw new PresetFormatException("non-string preset key");
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static String string(Object value, String field) throws PresetFormatException {
        if (!(value instanceof String text)) throw new PresetFormatException(field + " must be a string");
        return text;
    }

    private static double decimal(Object value, String field) throws PresetFormatException {
        if (!(value instanceof BigDecimal number)) throw new PresetFormatException(field + " must be a number");
        double result = number.doubleValue();
        if (!Double.isFinite(result)) throw new PresetFormatException(field + " is not finite");
        return result;
    }

    private static int exactInt(Object value, String field) throws PresetFormatException {
        if (!(value instanceof BigDecimal number)) throw new PresetFormatException(field + " must be an integer");
        try {
            return number.intValueExact();
        } catch (ArithmeticException exception) {
            throw new PresetFormatException(field + " must be an integer in range", exception);
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, Object value, String field) throws PresetFormatException {
        if (!(value instanceof String text)) throw new PresetFormatException(field + " must be a string");
        try {
            return Enum.valueOf(type, text);
        } catch (IllegalArgumentException exception) {
            throw new PresetFormatException("unsupported " + field + ": " + text, exception);
        }
    }

    public static final class PresetFormatException extends IOException {
        public PresetFormatException(String message) { super(message); }
        public PresetFormatException(String message, Throwable cause) { super(message, cause); }
    }

    /** Minimal strict JSON reader for the bounded, fixed preset schema; no engine/runtime dependency. */
    private static final class JsonParser {
        private final String text;
        private int position;

        JsonParser(String text) { this.text = Objects.requireNonNull(text); }

        Object parse() throws PresetFormatException {
            skipWhitespace();
            Object result = value(0);
            skipWhitespace();
            if (position != text.length()) fail("trailing characters");
            return result;
        }

        private Object value(int depth) throws PresetFormatException {
            if (depth > 8) fail("JSON nesting limit exceeded");
            skipWhitespace();
            if (position >= text.length()) return fail("unexpected end of JSON");
            char ch = text.charAt(position);
            if (ch == '"') return string();
            if (ch == '{') return object(depth + 1);
            if (ch == '[') return array(depth + 1);
            if (ch == '-' || (ch >= '0' && ch <= '9')) return number();
            if (consumeLiteral("true")) return Boolean.TRUE;
            if (consumeLiteral("false")) return Boolean.FALSE;
            if (consumeLiteral("null")) return null;
            return fail("invalid JSON value");
        }

        private Map<String, Object> object(int depth) throws PresetFormatException {
            position++;
            skipWhitespace();
            Map<String, Object> values = new LinkedHashMap<>();
            if (take('}')) return values;
            while (true) {
                skipWhitespace();
                if (position >= text.length() || text.charAt(position) != '"') fail("object key must be a string");
                String key = string();
                if (values.containsKey(key)) fail("duplicate object key");
                skipWhitespace();
                require(':');
                values.put(key, value(depth));
                if (values.size() > 32) fail("too many object fields");
                skipWhitespace();
                if (take('}')) return values;
                require(',');
            }
        }

        private List<Object> array(int depth) throws PresetFormatException {
            position++;
            skipWhitespace();
            List<Object> values = new ArrayList<>();
            if (take(']')) return values;
            while (true) {
                values.add(value(depth));
                if (values.size() > 128) fail("too many array items");
                skipWhitespace();
                if (take(']')) return values;
                require(',');
            }
        }

        private String string() throws PresetFormatException {
            require('"');
            StringBuilder result = new StringBuilder();
            while (position < text.length()) {
                char ch = text.charAt(position++);
                if (ch == '"') return result.toString();
                if (ch < 0x20) fail("unescaped control character in string");
                if (ch != '\\') {
                    result.append(ch);
                } else {
                    if (position >= text.length()) fail("unfinished string escape");
                    char escape = text.charAt(position++);
                    switch (escape) {
                        case '"', '\\', '/' -> result.append(escape);
                        case 'b' -> result.append('\b');
                        case 'f' -> result.append('\f');
                        case 'n' -> result.append('\n');
                        case 'r' -> result.append('\r');
                        case 't' -> result.append('\t');
                        case 'u' -> result.append(unicodeEscape());
                        default -> fail("invalid string escape");
                    }
                }
                if (result.length() > MAX_JSON_BYTES) fail("string is too long");
            }
            return fail("unterminated string");
        }

        private char unicodeEscape() throws PresetFormatException {
            if (position + 4 > text.length()) return fail("short Unicode escape");
            int value = 0;
            for (int i = 0; i < 4; i++) {
                int digit = Character.digit(text.charAt(position++), 16);
                if (digit < 0) return fail("invalid Unicode escape");
                value = (value << 4) | digit;
            }
            return (char)value;
        }

        private BigDecimal number() throws PresetFormatException {
            int start = position;
            if (take('-') && position >= text.length()) return fail("incomplete number");
            if (take('0')) {
                if (position < text.length() && Character.isDigit(text.charAt(position))) fail("leading zero in number");
            } else {
                digits(true);
            }
            if (take('.')) digits(true);
            if (take('e') || take('E')) {
                if (!take('+')) take('-');
                digits(true);
            }
            try {
                return new BigDecimal(text.substring(start, position));
            } catch (NumberFormatException exception) {
                throw new PresetFormatException("invalid number", exception);
            }
        }

        private void digits(boolean required) throws PresetFormatException {
            int start = position;
            while (position < text.length() && text.charAt(position) >= '0' && text.charAt(position) <= '9') position++;
            if (required && start == position) fail("expected digit");
        }

        private boolean consumeLiteral(String literal) {
            if (!text.startsWith(literal, position)) return false;
            position += literal.length();
            return true;
        }

        private void skipWhitespace() {
            while (position < text.length()) {
                char ch = text.charAt(position);
                if (ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n') position++;
                else return;
            }
        }

        private boolean take(char expected) {
            if (position < text.length() && text.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void require(char expected) throws PresetFormatException {
            if (!take(expected)) fail("expected '" + expected + "'");
        }

        private <T> T fail(String message) throws PresetFormatException {
            throw new PresetFormatException(message + " at character " + position);
        }
    }
}
