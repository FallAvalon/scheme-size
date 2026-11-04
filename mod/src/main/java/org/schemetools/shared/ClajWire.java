package org.schemetools.shared;

import mindustry.io.TypeIO;
import mindustry.net.ArcNetProvider.PacketSerializer;

import java.net.URI;
import java.nio.ByteBuffer;

/** Independent adapter for the legacy CLaJ string envelope (-3 + native TypeIO string).
 * Game payloads are preserved byte-for-byte; no received game packet is handled here. */
public final class ClajWire extends PacketSerializer {
    public record Frame(byte[] data) { }
    public record Link(String key, String host, int port) {
        public String text() { return key + "#" + (host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host) + ":" + port; }
    }
    public static Link parse(String text) {
        if (text == null || text.length() > 400) throw new IllegalArgumentException("st.claj.error.link");
        String[] parts = text.trim().split("#", -1);
        if (parts.length != 2 || !parts[0].matches("CLaJ[A-Za-z0-9_+=/-]{42}")) throw new IllegalArgumentException("st.claj.error.link");
        URI address;
        try { address = URI.create("tcp://" + parts[1]); }
        catch (IllegalArgumentException failure) { throw new IllegalArgumentException("st.claj.error.link"); }
        if (address.getHost() == null || address.getHost().length() > 253 || address.getPort() < 1 || address.getPort() > 65535
                || address.getUserInfo() != null || address.getRawQuery() != null || address.getRawFragment() != null
                || !address.getRawPath().isEmpty()) throw new IllegalArgumentException("st.claj.error.link");
        return new Link(parts[0], address.getHost(), address.getPort());
    }
    public static ByteBuffer command(String value) {
        if (value == null || value.length() > 256) throw new IllegalArgumentException("st.claj.error.link");
        ByteBuffer data = ByteBuffer.allocate(1024); data.put((byte)-3); TypeIO.writeString(data, value); data.flip(); return data;
    }
    @Override public void write(ByteBuffer buffer, Object value) {
        if (value instanceof Frame frame) {
            if (frame.data.length > 65536) throw new IllegalArgumentException("st.claj.error.data");
            buffer.put(frame.data);
        } else if (value instanceof String command) buffer.put(command(command));
        else super.write(buffer, value);
    }
    @Override public Object read(ByteBuffer buffer) {
        if (buffer.remaining() == 0 || buffer.remaining() > 65536) throw new IllegalArgumentException("st.claj.error.data");
        if (buffer.get(buffer.position()) == (byte)-3) {
            buffer.get(); String value = TypeIO.readString(buffer);
            if (buffer.hasRemaining() || value == null || value.length() > 256 || value.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("st.claj.error.data");
            return value;
        }
        if (buffer.get(buffer.position()) == (byte)-2) return super.read(buffer);
        byte[] raw = new byte[buffer.remaining()]; buffer.get(raw); return new Frame(raw);
    }
}
