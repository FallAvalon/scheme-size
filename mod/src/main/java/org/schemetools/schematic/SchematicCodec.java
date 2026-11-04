package org.schemetools.schematic;

import arc.math.geom.Point2;
import arc.struct.Seq;
import arc.struct.StringMap;
import arc.util.io.Reads;
import arc.util.serialization.JsonReader;
import arc.util.serialization.JsonValue;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.ctype.Content;
import mindustry.ctype.ContentType;
import mindustry.game.Schematic;
import mindustry.game.Schematic.Stile;
import mindustry.game.Schematics;
import mindustry.io.SaveFileReader;
import mindustry.io.TypeIO;
import mindustry.world.Block;
import mindustry.world.blocks.distribution.ItemBridge;
import mindustry.world.blocks.distribution.MassDriver;
import mindustry.world.blocks.distribution.Sorter;
import mindustry.world.blocks.storage.Unloader;
import mindustry.world.blocks.sandbox.ItemSource;
import mindustry.world.blocks.sandbox.LiquidSource;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.legacy.LegacyBlock;
import mindustry.world.blocks.power.LightBlock;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.InflaterInputStream;

/** Independent bounded adapter for Mindustry's documented-by-source msch v0/v1 wire format.
 * mtls uses the same bytes; it is an extension used for files exceeding the vanilla reader's limit.
 * Configuration objects and export are handled by the exact game's TypeIO/Schematics APIs.
 */
public final class SchematicCodec {
    public static final int MAX_SIZE = 512, MAX_ENTRIES = 3 * MAX_SIZE * MAX_SIZE;
    public static final int MAX_FILE_BYTES = 16 * 1024 * 1024, MAX_INFLATED_BYTES = 32 * 1024 * 1024;
    private SchematicCodec() { }

