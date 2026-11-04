package org.schemetools.client;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Rect;
import mindustry.Vars;
import mindustry.game.EventType.*;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.world.blocks.defense.OverdriveProjector;
import mindustry.world.blocks.defense.turrets.BaseTurret;
import mindustry.world.blocks.power.NuclearReactor;

import java.util.ArrayList;
import java.util.IdentityHashMap;

/** Local renderer extensions. No rule changes or network messages are sent. */
public final class VisualLayers {
    public enum Option {
        GRID("grid"), RULER("ruler"), UNIT_BARS("unit-bars"), UNIT_RANGE("unit-range"),
        TURRET_RANGE("turret-range"), REACTOR_RANGE("reactor-range"), BOOST_RANGE("boost-range"),
        POWER_BARS("power-bars"), FLOOR_XRAY("floor-xray"), HIDE_UNITS("hide-units"),
        DISPLAY_BORDERLESS("display-borderless"), BLOCK_STATUS("block-status");
        public final String key;
        Option(String key) { this.key = "schemesize-visual-" + key; }
        public String labelKey() { return "st.visual." + name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'); }
    }
    private final Rect view = new Rect();
    private final ArrayList<Unit> hidden = new ArrayList<>();

    public static boolean enabled(Option option) { return Core.settings.getBool("schemesize-module-visual", true) && Core.settings.getBool(option.key, false); }
    public static void enabled(Option option, boolean value) { Core.settings.put(option.key, value); }
    public void install() {
        if (Vars.headless) return;
        // Remove units only for the renderer's draw pass, then restore their native group indices.
        // Update/reset restoration is a fallback if a frame exits before postDraw.
        Events.run(Trigger.preDraw, this::hideForFrame);
        Events.run(Trigger.postDraw, this::restore);
        Events.run(Trigger.update, this::restore);
        Events.on(ResetEvent.class, event -> restore());
        Events.run(Trigger.draw, this::draw);
        Events.on(ClientLoadEvent.class, event -> Vars.ui.hudGroup.fill(root -> {
            root.bottom().left().marginLeft(8).marginBottom(70);
            root.visible(() -> enabled(Option.RULER) && Vars.state.isGame() && !Vars.player.dead());
            root.label(() -> {
                float dx = (Core.input.mouseWorldX() - Vars.player.x) / Vars.tilesize;
                float dy = (Core.input.mouseWorldY() - Vars.player.y) / Vars.tilesize;
                return Core.bundle.format("st.visual.ruler-distance", (int)dx, (int)dy, Math.round(Mathf.dst(dx, dy) * 10f) / 10f);
            }).left();
        }));
    }
    private void hideForFrame() {
        restore();
        if (!enabled(Option.HIDE_UNITS) || !Vars.state.isGame()) return;
        for (int i = Groups.draw.size() - 1; i >= 0; i--) {
            var drawable = Groups.draw.index(i);
            if (drawable instanceof Unit unit) {
                Groups.draw.removeIndex(unit, i);
                unit.setIndex__draw(-1);
                hidden.add(unit);
            }
        }
    }
    private void restore() {
        if (hidden.isEmpty()) return;
        var present = new IdentityHashMap<Unit, Boolean>();
        for (var drawable : Groups.draw) if (drawable instanceof Unit unit) present.put(unit, Boolean.TRUE);
        for (Unit unit : hidden) if (unit.isAdded() && !present.containsKey(unit)) unit.setIndex__draw(Groups.draw.addIndex(unit));
        hidden.clear();
    }
    private void draw() {
        if (!Vars.state.isGame()) return;
        boolean grid = enabled(Option.GRID), ruler = enabled(Option.RULER), floor = enabled(Option.FLOOR_XRAY);
        boolean unitBars = enabled(Option.UNIT_BARS), unitRange = enabled(Option.UNIT_RANGE);
        boolean turret = enabled(Option.TURRET_RANGE), reactor = enabled(Option.REACTOR_RANGE);
        boolean boost = enabled(Option.BOOST_RANGE), power = enabled(Option.POWER_BARS);
        boolean displays = enabled(Option.DISPLAY_BORDERLESS), status = enabled(Option.BLOCK_STATUS);
        if (!(grid || ruler || floor || unitBars || unitRange || turret || reactor || boost || power || displays || status)) return;
        Core.camera.bounds(view);
        float oldZ = Draw.z();
        Draw.z(Layer.overlayUI - 0.2f);
        if (floor) drawFloor();
        if (grid) drawGrid();
        if (ruler && !Vars.player.dead()) {
            Draw.color(Pal.accent); Lines.stroke(1.2f);
            Lines.line(Vars.player.x, Vars.player.y, Core.input.mouseWorldX(), Core.input.mouseWorldY());
        }
        if (unitBars || unitRange) {
            int[] count = {0};
            Groups.unit.intersect(view.x, view.y, view.width, view.height, unit -> {
                if (count[0]++ >= 500 || Vars.net.client() && unit.inFogTo(Vars.player.team())) return;
                if (unitRange && unit.range() > 0) { Draw.color(Color.scarlet, 0.6f); Lines.stroke(0.8f); Lines.circle(unit.x, unit.y, unit.range()); }
                if (unitBars) {
                    float width = Math.max(12, Math.min(50, unit.hitSize));
                    float y = unit.y + unit.hitSize / 2 + 4;
                    bar(unit.x, y, width, Mathf.clamp(unit.healthf()), Color.lime);
                    if (unit.type.ammoCapacity > 0) bar(unit.x, y - 3, width, Mathf.clamp(unit.ammof()), Color.orange);
                }
            });
        }
        if (turret || reactor || boost || power || status) {
            int[] count = {0};
            Vars.indexer.allBuildings(Core.camera.position.x, Core.camera.position.y,
                Math.max(view.width, view.height), building -> {
                    if (count[0]++ >= 500 || !visible(building)) return;
                    float radius = 0;
                    if (turret && building.block instanceof BaseTurret type) radius = type.range;
                    if (reactor && building.block instanceof NuclearReactor type) radius = type.explosionRadius * Vars.tilesize;
                    if (boost && building.block instanceof OverdriveProjector type) radius = type.range;
                    if (radius > 0) { Draw.color(Color.orange, 0.65f); Lines.stroke(0.8f); Lines.circle(building.x, building.y, radius); }
                    if (power && building.power != null && building.power.graph != null)
                        bar(building.x, building.y + building.block.size * Vars.tilesize / 2f + 3f,
                            18f, Mathf.clamp(building.power.graph.getSatisfaction()), Color.yellow);
                    if (status && building.block.hasConsumers) { building.drawStatus(); building.drawDisabled(); }
                });
        }
        if (displays && Vars.renderer.drawDisplays) {
            float overlayZ = Draw.z(); Draw.z(Layer.blockOver + 0.01f);
            int count = 0;
            var region = new arc.graphics.g2d.TextureRegion();
            for (var display : mindustry.world.blocks.logic.LogicDisplay.displays) {
                if (count >= 128) break;
                if (display == null || !display.isValid() || !visible(display) || !view.contains(display.x, display.y)
                        || display.rootDisplay.buffer == null) continue;
                count++; display.getBufferRegion(region); Draw.color();
                Draw.rect(region, display.x, display.y, display.block.size * Vars.tilesize, display.block.size * Vars.tilesize);
            }
            Draw.z(overlayZ);
        }
        Draw.reset(); Draw.z(oldZ);
    }
    private void drawGrid() {
        int left = Math.max(0, (int)Math.floor(view.x / Vars.tilesize)), bottom = Math.max(0, (int)Math.floor(view.y / Vars.tilesize));
        int right = Math.min(Vars.world.width(), (int)Math.ceil((view.x + view.width) / Vars.tilesize));
        int top = Math.min(Vars.world.height(), (int)Math.ceil((view.y + view.height) / Vars.tilesize));
        int step = Math.max(1, Math.max(right - left, top - bottom) / 512 + 1);
        Draw.color(Color.white, 0.23f); Lines.stroke(0.5f);
        for (int x = left; x <= right; x += step) Lines.line(x * Vars.tilesize - Vars.tilesize / 2f, bottom * Vars.tilesize, x * Vars.tilesize - Vars.tilesize / 2f, top * Vars.tilesize);
        for (int y = bottom; y <= top; y += step) Lines.line(left * Vars.tilesize, y * Vars.tilesize - Vars.tilesize / 2f, right * Vars.tilesize, y * Vars.tilesize - Vars.tilesize / 2f);
    }
    private void drawFloor() {
        int left = Math.max(0, (int)Math.floor(view.x / Vars.tilesize)), bottom = Math.max(0, (int)Math.floor(view.y / Vars.tilesize));
        int right = Math.min(Vars.world.width() - 1, (int)Math.ceil((view.x + view.width) / Vars.tilesize));
        int top = Math.min(Vars.world.height() - 1, (int)Math.ceil((view.y + view.height) / Vars.tilesize));
        int step = Math.max(1, (int)Math.ceil(Math.sqrt(Math.max(1L, (long)(right - left + 1) * (top - bottom + 1)) / 4096d)));
        for (int x = left; x <= right; x += step) for (int y = bottom; y <= top; y += step) {
            var tile = Vars.world.tile(x, y);
            if (tile.build == null || !visible(tile.build)) continue;
            tile.floor().drawBase(tile);
        }
    }
    private static void bar(float x, float y, float width, float value, Color color) {
        Draw.color(Color.darkGray); Fill.crect(x - width / 2, y, width, 2);
        Draw.color(color); Fill.crect(x - width / 2, y, width * value, 2);
    }
    private static boolean visible(mindustry.gen.Building building) {
        return !Vars.net.client() || building.isDiscovered(Vars.player.team()) && (!Vars.state.rules.fog
            || building.team == Vars.player.team() || Vars.fogControl.isVisibleTile(Vars.player.team(), building.tileX(), building.tileY()));
    }
}
