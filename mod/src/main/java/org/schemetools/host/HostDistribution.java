package org.schemetools.host;

import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.gen.Building;
import mindustry.type.Item;
import mindustry.world.Tile;
import mindustry.world.blocks.distribution.Router;

/** Optional factory adapter; no extra serialized fields, block IDs, packets or resources. */
public final class HostDistribution {
    private static boolean installed;
    private HostDistribution() { }
    public static void install() {
        if (installed || Vars.headless) return;
        installed = true;
        Router distributor = (Router)Blocks.distributor;
        var original = distributor.buildType;
        distributor.buildType = () -> {
            // A remote vanilla server retains its own native distributor simulation.
            if (Vars.net.client()) return original.get();
            return distributor.new RouterBuild() {
                @Override public boolean canControl() { return true; }
                @Override public Building getTileTarget(Item item, Tile from, boolean advance) {
                    if (!isControlled() || unit == null) return super.getTileTarget(item, from, advance);
                    super.getTileTarget(item, from, false); // Let native RouterBuild refresh its control-unit state.
                    if (!unit.isShooting()) return null;
                    float dx = unit.aimX() - x, dy = unit.aimY() - y;
                    float edge = (block.size / 2 + 1) * Vars.tilesize;
                    Building target = Math.abs(dx) >= Math.abs(dy)
                        ? Vars.world.buildWorld(x + Math.copySign(edge, dx), y)
                        : Vars.world.buildWorld(x, y + Math.copySign(edge, dy));
                    return target != null && target != this && proximity.contains(target, true) && target.team == team
                        && target.acceptItem(this, item) ? target : null;
                }
            };
        };
    }
}