    public static Schematic read(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 6 || bytes.length > MAX_FILE_BYTES
                || bytes[0] != 'm' || bytes[1] != 's' || bytes[2] != 'c' || bytes[3] != 'h'
                || bytes[4] < 0 || bytes[4] > 1) throw new IOException("st.schematic.error.header");
        int version = bytes[4];
        try (var input = new DataInputStream(new LimitedInput(new InflaterInputStream(new ByteArrayInputStream(bytes, 5, bytes.length - 5)), MAX_INFLATED_BYTES))) {
            int width = input.readShort(), height = input.readShort();
            dimensions(width, height);
            StringMap tags = new StringMap();
            int tagCount = input.readUnsignedByte();
            for (int i = 0; i < tagCount; i++) {
                String key = input.readUTF(), value = input.readUTF();
                if (key.length() > 256 || tags.containsKey(key)) throw new IOException("st.schematic.error.tags");
                tags.put(key, value);
            }
            TypeIO.ContentMapper mapper = contentMapper(tags.get("contentMap"));
            int dictionarySize = input.readUnsignedByte();
            Block[] dictionary = new Block[dictionarySize];
            for (int i = 0; i < dictionary.length; i++) {
                String name = input.readUTF();
                Block block = Vars.content.block(SaveFileReader.fallback.get(name, name));
                if (block == null || block instanceof LegacyBlock) throw new IOException("st.schematic.error.missing-content");
                dictionary[i] = block;
            }
            int count = input.readInt();
            if (count < 0 || count > Math.min(MAX_ENTRIES, (long)width * height * 3)) throw new IOException("st.schematic.error.entries");
            Seq<Stile> tiles = new Seq<>(count);
            Reads reads = new Reads(input);
            for (int i = 0; i < count; i++) {
                int type = input.readUnsignedByte(), packed = input.readInt();
                if (type >= dictionary.length) throw new IOException("st.schematic.error.dictionary");
                Block block = dictionary[type];
                Object config = version == 0 ? legacy(block, input.readInt(), Point2.x(packed), Point2.y(packed))
                    : TypeIO.readObject(reads, true, mapper);
                int rotation = input.readUnsignedByte();
                if (block != Blocks.air) tiles.add(new Stile(block, Point2.x(packed), Point2.y(packed), config, (byte)rotation));
            }
            if (input.read() != -1) throw new IOException("st.schematic.error.trailing");
            Schematic schematic = new Schematic(tiles, tags, width, height);
            String labels = tags.get("labels", "[]");
            JsonValue json = json(labels);
            if (!json.isArray() || json.size > 64) throw new IOException("st.schematic.error.tags");
            for (JsonValue label = json.child; label != null; label = label.next) {
                if (!label.isString() || label.asString().length() > 64) throw new IOException("st.schematic.error.tags");
                schematic.labels.add(label.asString());
            }
            validate(schematic);
            return schematic;
        } catch (RuntimeException failure) { throw new IOException("st.schematic.error.data", failure); }
    }

    public static Schematic readBase64(String text) throws IOException {
        if (text == null || text.length() > MAX_FILE_BYTES * 4L / 3 + 4096) throw new IOException("st.schematic.error.size");
        String compact = text.replaceAll("[\\r\\n\\t ]", "");
        try { return read(Base64.getDecoder().decode(compact)); }
        catch (IllegalArgumentException failure) { throw new IOException("st.schematic.error.base64", failure); }
    }

    public static byte[] write(Schematic schematic) throws IOException {
        validate(schematic);
        Set<Block> dictionary = new HashSet<>();
        for (Stile tile : schematic.tiles) dictionary.add(tile.block);
        // The 160.7 vanilla reader reads tile dictionary indices as signed bytes.
        if (dictionary.size() > 128) throw new IOException("st.schematic.error.dictionary");
        StringMap tags = new StringMap();
        for (var entry : schematic.tags) tags.put(entry.key, entry.value);
        Schematic detached = new Schematic(schematic.tiles.copy(), tags, schematic.width, schematic.height);
        detached.labels.addAll(schematic.labels);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (OutputStream output = new FilterOutputStream(bytes) {
            private int size;
            @Override public void write(int value) throws IOException {
                if (++size > MAX_FILE_BYTES) throw new IOException("st.schematic.error.size"); out.write(value);
            }
            @Override public void write(byte[] values, int offset, int count) throws IOException {
                if ((long)size + count > MAX_FILE_BYTES) throw new IOException("st.schematic.error.size"); size += count; out.write(values, offset, count);
            }
        }) { Schematics.write(detached, output); }
        catch (RuntimeException failure) { throw new IOException("st.schematic.error.data", failure); }
        byte[] encoded = bytes.toByteArray();
        // Never export a file that this bounded reader could not inflate on its next import.
        try (InputStream input = new LimitedInput(new InflaterInputStream(new ByteArrayInputStream(encoded, 5, encoded.length - 5)), MAX_INFLATED_BYTES)) {
            byte[] buffer = new byte[8192];
            while (input.read(buffer) >= 0) { /* count decoded bytes without allocating decoded objects */ }
        }
        return encoded;
    }
    public static String writeBase64(Schematic schematic) throws IOException { return Base64.getEncoder().encodeToString(write(schematic)); }

    public static void validate(Schematic schematic) throws IOException {
        if (schematic == null) throw new IOException("st.schematic.error.data");
        dimensions(schematic.width, schematic.height);
        if (schematic.tiles.size > Math.min(MAX_ENTRIES, (long)schematic.width * schematic.height * 3)) throw new IOException("st.schematic.error.entries");
        if (schematic.tags.size > 253 || schematic.labels.size > 64) throw new IOException("st.schematic.error.tags");
        Set<Long> occupied = new HashSet<>();
        for (Stile tile : schematic.tiles) {
            if (tile.block == null || !Vars.content.blocks().contains(tile.block, true) || tile.x < 0 || tile.y < 0
                    || tile.x >= schematic.width || tile.y >= schematic.height || tile.rotation < 0 || tile.rotation > 3)
                throw new IOException("st.schematic.error.entries");
            int layer = tile.block.isOverlay() ? 2 : tile.block instanceof Floor ? 1 : 0;
            long key = ((long)layer << 32) | tile.x + (long)tile.y * schematic.width;
            if (!occupied.add(key)) throw new IOException("st.schematic.error.duplicate");
        }
    }

    private static void dimensions(int width, int height) throws IOException {
        if (width < 1 || height < 1 || width > MAX_SIZE || height > MAX_SIZE) throw new IOException("st.schematic.error.dimensions");
    }
    private static Object legacy(Block block, int value, int x, int y) {
        if (block instanceof Sorter || block instanceof Unloader || block instanceof ItemSource) return Vars.content.item(value);
        if (block instanceof LiquidSource) return Vars.content.liquid(value);
        if (block instanceof ItemBridge || block instanceof MassDriver) return Point2.unpack(value).sub(x, y);
        if (block instanceof LightBlock) return value;
        return null;
    }
    private static TypeIO.ContentMapper contentMapper(String encoded) throws IOException {
        if (encoded == null) return null;
        JsonValue root = json(encoded);
        if (!root.isObject()) throw new IOException("st.schematic.error.data");
        Map<Integer, Map<Integer, Content>> mappings = new HashMap<>();
        for (JsonValue type = root.child; type != null; type = type.next) {
            int id = Integer.parseInt(type.name);
            if (id < 0 || id >= ContentType.all.length || !type.isObject() || type.size > 32768 || mappings.containsKey(id))
                throw new IOException("st.schematic.error.data");
            Map<Integer, Content> entries = new HashMap<>();
            mappings.put(id, entries);
            for (JsonValue value = type.child; value != null; value = value.next) {
                int contentId = value.asInt();
                Content content = Vars.content.getByName(ContentType.all[id], value.name);
                if (contentId < 0 || contentId > 32767 || content == null || entries.put(contentId, content) != null)
                    throw new IOException("st.schematic.error.missing-content");
            }
        }
        return (type, id) -> mappings.getOrDefault(type.ordinal(), Map.of()).get(id);
    }
    private static JsonValue json(String text) throws IOException {
        if (text.length() > 65535) throw new IOException("st.schematic.error.tags");
        boolean string = false, escape = false;
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (string) {
                if (escape) escape = false;
                else if (ch == '\\') escape = true;
                else if (ch == '"') string = false;
            } else if (ch == '"') string = true;
            else if (ch == '{' || ch == '[') { if (++depth > 8) throw new IOException("st.schematic.error.tags"); }
            else if (ch == '}' || ch == ']') { if (--depth < 0) throw new IOException("st.schematic.error.tags"); }
        }
        if (depth != 0 || string) throw new IOException("st.schematic.error.tags");
        return new JsonReader().parse(text);
    }
    private static final class LimitedInput extends FilterInputStream {
        private final long limit;
        private long used;
        LimitedInput(InputStream input, long limit) { super(input); this.limit = limit; }
        @Override public int read() throws IOException {
            int result = in.read(); if (result >= 0 && ++used > limit) throw new IOException("st.schematic.error.size"); return result;
        }
        @Override public int read(byte[] data, int offset, int count) throws IOException {
            int result = in.read(data, offset, (int)Math.min(count, Math.max(1, limit - used + 1)));
            if (result > 0 && (used += result) > limit) throw new IOException("st.schematic.error.size"); return result;
        }
    }
}
