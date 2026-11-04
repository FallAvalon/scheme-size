package org.schemetools.host;

import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Bounded, optimistic transactions over environmental layers, using vanilla tile RPCs. */
public final class ArenaTools {
    public static final int MAX_AREA = 4096;
    public enum Shape { RECTANGLE, CIRCLE }
    public enum Layer { FLOOR, OVERLAY, BLOCK, TERRAIN }

    public record Area(int x1, int y1, int x2, int y2, Shape shape) {
        public Area {
            if (shape == null || x1 < 0 || y1 < 0 || x2 < x1 || y2 < y1
                    || (long)(x2 - x1 + 1) * (y2 - y1 + 1) > MAX_AREA)
                throw new IllegalArgumentException("st.arena.error.area");
        }
        public boolean contains(int x, int y) {
            if (shape == Shape.RECTANGLE) return true;
            double rx = (x2 - x1 + 1) / 2d, ry = (y2 - y1 + 1) / 2d;
            double dx = (x - (x1 + x2) / 2d) / rx, dy = (y - (y1 + y2) / 2d) / ry;
            return dx * dx + dy * dy <= 1d;
        }
    }

    private record State(Block floor, Block overlay, Block block, mindustry.gen.Building building) {
        State(Tile tile) { this(tile.floor(), tile.overlay(), tile.block(), tile.build); }
        boolean matches(Tile tile) { return tile.build == building && floor == tile.floor() && overlay == tile.overlay() && block == tile.block(); }
        void set(Tile tile) {
            if (tile.floor() != floor || tile.overlay() != overlay) tile.setFloorNet(floor, overlay);
            if (tile.block() != block) tile.setNet(block, Team.derelict, 0);
        }
    }
    private record Change(int x, int y, State before, State after) { }
    public static final class Edit {
        private final Object world;
        private final List<Change> changes;
        private final Area area;
        private Edit(Object world, Area area, List<Change> changes) { this.world = world; this.area = area; this.changes = List.copyOf(changes); }
        public int count() { return changes.size(); }
        public Area area() { return area; }
    }

    private final HostActions authority;
    private final ArrayDeque<Edit> undo = new ArrayDeque<>();
    private long lastEdit;
    private final java.util.Map<Edit,WorldSnapshot> snapshots=new java.util.IdentityHashMap<>();
    public ArenaTools(HostActions authority) { this.authority = java.util.Objects.requireNonNull(authority); }
    public boolean godMode(){return authority.godMode();}

    public Edit preview(Area area, Layer layer, Block floor, Block overlay, Block block) {
        requireHost();
        if (area == null || layer == null || area.x2 >= Vars.world.width() || area.y2 >= Vars.world.height())
            throw new IllegalArgumentException("st.arena.error.area");
        if (layer == Layer.FLOOR || layer == Layer.TERRAIN) {
            if (!(floor instanceof Floor) || floor == Blocks.air || floor.isOverlay() || !registered(floor))
                throw new IllegalArgumentException("st.arena.error.content");
        }
        if (layer == Layer.OVERLAY || layer == Layer.TERRAIN) {
            if ((overlay != Blocks.air && !(overlay instanceof Floor && overlay.isOverlay())) || !registered(overlay))
                throw new IllegalArgumentException("st.arena.error.content");
        }
        if ((layer == Layer.BLOCK || layer == Layer.TERRAIN) && !environmentBlock(block))
            throw new IllegalArgumentException("st.arena.error.content");
        List<Change> changes = new ArrayList<>();
        for (int y = area.y1; y <= area.y2; y++) for (int x = area.x1; x <= area.x2; x++) {
            if (!area.contains(x, y)) continue;
            Tile tile = Vars.world.tile(x, y);
            if (tile == null || (!authority.godMode() && (layer == Layer.BLOCK || layer == Layer.TERRAIN)
                    && (tile.build != null || tile.block().size != 1 || tile.block().hasBuilding())))
                throw new IllegalStateException("st.arena.error.occupied");
            State before = new State(tile);
            State after = switch (layer) {
                case FLOOR -> new State(floor, before.overlay, before.block, before.building);
                case OVERLAY -> new State(before.floor, overlay, before.block, before.building);
                case BLOCK -> new State(before.floor, before.overlay, block, null);
                case TERRAIN -> new State(floor, overlay, block, null);
            };
            if (!before.equals(after)) changes.add(new Change(x, y, before, after));
        }
        return new Edit(Vars.world.tiles, area, changes);
    }

    public record TerrainCell(int x, int y, Block floor, Block overlay, Block obstacle) { }

