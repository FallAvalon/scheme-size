package org.schemetools.client;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.geom.Rect;
import mindustry.Vars;
import mindustry.ai.Pathfinder;
import mindustry.game.EventType.*;
import mindustry.gen.PathTile;
import mindustry.graphics.Layer;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.meta.BlockFlag;
import org.schemetools.shared.GridRoutes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/** Common Plus/God local forecast. Workers only receive detached integer grids. No network calls. */
public final class RouteOverlay {
    private static final int MAX_SEGMENTS = 12000;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Scheme-Size route forecast"); thread.setDaemon(true); return thread;
    });
    private Future<GridRoutes.Result> pending;
    private GridRoutes.Result result;
    private boolean enabled, refreshNeeded;
    private int costType = Pathfinder.costGround;
    private Object world;
    private int width, height, cursor;
    private int[] costs, starts, targets;
    private long revision, snapshotRevision, changedAt;
    private int teamId;
    private String status = "st.routes.off";
    private final Rect view = new Rect();

    public void install() {
        if (Vars.headless) return;
        enabled = Core.settings.getBool("schemesize-routes", false);
        Events.on(TileChangeEvent.class, event -> invalidate());
        Events.on(TileFloorChangeEvent.class, event -> invalidate());
        Events.on(TileOverlayChangeEvent.class, event -> invalidate());
        Events.on(WorldLoadEvent.class, event -> invalidate());
        Events.on(ResetEvent.class, event -> { clear(); refreshNeeded = enabled; });
        Events.run(Trigger.update, this::update);
        Events.run(Trigger.draw, this::draw);
        refreshNeeded = enabled;
    }

    public void enabled(boolean value) {
        enabled = value;
        Core.settings.put("schemesize-routes", value);
        clear(); refreshNeeded = value;
        if (!value) status = "st.routes.off";
    }
    public boolean enabled() { return enabled; }
    public int costType() { return costType; }
    public void costType(int value) {
        if (value < 0 || value >= Pathfinder.costTypes.size) throw new IllegalArgumentException("st.error.input");
        costType = value; refresh();
    }
    public void refresh() { clear(); refreshNeeded = enabled; changedAt = 0; }
    public String status() { if(!enabled)return Core.bundle.get("st.routes.off");if(costs!=null)return Core.bundle.format("st.routes.progress",cursor*100/Math.max(1,costs.length));return Core.bundle.get(status); }
    public GridRoutes.Result result() { return result; }

    private void invalidate() {
        revision++;
        changedAt = System.nanoTime();
        if (enabled) { clear(); refreshNeeded = true; status = "st.routes.changed"; }
    }
    private void clear() {
        if (pending != null) pending.cancel(true);
        pending = null; result = null; costs = null; world = null;
    }
    private void begin() {
        refreshNeeded = false;
        long count = (long)Vars.world.width() * Vars.world.height();
        if (count < 1 || count > GridRoutes.MAX_CELLS) { status = "st.routes.too-large"; return; }
        width = Vars.world.width(); height = Vars.world.height(); world = Vars.world.tiles;
        teamId = Vars.state.rules.waveTeam.id;
        List<Integer> spawn = new ArrayList<>(), goals = new ArrayList<>();
        for (Tile tile : Vars.spawner.getSpawns()) if (spawn.size() < 32) spawn.add(tile.array());
        for (var core : Vars.indexer.getEnemy(Vars.state.rules.waveTeam, BlockFlag.core)) {
            if (goals.size() >= 1024) break;
            if (Vars.net.client() && Vars.state.rules.fog && !core.isDiscovered(Vars.player.team())) continue;
            goals.add(core.tile.array());
        }
        if (spawn.isEmpty()) { status = "st.routes.no-spawns"; return; }
        if (goals.isEmpty()) { status = "st.routes.no-targets"; return; }
        starts = spawn.stream().mapToInt(Integer::intValue).toArray();
        targets = goals.stream().mapToInt(Integer::intValue).toArray();
        snapshotRevision = revision; cursor = 0;
        costs = new int[(int)count];
        status = "st.routes.snapshot";
    }
    private void update() {
        if (!enabled || !Vars.state.isGame() || Vars.world.isGenerating()) return;
        if (world != null && (world != Vars.world.tiles || teamId != Vars.state.rules.waveTeam.id)) invalidate();
        if (refreshNeeded && System.nanoTime() - changedAt >= 500_000_000L) begin();
        if (costs != null) {
            if (snapshotRevision != revision) { invalidate(); return; }
            long began = System.nanoTime();
            int end = Math.min(costs.length, cursor + 4096);
            Pathfinder.PathCost metric = Pathfinder.costTypes.get(costType);
            while (cursor < end) {
                Tile tile = Vars.world.tiles.geti(cursor);
                boolean unknown = Vars.net.client() && Vars.state.rules.fog && !Vars.fogControl.isDiscovered(Vars.player.team(), tile.x, tile.y);
                costs[cursor++] = unknown ? -1 : metric.getCost(teamId, packReadOnly(tile));
                if ((cursor & 127) == 0 && System.nanoTime() - began > 2_000_000L) break;
            }
            if (cursor == costs.length) {
                int[] grid = costs; costs = null;
                int w = width, h = height;
                int[] from = starts, to = targets;
                pending = worker.submit(() -> GridRoutes.calculate(w, h, grid, from, to, 1_048_576, 8192, 3_000_000_000L));
                status = "st.routes.calculating";
            }
        }
        if (pending != null && pending.isDone()) {
            try {
                result = pending.get();
                status = result.state() == GridRoutes.State.COMPLETE ? "st.routes.ready" : "st.routes.limited";
            } catch (InterruptedException failure) { Thread.currentThread().interrupt(); status = "st.routes.failed"; }
            catch (ExecutionException | CancellationException failure) { status = "st.routes.failed"; }
            pending = null;
        }
    }

    /** Uses the game's public bit layout and cost functions without touching its mutable path cache. */
    public static int packReadOnly(Tile tile) {
        boolean liquidNeighbour = false, solidNeighbour = false, legsNeighbour = false, landNeighbour = false;
        boolean deepNeighbour = tile.isDeep(), allDeep = tile.isDeep();
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
            if (dx == 0 && dy == 0) continue;
            Tile neighbour = Vars.world.tile(tile.x + dx, tile.y + dy);
            if (neighbour == null) continue;
            allDeep &= neighbour.isDeep();
            if (dx != 0 && dy != 0) continue;
            liquidNeighbour |= neighbour.floor().isLiquid && neighbour.isDeep();
            solidNeighbour |= neighbour.solid() && !neighbour.block().teamPassable;
            legsNeighbour |= neighbour.legSolid();
            landNeighbour |= !neighbour.floor().isLiquid;
            deepNeighbour |= neighbour.isDeep();
        }
        int health = tile.build != null && tile.solid() && !(tile.block() instanceof CoreBlock)
            ? Math.min(80, Math.max(0, (int)(tile.build.health / 40))) : 0;
        int team = tile.getTeamID();
        if (team == 0 && tile.build != null && Vars.state.rules.coreCapture) team = 255;
        return PathTile.get(health, team, tile.solid(), tile.floor().isLiquid && !tile.block().solid,
            tile.legSolid(), liquidNeighbour, landNeighbour, solidNeighbour, legsNeighbour,
            tile.isDeep(), tile.floor().damages(), allDeep, deepNeighbour, tile.block().teamPassable);
    }

    private void draw() {
        if (!enabled || result == null || !Vars.state.isGame() || world != Vars.world.tiles) return;
        Core.camera.bounds(view);
        float z = Draw.z();
        Draw.z(Layer.overlayUI);
        Lines.stroke(1f);
        int drawn = 0, index = 0;
        for (GridRoutes.Route route : result.routes()) {
            Draw.color(index++ % 2 == 0 ? Color.cyan : Color.orange, 0.8f);
            int[] cells = route.cells();
            for (int i = 1; i < cells.length; i++) {
                float x = cells[i] % width * Vars.tilesize, y = cells[i] / width * Vars.tilesize;
                float px = cells[i - 1] % width * Vars.tilesize, py = cells[i - 1] / width * Vars.tilesize;
                if (!view.contains(x, y) && !view.contains(px, py)) continue;
                Lines.line(px, py, x, y);
                if (++drawn >= MAX_SEGMENTS) break;
            }
            if (drawn >= MAX_SEGMENTS) break;
        }
        Draw.reset(); Draw.z(z);
    }
}
