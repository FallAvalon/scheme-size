package org.schemetools.schematic;

import arc.graphics.Pixmap;
import arc.struct.Seq;
import arc.struct.StringMap;
import mindustry.Vars;
import mindustry.game.Schematic;
import mindustry.game.Schematic.Stile;
import mindustry.logic.LExecutor;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.logic.LogicBlock.LogicLink;
import mindustry.world.blocks.logic.LogicDisplay;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Independent image compiler. Only vanilla display/processors/configuration bytes appear in its result. */
public final class ImageSchematic {
    public static final int MAX_SOURCE_PIXELS = 16 * 1024 * 1024, MAX_OUTPUT_PIXELS = 1024 * 1024;
    public record Options(LogicDisplay display, LogicBlock processor, int columns, int rows, boolean smooth, int colorStep) { }
    public record Result(Schematic schematic, List<String> programs, int rectangles) { }
    private record Job(int x, int y, String code) { }
    private ImageSchematic() { }

    public static Result read(Path file, Options options, BooleanSupplier cancelled) throws IOException {
        long length = Files.size(file);
        if (length < 10 || length > 16 * 1024 * 1024) throw new IOException("st.image.error.file");
        byte[] bytes;
        try (var input = Files.newInputStream(file)) { bytes = input.readNBytes(16 * 1024 * 1024 + 1); }
        if (bytes.length > 16 * 1024 * 1024) throw new IOException("st.image.error.file");
        int[] dimensions = dimensions(bytes);
        int width = dimensions[0], height = dimensions[1];
        if (width < 1 || height < 1 || width > 4096 || height > 4096 || (long) width * height > MAX_SOURCE_PIXELS)
            throw new IOException("st.image.error.size");
        check(cancelled);
        Pixmap image = null;
        try {
            image = new Pixmap(bytes);
            if (image.width != width || image.height != height) throw new IOException("st.image.error.png");
            return convert(image, file.getFileName().toString(), options, cancelled);
        } catch (RuntimeException failure) { throw new IOException("st.image.error.png", failure); }
        finally { if (image != null) image.dispose(); }
    }
    private static int[] dimensions(byte[] bytes) throws IOException {
        var header = ByteBuffer.wrap(bytes);
        if (bytes.length >= 33 && header.getLong(0) == 0x89504e470d0a1a0aL && header.getInt(8) == 13 && header.getInt(12) == 0x49484452)
            return new int[]{header.getInt(16), header.getInt(20)};
        if (bytes.length >= 54 && bytes[0] == 'B' && bytes[1] == 'M') {
            header.order(java.nio.ByteOrder.LITTLE_ENDIAN);
            int dib = header.getInt(14), offset = header.getInt(10), width = header.getInt(18), signedHeight = header.getInt(22);
            int compression = header.getInt(30), bits = header.getShort(28) & 65535;
            if (dib < 40 || dib > 124 || offset < 14 + dib || offset >= bytes.length || header.getShort(26) != 1
                    || signedHeight == Integer.MIN_VALUE || bits != 24 && bits != 32 || compression != 0 && compression != 3)
                throw new IOException("st.image.error.png");
            return new int[]{width, Math.abs(signedHeight)};
        }
        if (bytes.length > 4 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216) {
            int width = 0, height = 0, position = 2;
            while (position < bytes.length) {
                if ((bytes[position++] & 255) != 255) throw new IOException("st.image.error.png");
                while (position < bytes.length && (bytes[position] & 255) == 255) position++;
                if (position >= bytes.length) break;
                int marker = bytes[position++] & 255;
                if (marker == 218 || marker == 217) break;
                if (marker == 1 || marker >= 208 && marker <= 216) continue;
                if (position + 2 > bytes.length) break;
                int length = (bytes[position] & 255) * 256 + (bytes[position + 1] & 255);
                if (length < 2 || (long)position + length > bytes.length) throw new IOException("st.image.error.png");
                if (marker >= 192 && marker <= 194) {
                    if (length < 8) throw new IOException("st.image.error.png");
                    int h = (bytes[position + 3] & 255) * 256 + (bytes[position + 4] & 255);
                    int w = (bytes[position + 5] & 255) * 256 + (bytes[position + 6] & 255);
                    if (width != 0 && (width != w || height != h)) throw new IOException("st.image.error.png");
                    width = w; height = h;
                }
                position += length;
            }
            if (width > 0 && height > 0) return new int[]{width, height};
        }
        throw new IOException("st.image.error.png");
    }

