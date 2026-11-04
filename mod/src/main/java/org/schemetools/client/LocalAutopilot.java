package org.schemetools.client;

import arc.Core;
import arc.Events;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import mindustry.Vars;
import mindustry.entities.units.BuildPlan;
import mindustry.game.EventType.*;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.Build;
import mindustry.world.Tile;
import mindustry.input.Binding;

import java.util.Locale;
import java.util.Objects;

/** Local player input assistance. Server-bound transfers use vanilla requests and remain subject to server validation. */
public final class LocalAutopilot {
    public enum Mode { OFF, MINE, FOLLOW, CURSOR, CIRCLE, ASSIST, REPAIR, DEMOLISH }
    private final Vec2 movement = new Vec2();
    private Mode mode = Mode.OFF;
    private Item item;
    private Block demolitionBlock;
    private String targetId;
    private float centerX, centerY, circleRadius = 80;
    private long nextAction, nextScan;
    private Tile ore;
    private Building targetBuilding;
    private Unit controlled;
    private String status = "st.ai.off";

    public void install() {
        if (Vars.headless) return;
        Events.on(WorldLoadEvent.class, event -> stop());
        Events.on(ResetEvent.class, event -> stop());
        // ClientLauncher updates input before the renderer and sends client snapshots after it.
        Events.run(Trigger.preDraw, this::update);
        Events.on(ClientLoadEvent.class, event -> Vars.ui.hudGroup.fill(root -> {
            root.bottom().left().marginLeft(8).marginBottom(100);
            root.visible(() -> mode != Mode.OFF && Vars.state.isGame());
            root.label(() -> Core.bundle.format("st.ai.active", Core.bundle.get("st.ai.mode." + mode.name().toLowerCase(Locale.ROOT))));
            root.button(Core.bundle.get("st.ai.stop"), this::stop).height(40f);
        }));
    }
    public void start(Mode mode, Item item, Player target, float centerX, float centerY, float radius, Block demolitionBlock) {
        if (mode == null || mode == Mode.OFF) { stop(); return; }
        if (!Vars.state.isGame() || Vars.player.dead() || !Float.isFinite(centerX) || !Float.isFinite(centerY)
                || !Float.isFinite(radius) || radius < Vars.tilesize || radius > 100 * Vars.tilesize)
            throw new IllegalArgumentException("st.ai.error.settings");
        Unit unit = Vars.player.unit();
        if (mode == Mode.MINE && (!unit.canMine() || item != null && (!Vars.content.items().contains(item, true) || !unit.canMine(item))))
            throw new IllegalArgumentException("st.ai.error.mining");
        if ((mode == Mode.FOLLOW || mode == Mode.ASSIST) && (target == null || target == Vars.player
                || target.team() != Vars.player.team() || !Groups.player.contains(p -> p == target)))
            throw new IllegalArgumentException("st.ai.error.player");
        if (mode == Mode.ASSIST && !unit.canBuild()) throw new IllegalArgumentException("st.ai.error.build");
        if (mode == Mode.REPAIR && !unit.type.canHeal) throw new IllegalArgumentException("st.ai.error.repair");
        if (mode == Mode.DEMOLISH && (demolitionBlock == null || !Vars.content.blocks().contains(demolitionBlock, true)
                || demolitionBlock.privileged || !unit.type.hasWeapons())) throw new IllegalArgumentException("st.ai.error.demolish");
        stop();
        this.mode = mode; this.item = item; this.targetId = target == null ? null : org.schemetools.shared.PlayerKey.of(target);
        this.centerX = centerX; this.centerY = centerY; this.circleRadius = radius; this.demolitionBlock = demolitionBlock;
        controlled = unit; status = "st.ai.working"; nextAction = nextScan = 0;
        if (item != null) Core.settings.put("schemesize-ai-item", item.name);
        Core.settings.put("schemesize-ai-radius", radius / Vars.tilesize);
    }
    public void stop() {
        if (controlled != null && controlled.isAdded()) {
            if (mode == Mode.MINE) controlled.mineTile = null;
            if (mode == Mode.REPAIR || mode == Mode.DEMOLISH) {
                controlled.controlWeapons(false);
                if (Vars.player != null && Vars.player.unit() == controlled) Vars.player.shooting = false;
            }
        }
        mode = Mode.OFF; controlled = null; ore = null; targetBuilding = null; status = "st.ai.off";
    }
    public Mode mode() { return mode; }
    public String status() { return Core.bundle.get(status); }
    private Player target() {
        for (Player player : Groups.player.copy()) if (Objects.equals(org.schemetools.shared.PlayerKey.of(player), targetId)) return player;
        return null;
    }
    private void update() {
        if (mode == Mode.OFF) return;
        if (!Vars.state.isGame() || Vars.player.dead() || Vars.player.unit() != controlled || !controlled.isAdded()) { stop(); return; }
        if (Vars.state.isPaused() || Core.scene.hasDialog() || Core.scene.hasKeyboard()) return;
        if (Math.abs(Core.input.axis(Binding.moveX)) > 0.01f || Math.abs(Core.input.axis(Binding.moveY)) > 0.01f) { stop(); return; }
        Unit unit = controlled;
        switch (mode) {
            case MINE -> mine(unit);
            case FOLLOW -> follow(unit, false);
            case ASSIST -> follow(unit, true);
            case CURSOR -> move(unit, Core.input.mouseWorldX(), Core.input.mouseWorldY(), 10);
            case CIRCLE -> circle(unit);
            case REPAIR -> combat(unit, true);
            case DEMOLISH -> combat(unit, false);
            default -> { }
        }
    }
    private void mine(Unit unit) {
        var core = unit.closestCore();
        if (core == null || !core.isValid()) { unit.mineTile = null; status = "st.ai.error.core"; return; }
        Item desired = item;
        if (desired == null) {
            int least = Integer.MAX_VALUE;
            for (Item candidate : Vars.content.items()) if (unit.canMine(candidate) && Vars.indexer.hasOre(candidate)
                    && core.acceptStack(candidate, 1, unit) > 0 && core.items.get(candidate) < least) {
                desired = candidate; least = core.items.get(candidate);
            }
        }
        if (desired == null) { unit.mineTile = null; status = "st.ai.error.ore"; return; }
        if (unit.stack.amount > 0 && (unit.stack.item != desired || unit.stack.amount >= unit.itemCapacity() || core.acceptStack(desired, 1, unit) == 0)) {
            unit.mineTile = null; status = "st.ai.returning";
            if (!unit.within(core, Vars.itemTransferRange - 1)) move(unit, core.x, core.y, Vars.itemTransferRange / 2f);
            else if (System.nanoTime() >= nextAction) {
                Call.transferInventory(Vars.player, core); nextAction = System.nanoTime() + 1_000_000_000L;
            }
            return;
        }
        if (System.nanoTime() >= nextScan || ore == null || !unit.validMine(ore, false)) {
            ore = closestOre(unit, desired); nextScan = System.nanoTime() + 1_000_000_000L;
        }
        if (ore == null || !unit.validMine(ore, false)) { unit.mineTile = null; status = "st.ai.error.ore"; return; }
        status = "st.ai.mining";
        if (unit.within(ore, unit.type.mineRange * 0.8f)) { unit.mineTile = ore; unit.movePref(movement.setZero()); }
        else { unit.mineTile = null; move(unit, ore.worldx(), ore.worldy(), unit.type.mineRange * 0.7f); }
    }
    private Tile closestOre(Unit unit, Item desired) {
        Tile best = Vars.indexer.hasOre(desired) ? Vars.indexer.findClosestOre(unit, desired) : null;
        if (best != null && (!unit.validMine(best, false) || unit.getMineResult(best) != desired
                || Vars.net.client() && !Vars.fogControl.isDiscovered(unit.team, best.x, best.y))) best = null;
        float bestDistance = best == null ? Float.MAX_VALUE : unit.dst2(best.worldx(), best.worldy());
        // The game's ore index can lag behind terrain edits. Keep the fallback bounded and
        // never inspect undiscovered tiles on a remote server.
        int cx = unit.tileX(), cy = unit.tileY(), radius = 48;
        for (int x = Math.max(0, cx - radius); x <= Math.min(Vars.world.width() - 1, cx + radius); x++) {
            for (int y = Math.max(0, cy - radius); y <= Math.min(Vars.world.height() - 1, cy + radius); y++) {
                Tile tile = Vars.world.tile(x, y);
                if (tile == null || Vars.net.client() && !Vars.fogControl.isDiscovered(unit.team, x, y)
                        || unit.getMineResult(tile) != desired || !unit.validMine(tile, false)) continue;
                float distance = unit.dst2(tile.worldx(), tile.worldy());
                if (distance < bestDistance) { best = tile; bestDistance = distance; }
            }
        }
        return best;
    }
    private void follow(Unit unit, boolean assist) {
        Player other = target();
        if (other == null || other.dead() || other.team() != unit.team || other.unit().inFogTo(unit.team)) { status = "st.ai.error.player"; return; }
        status = "st.ai.working";
        if (assist && unit.canBuild() && unit.plans.isEmpty() && System.nanoTime() >= nextAction) {
            int copied = 0;
            java.util.List<BuildPlan> candidates = new java.util.ArrayList<>();
            for (BuildPlan plan : other.unit().plans) candidates.add(plan);
            for (BuildPlan plan : other.getPreviewPlans()) if (candidates.stream().noneMatch(value -> value.x == plan.x && value.y == plan.y)) candidates.add(plan);
            for (BuildPlan plan : candidates) {
                if (copied >= 16) break;
                if (plan.breaking ? Build.validBreak(unit.team, plan.x, plan.y)
                        : plan.block != null && !plan.block.privileged && !plan.block.isBanned()
                            && Build.validPlace(plan.block, unit.team, plan.x, plan.y, plan.rotation)) { unit.addBuild(plan.copy()); copied++; }
            }
            unit.updateBuilding = true;
            nextAction = System.nanoTime() + 2_000_000_000L;
        }
        move(unit, other.x, other.y, assist && !unit.plans.isEmpty() ? unit.type.buildRange * 0.5f : 32);
    }
    private void circle(Unit unit) {
        float dx = unit.x - centerX, dy = unit.y - centerY;
        if (Mathf.dst(dx, dy) < 1f) { unit.movePref(movement.set(unit.speed(), 0)); status = "st.ai.working"; return; }
        float distance = Math.max(1f, Mathf.dst(dx, dy));
        float radial = Mathf.clamp((circleRadius - distance) / circleRadius, -1f, 1f);
        movement.set(-dy / distance + dx / distance * radial, dx / distance + dy / distance * radial).nor().scl(unit.speed());
        unit.movePref(movement); unit.lookAt(centerX, centerY); status = "st.ai.working";
    }
    private void combat(Unit unit, boolean repair) {
        if (System.nanoTime() >= nextScan || targetBuilding == null || !targetBuilding.isValid()) {
            Building[] closest = {null};
            Vars.indexer.eachBlock(null, unit.x, unit.y, 600f, building -> {
                if (building == null || !building.isValid() || Vars.net.client() && (!building.isDiscovered(unit.team)
                        || Vars.state.rules.fog && building.team != unit.team && !Vars.fogControl.isVisibleTile(unit.team, building.tileX(), building.tileY()))) return false;
                return repair ? building.team == unit.team && building.health < building.maxHealth
                    : building.team != unit.team && building.block == demolitionBlock;
            }, building -> { if (closest[0] == null || building.dst2(unit) < closest[0].dst2(unit)) closest[0] = building; });
            targetBuilding = closest[0]; nextScan = System.nanoTime() + 1_000_000_000L;
        }
        if (targetBuilding == null) { unit.controlWeapons(false); Vars.player.shooting = false; status = "st.ai.no-target"; return; }
        status = "st.ai.working";
        float reach = Math.max(20f, unit.range() * 0.8f);
        if (!unit.within(targetBuilding, reach)) { unit.controlWeapons(false); Vars.player.shooting = false; move(unit, targetBuilding.x, targetBuilding.y, reach); }
        else {
            unit.movePref(movement.setZero()); unit.aim(targetBuilding.x, targetBuilding.y, true); unit.lookAt(targetBuilding);
            Vars.player.mouseX = targetBuilding.x; Vars.player.mouseY = targetBuilding.y; Vars.player.shooting = true;
            unit.controlWeapons(true, true);
        }
    }
    private void move(Unit unit, float x, float y, float distance) {
        movement.set(x - unit.x, y - unit.y);
        if (movement.len() <= distance) movement.setZero(); else movement.setLength(unit.speed());
        unit.movePref(movement);
        if (!movement.isZero()) unit.lookAt(x, y);
    }
}
