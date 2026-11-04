package org.schemetools.client;

import arc.Core;
import arc.Events;
import arc.net.Client;
import arc.net.Connection;
import arc.net.DcReason;
import arc.net.NetListener;
import mindustry.Vars;
import mindustry.game.EventType.*;
import org.schemetools.shared.ClajWire;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Legacy relay rooms bridge to the real loopback host socket, never to a private NetProvider listener. */
public final class ClajService {
    public static final class Room implements AutoCloseable {
        private final String host;
        private final int port, localPort;
        private final Client control = client();
        private final List<Bridge> bridges = new CopyOnWriteArrayList<>();
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile String key;
        private double bridgeTokens = 16;
        private long tokenTime = System.nanoTime();
        private Room(String host, int port, int localPort, Consumer<String> status) {
            this.host = host; this.port = port; this.localPort = localPort;
            control.addListener(new NetListener() {
                @Override public void connected(Connection connection) { connection.sendTCP("new"); }
                @Override public void disconnected(Connection connection, DcReason reason) { close(); Core.app.post(() -> status.accept("st.claj.closed")); }
                @Override public void received(Connection connection, Object object) {
                    if (!(object instanceof String value) || closed.get()) return;
                    if (value.startsWith("CLaJ") && key == null) {
                        try { key = ClajWire.parse(value + "#" + authority()).key(); Core.app.post(() -> status.accept(link())); }
                        catch (RuntimeException failure) { close(); }
                    } else if (value.equals("new") && key != null && bridges.size() < 16) {
                        long now = System.nanoTime(); bridgeTokens = Math.min(16, bridgeTokens + (now - tokenTime) / 1_000_000_000d * 5); tokenTime = now;
                        if (bridgeTokens < 1) return;
                        bridgeTokens--;
                        Bridge bridge = new Bridge(); bridges.add(bridge);
                        if (closed.get()) { bridge.close(); return; }
                        daemon(() -> {
                            try { bridge.connect(); } catch (Exception failure) { bridge.close(); }
                        });
                    }
                }
            });
        }
        private String authority() { return (host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host) + ":" + port; }
        public String link() { return key == null ? "" : key + "#" + authority(); }
        public boolean closed() { return closed.get(); }
        public int guests() { return bridges.size(); }
        @Override public void close() {
            if (!closed.compareAndSet(false, true)) return;
            control.close(); control.stop(); bridges.forEach(Bridge::close); bridges.clear();
        }
        private final class Bridge implements AutoCloseable {
            private final Client remote = client(), local = client();
            private final ArrayDeque<ClajWire.Frame> toRemote = new ArrayDeque<>(), toLocal = new ArrayDeque<>();
            private final AtomicBoolean stopped = new AtomicBoolean();
            private Bridge() {
                remote.addListener(listener(local, toLocal, false)); local.addListener(listener(remote, toRemote, true));
            }
            private NetListener listener(Client destination, ArrayDeque<ClajWire.Frame> queue, boolean localSide) {
                return new NetListener() {
                    @Override public void connected(Connection connection) {
                        if (!localSide) connection.sendTCP("host" + key);
                        synchronized (Bridge.this) {
                            var pending = localSide ? toLocal : toRemote;
                            while (!pending.isEmpty()) connection.sendTCP(pending.removeFirst());
                        }
                    }
                    @Override public void disconnected(Connection connection, DcReason reason) { close(); }
                    @Override public void received(Connection connection, Object object) {
                        if (!(object instanceof ClajWire.Frame frame) || stopped.get()) return;
                        try {
                            synchronized (Bridge.this) {
                                if (destination.isConnected()) destination.sendTCP(frame);
                                else if (queue.size() < 8) queue.addLast(frame);
                                else close();
                            }
                        } catch (RuntimeException failure) { close(); }
                    }
                };
            }
            private void connect() throws IOException {
                if (closed.get() || stopped.get() || !Vars.net.server()) { close(); return; }
                daemon(() -> { try { remote.run(); } catch (RuntimeException failure) { close(); } });
                daemon(() -> { try { local.run(); } catch (RuntimeException failure) { close(); } });
                remote.connect(5000, host, port, port);
                if (closed.get() || stopped.get()) { remote.close(); remote.stop(); close(); return; }
                local.connect(5000, "127.0.0.1", localPort, localPort);
                if (closed.get() || stopped.get()) { local.close(); local.stop(); remote.close(); remote.stop(); }
            }
            @Override public void close() {
                if (!stopped.compareAndSet(false, true)) return;
                remote.close(); remote.stop(); local.close(); local.stop(); bridges.remove(this);
                synchronized (this) { toLocal.clear(); toRemote.clear(); }
            }
        }
    }
    private final List<Room> rooms = new CopyOnWriteArrayList<>();
    public void install() {
        if (Vars.headless) return;
        Events.on(ResetEvent.class, event -> closeAll());
        Events.run(Trigger.update, () -> { if (!Vars.net.server()) closeAll(); rooms.removeIf(Room::closed); });
    }
    public List<Room> rooms() { return List.copyOf(rooms); }
    public void closeAll() { rooms.forEach(Room::close); rooms.clear(); }
    public Room create(String host, int port, int localPort, Consumer<String> status) {
        if (!Vars.net.server() || Vars.headless || rooms.size() >= 4 || localPort < 1 || localPort > 65535)
            throw new IllegalStateException("st.claj.error.host");
        ClajWire.parse("CLaJ" + "a".repeat(42) + "#" + (host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host) + ":" + port);
        Room room = new Room(host, port, localPort, status); rooms.add(room);
        arc.util.Time.runTask(600f, () -> { if (!room.closed() && room.key == null) { room.close(); status.accept("st.claj.error.network"); } });
        daemon(() -> {
            try {
                daemon(() -> { try { room.control.run(); } catch (RuntimeException failure) {
                    if (!room.closed()) { room.close(); Core.app.post(() -> status.accept("st.claj.error.network")); }
                } });
                room.control.connect(5000, host, port, port); if (room.closed()) room.control.close();
            }
            catch (Exception failure) { room.close(); Core.app.post(() -> status.accept("st.claj.error.network")); }
        });
        return room;
    }
    public static void join(ClajWire.Link link) {
        if (Vars.headless || link == null || Vars.net.server()) throw new IllegalStateException("st.claj.error.host");
        ClajWire.Link checked = ClajWire.parse(link.text());
        String name = Vars.player.name;
        if (name == null || name.isBlank()) name = Core.settings.getString("name", "");
        if (name.isBlank()) throw new IllegalArgumentException("st.claj.error.name");
        Vars.netClient.disconnectQuietly(); Vars.logic.reset(); Vars.net.reset(); Vars.netClient.beginConnecting();
        Vars.player.name = name;
        Vars.net.connect(checked.host(), checked.port(), () -> Vars.net.send(ClajWire.command("join" + checked.key()), true));
    }
    private static Client client() { return new Client(1024 * 1024, 65536, new ClajWire()); }
    private static void daemon(Runnable task) { Thread thread = new Thread(task, "scheme-size-claj"); thread.setDaemon(true); thread.start(); }
}