    public static Result convert(Pixmap image, String name, Options options, BooleanSupplier cancelled) throws IOException {
        if (image == null || image.isDisposed() || options == null || options.display == null || options.processor == null
                || options.display.privileged || options.processor.privileged || options.processor.size != 1
                || options.columns < 1 || options.rows < 1 || options.columns > 10 || options.rows > 10
                || options.colorStep < 1 || options.colorStep > 64) throw new IOException("st.image.error.options");
        int pixels = options.display.displaySize, size = options.display.size;
        if (pixels < 1 || pixels > 512 || (long)pixels * pixels * options.columns * options.rows > MAX_OUTPUT_PIXELS)
            throw new IOException("st.image.error.size");
        int width = size * options.columns, height = size * options.rows;
        Seq<Stile> tiles = new Seq<>();
        List<Job> jobs = new ArrayList<>();
        int rectangles = 0;
        int limit = Math.min(800, LExecutor.maxInstructions - 4);
        if (limit < 8) throw new IOException("st.image.error.options");
        for (int row = 0; row < options.rows; row++) for (int column = 0; column < options.columns; column++) {
            check(cancelled);
            int dx = column * size + (size - 1) / 2, dy = row * size + (size - 1) / 2;
            tiles.add(new Stile(options.display, dx, dy, null, (byte)0));
            StringBuilder code = new StringBuilder();
            int instructions = 0, buffered = 0;
            for (int y = 0; y < pixels; y++) {
                check(cancelled);
                for (int x = 0; x < pixels;) {
                    int color = sample(image, column * pixels + x, row * pixels + y,
                        pixels * options.columns, pixels * options.rows, options.smooth, options.colorStep);
                    int end = x + 1;
                    while (end < pixels && sample(image, column * pixels + end, row * pixels + y,
                            pixels * options.columns, pixels * options.rows, options.smooth, options.colorStep) == color) end++;
                    if (instructions + 4 >= limit) {
                        code.append("drawflush display1\n"); jobs.add(new Job(dx, dy, code.toString()));
                        code.setLength(0); instructions = buffered = 0;
                    }
                    code.append("draw color ").append(color >>> 16 & 255).append(' ').append(color >>> 8 & 255)
                        .append(' ').append(color & 255).append(" 255 0 0\n");
                    code.append("draw rect ").append(x).append(' ').append(y).append(' ').append(end - x).append(" 1 0 0\n");
                    instructions += 2; buffered += 2; rectangles++; x = end;
                    if (buffered >= Math.min(200, LExecutor.maxGraphicsBuffer - 2)) {
                        code.append("drawflush display1\n"); instructions++; buffered = 0;
                    }
                }
            }
            if (!code.isEmpty()) { code.append("drawflush display1\n"); jobs.add(new Job(dx, dy, code.toString())); }
        }
        if (jobs.size() > 4096) throw new IOException("st.image.error.space");
        // Allocate nearest unoccupied cells outside the display rectangle. No overlapping
        // footprints or out-of-range links are emitted, even if that requires refusing an image.
        Set<Long> used = new HashSet<>();
        List<String> programs = new ArrayList<>();
        int minX = 0, minY = 0, maxX = width - 1, maxY = height - 1;
        for (Job job : jobs) {
            check(cancelled);
            float range = options.processor.range / Vars.tilesize + size / 2f;
            int radius = Math.min(128, (int)Math.ceil(range));
            int bx = 0, by = 0; double best = Double.POSITIVE_INFINITY;
            for (int y = job.y - radius; y <= job.y + radius; y++) for (int x = job.x - radius; x <= job.x + radius; x++) {
                if (x >= 0 && y >= 0 && x < width && y < height || used.contains(pack(x, y))) continue;
                double distance = (double)(x - job.x) * (x - job.x) + (double)(y - job.y) * (y - job.y);
                if (distance <= range * range && distance < best) { best = distance; bx = x; by = y; }
            }
            if (!Double.isFinite(best)) throw new IOException("st.image.error.space");
            byte[] config = LogicBlock.compress(job.code, Seq.with(new LogicLink(job.x - bx, job.y - by, "display1", true)));
            if (config.length > 16000) throw new IOException("st.image.error.code");
            used.add(pack(bx, by)); programs.add(job.code);
            tiles.add(new Stile(options.processor, bx, by, config, (byte)0));
            minX = Math.min(minX, bx); minY = Math.min(minY, by); maxX = Math.max(maxX, bx); maxY = Math.max(maxY, by);
        }
        for (Stile tile : tiles) { tile.x -= minX; tile.y -= minY; }
        StringMap tags = new StringMap(); tags.put("name", name == null ? "Image" : name.substring(0, Math.min(name.length(), 80)));
        Schematic schematic = new Schematic(tiles, tags, maxX - minX + 1, maxY - minY + 1);
        SchematicCodec.validate(schematic);
        return new Result(schematic, List.copyOf(programs), rectangles);
    }
    private static long pack(int x, int y) { return (long)x << 32 | y & 0xffffffffL; }
    private static void check(BooleanSupplier cancelled) throws IOException {
        if (Thread.currentThread().isInterrupted() || cancelled != null && cancelled.getAsBoolean()) throw new IOException("st.image.cancelled");
    }
    private static int sample(Pixmap image, int x, int y, int width, int height, boolean smooth, int step) {
        // Output y grows upwards in mlog; PNG rows grow downwards. Composite alpha over black.
        float sx = Math.max(0, Math.min(image.width - 1, (x + 0.5f) * image.width / width - 0.5f));
        float sy = Math.max(0, Math.min(image.height - 1, (height - y - 0.5f) * image.height / height - 0.5f));
        int color = 0;
        int x0 = (int)sx, y0 = (int)sy;
        for (int shift = 24; shift >= 8; shift -= 8) {
            float channel;
            if (!smooth) channel = channel(image.getRaw(Math.round(sx), Math.round(sy)), shift);
            else {
                float fx = sx - x0, fy = sy - y0;
                int x1 = Math.min(image.width - 1, x0 + 1), y1 = Math.min(image.height - 1, y0 + 1);
                channel = channel(image.getRaw(x0, y0), shift) * (1 - fx) * (1 - fy)
                    + channel(image.getRaw(x1, y0), shift) * fx * (1 - fy)
                    + channel(image.getRaw(x0, y1), shift) * (1 - fx) * fy
                    + channel(image.getRaw(x1, y1), shift) * fx * fy;
            }
            color = color << 8 | Math.min(255, Math.round(channel / step) * step);
        }
        return color;
    }
    private static float channel(int rgba, int shift) { return (rgba >>> shift & 255) * (rgba & 255) / 255f; }
}