    /** A sparse, multi-content paste. Null layer fields preserve the corresponding world layer. */
    public Edit previewCells(List<TerrainCell> cells) {
        requireHost();
        if (cells == null || cells.isEmpty() || cells.size() > MAX_AREA) throw new IllegalArgumentException("st.arena.error.area");
        int x1 = Integer.MAX_VALUE, y1 = Integer.MAX_VALUE, x2 = 0, y2 = 0;
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        List<Change> changes = new ArrayList<>();
        for (TerrainCell cell : cells) {
            Tile tile = Vars.world.tile(cell.x, cell.y);
            if (tile == null || !seen.add(tile.pos())) throw new IllegalArgumentException("st.arena.error.area");
            if (cell.floor != null && (!(cell.floor instanceof Floor) || cell.floor == Blocks.air || cell.floor.isOverlay() || !registered(cell.floor))
                    || cell.overlay != null && (!registered(cell.overlay) || cell.overlay != Blocks.air && !cell.overlay.isOverlay())
                    || cell.obstacle != null && !environmentBlock(cell.obstacle)) throw new IllegalArgumentException("st.arena.error.content");
            if (!authority.godMode() && cell.obstacle != null && (tile.build != null || tile.block().size != 1 || tile.block().hasBuilding()))
                throw new IllegalStateException("st.arena.error.occupied");
            State before = new State(tile);
            State after = new State(cell.floor == null ? before.floor : cell.floor, cell.overlay == null ? before.overlay : cell.overlay,
                cell.obstacle == null ? before.block : cell.obstacle, cell.obstacle == null ? before.building : null);
            if (!before.equals(after)) changes.add(new Change(cell.x, cell.y, before, after));
            x1 = Math.min(x1, cell.x); y1 = Math.min(y1, cell.y); x2 = Math.max(x2, cell.x); y2 = Math.max(y2, cell.y);
        }
        return new Edit(Vars.world.tiles, new Area(x1, y1, x2, y2, Shape.RECTANGLE), changes);
    }

    public int apply(Edit edit) {
        requireHost(); rate(); validate(edit, false);
        if (edit.count() == 0) return 0;
        WorldSnapshot snapshot=null;
        if(authority.godMode())snapshot=WorldSnapshot.capture(edit.changes.stream().map(c->Vars.world.tile(c.x,c.y)).toList());
        try{applyStates(edit,false);}catch(RuntimeException failure){if(snapshot!=null)try{snapshot.restore();}catch(RuntimeException rollback){failure.addSuppressed(rollback);}throw failure;}
        if(snapshot!=null)snapshots.put(edit,snapshot);
        undo.push(edit);
        while (undo.size() > 16) snapshots.remove(undo.removeLast());
        lastEdit = System.nanoTime();
        return edit.count();
    }

    public int undo() {
        requireHost(); rate();
        if (undo.isEmpty()) throw new IllegalStateException("st.arena.error.no-undo");
        Edit edit = undo.peek();
        if(authority.godMode()&&snapshots.containsKey(edit)){snapshots.get(edit).restore();snapshots.remove(edit);}
        else {validate(edit,true);applyStates(edit,true);}
        undo.pop();
        lastEdit = System.nanoTime();
        return edit.count();
    }

    public void clear() { undo.clear(); snapshots.clear(); lastEdit = 0; }
    private void requireHost() { if (!authority.isAvailable()) throw new IllegalStateException("st.error.authority"); }
    private void rate() { if(authority.godMode())return; if (lastEdit != 0 && System.nanoTime() - lastEdit < 250_000_000L) throw new IllegalStateException("st.arena.error.rate"); }
    private void validate(Edit edit, boolean reverse) {
        if (edit == null || edit.world != Vars.world.tiles || edit.count() > MAX_AREA)
            throw new IllegalStateException("st.arena.error.world");
        for (Change change : edit.changes) {
            Tile tile = Vars.world.tile(change.x, change.y);
            if (tile == null || (!authority.godMode() && !(reverse ? change.after : change.before).matches(tile)))
                throw new IllegalStateException("st.arena.error.conflict");
        }
    }
    private static void applyStates(Edit edit, boolean reverse) {
        List<Change> applied = new ArrayList<>();
        try {
            for (Change change : edit.changes) {
                applied.add(change);
                (reverse ? change.before : change.after).set(Vars.world.tile(change.x, change.y));
            }
        } catch (RuntimeException failure) {
            for (int i = applied.size() - 1; i >= 0; i--) {
                Change change = applied.get(i);
                try { (reverse ? change.after : change.before).set(Vars.world.tile(change.x, change.y)); }
                catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            }
            throw failure;
        }
    }
    private static boolean registered(Block block) { return block != null && Vars.content.blocks().contains(block, true); }
    public static boolean environmentBlock(Block block) {
        return registered(block) && block.size == 1 && !block.hasBuilding() && !block.privileged
            && (block == Blocks.air || (!block.isFloor() && !block.synthetic()));
    }
}
