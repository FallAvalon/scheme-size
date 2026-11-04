package org.schemetools.schematic;

import arc.struct.Seq;
import arc.struct.StringMap;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.Schematic;
import mindustry.game.Schematic.Stile;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.blocks.ConstructBlock.ConstructBuild;
import mindustry.world.blocks.environment.Floor;
import mindustry.input.DesktopInput;
import mindustry.input.Binding;
import arc.Core;
import arc.input.KeyCode;
import arc.math.geom.Vec2;
import org.schemetools.host.ArenaTools;

import java.io.IOException;
import java.util.*;

/** Schematic capture and application stay separate from file management and UI. */
public final class SchematicTools {
    public static final int MAX_PLACEMENT_PLANS = 16384;
    public enum Layer { BUILDINGS, PLANS, FLOOR, OBSTACLES, OVERLAY, TERRAIN }
    private static final Map<BuildPlan, Boolean> previewPlans = new IdentityHashMap<>();
    private SchematicTools() { }

    public static Schematic capture(int x1, int y1, int x2, int y2, Layer layer) throws IOException {
        if (!Vars.state.isGame() || layer == null || x1 < 0 || y1 < 0 || x2 < x1 || y2 < y1
                || x2 >= Vars.world.width() || y2 >= Vars.world.height()
                || x2 - x1 + 1 > 512 || y2 - y1 + 1 > 512) throw new IOException("st.schematic.error.selection");
        Seq<Stile> entries = new Seq<>();
        if (layer == Layer.BUILDINGS) {
            Set<Integer> seen = new HashSet<>();
            List<Building> buildings = new ArrayList<>();
            for (int y = y1; y <= y2; y++) for (int x = x1; x <= x2; x++) {
                Building build = Vars.world.build(x, y);
                if (build == null || !seen.add(build.pos()) || !visible(build)) continue;
                buildings.add(build);
            }
            int minX = x1, minY = y1, maxX = x2, maxY = y2;
            for (Building building : buildings) {
                Block block = building instanceof ConstructBuild c ? c.current : building.block;
                if (block == null) continue;
                minX = Math.min(minX, building.tileX() - (block.size - 1) / 2);
                minY = Math.min(minY, building.tileY() - (block.size - 1) / 2);
                maxX = Math.max(maxX, building.tileX() + block.size / 2);
                maxY = Math.max(maxY, building.tileY() + block.size / 2);
            }
            x1 = minX; y1 = minY; x2 = maxX; y2 = maxY;
            if (x2 - x1 + 1 > 512 || y2 - y1 + 1 > 512 || x1 < 0 || y1 < 0
                    || x2 >= Vars.world.width() || y2 >= Vars.world.height()) throw new IOException("st.schematic.error.selection");
            for (Building building : buildings) {
                Block block = building instanceof ConstructBuild c ? c.current : building.block;
                if (block == null || block.privileged || block.isBanned()) continue;
                Object config = building instanceof ConstructBuild c ? c.lastConfig : building.config();
                entries.add(new Stile(block, building.tileX() - x1, building.tileY() - y1, config, (byte)building.rotation));
            }
        } else if (layer == Layer.PLANS) {
            if (Vars.player.dead()) throw new IOException("st.build.error.builder");
            for (BuildPlan plan : Vars.player.unit().plans) if (!plan.breaking && plan.x >= x1 && plan.x <= x2 && plan.y >= y1 && plan.y <= y2)
                entries.add(new Stile(plan.block, plan.x - x1, plan.y - y1, plan.config, (byte)plan.rotation));
        } else {
            for (int y = y1; y <= y2; y++) for (int x = x1; x <= x2; x++) {
                var tile = Vars.world.tile(x, y);
                if (Vars.net.client() && Vars.state.rules.fog && !Vars.fogControl.isDiscovered(Vars.player.team(), x, y)) continue;
                if (layer == Layer.FLOOR || layer == Layer.TERRAIN) entries.add(new Stile(tile.floor(), x - x1, y - y1, null, (byte)0));
                if ((layer == Layer.OBSTACLES || layer == Layer.TERRAIN) && tile.build == null && tile.block() != Blocks.air)
                    entries.add(new Stile(tile.block(), x - x1, y - y1, null, (byte)0));
                if ((layer == Layer.OVERLAY || layer == Layer.TERRAIN) && tile.overlay() != Blocks.air)
                    entries.add(new Stile(tile.overlay(), x - x1, y - y1, null, (byte)0));
            }
        }
        StringMap tags = new StringMap();
        tags.put("st-layer", layer.name().toLowerCase(Locale.ROOT));
        Schematic result = new Schematic(entries, tags, x2 - x1 + 1, y2 - y1 + 1);
        SchematicCodec.validate(result);
        return result;
    }

    private static boolean visible(Building building) {
        return building.team == Vars.player.team() || building.isDiscovered(Vars.player.team()) && building.wasVisible;
    }
    public static boolean terrain(Schematic schematic) {
        for (Stile tile : schematic.tiles) if (tile.block instanceof Floor || !tile.block.hasBuilding() && !tile.block.synthetic()) return true;
        return false;
    }
    public static void use(Schematic schematic) throws IOException {
        SchematicCodec.validate(schematic);
        if (!Vars.state.isGame() || Vars.player.dead() || !(Vars.control.input instanceof DesktopInput desktop))
            throw new IOException("st.build.error.builder");
        if (terrain(schematic)) throw new IOException("st.schematic.error.terrain-place");
        // The native converter removes locked content even when checkHidden=false.
        // Keep native plans and add the missing locked tiles solely for the local preview.
        desktop.useSchematic(schematic, false);
        for (Stile tile : schematic.tiles) if (!tile.block.unlockedNow()) {
            desktop.selectPlans.add(new BuildPlan(tile.x + desktop.schematicX - schematic.width / 2,
                tile.y + desktop.schematicY - schematic.height / 2, tile.rotation, tile.block, tile.config));
        }
        desktop.selectPlans.sort((a,b)->Integer.compare(b.block.schematicPriority,a.block.schematicPriority));
        previewPlans.clear();
        for(BuildPlan plan:desktop.selectPlans)previewPlans.put(plan,Boolean.TRUE);
    }

    /** Remove unavailable preview-only tiles immediately before the normal game places the selection. */
    public static boolean beforePlacement(KeyCode key){
        if(previewPlans.isEmpty()||Vars.headless||!Vars.state.isGame()||Vars.player.dead()
                ||Binding.select.value==null||Binding.select.value.key!=key||Core.scene.hasDialog()||Core.scene.hasKeyboard())return false;
        Vec2 p=Core.scene.screenToStageCoordinates(new Vec2(Core.input.mouseX(),Core.input.mouseY()));
        if(Core.scene.hit(p.x,p.y,true)!=null)return false;
        Seq<BuildPlan> plans=Vars.control.input.selectPlans;int before=plans.size;
        plans.removeAll(plan->previewPlans.containsKey(plan)&&!availableForPlacement(plan.block));
        previewPlans.clear();
        if(before>plans.size&&plans.isEmpty())return true;
        return false;
    }
    private static boolean availableForPlacement(Block block){
        return Vars.state.rules.schematicsAllowed&&Vars.player.unit().canBuild()&&block!=null&&!block.privileged
            &&!block.isBanned()&&block.unlockedNow()&&block.isVisible()&&block.environmentBuildable()
            &&block.supportsEnv(Vars.state.rules.env);
    }
    public static void clearPreview(){previewPlans.clear();}
    public static void clearFinishedPreview(){
        if(!previewPlans.isEmpty()&&Vars.control.input.selectPlans.isEmpty())previewPlans.clear();
    }

    /** Assign by anchor: every building belongs to exactly one section, including boundary-crossing footprints. */
    public static Schematic section(Schematic source, int x, int y, int width, int height) throws IOException {
        SchematicCodec.validate(source);
        if (x < 0 || y < 0 || width < 1 || height < 1 || width > 128 || height > 128
                || (long)x + width > source.width || (long)y + height > source.height) throw new IOException("st.schematic.error.selection");
        Seq<Stile> tiles = new Seq<>();
        for (Stile tile : source.tiles) {
            if (tile.x < x || tile.y < y || tile.x >= x + width || tile.y >= y + height) continue;
            tiles.add(new Stile(tile.block, tile.x - x, tile.y - y, tile.config, tile.rotation));
        }
        StringMap tags = new StringMap(); for (var entry : source.tags) tags.put(entry.key, entry.value);
        String title = tags.get("name", "");
        tags.put("name", title.substring(0, Math.min(title.length(), 55)) + " (" + x + ", " + y + ")");
        Schematic result = new Schematic(tiles, tags, width, height);
        result.labels.addAll(source.labels); SchematicCodec.validate(result); return result;
    }

    public static ArenaTools.Edit terrainPreview(ArenaTools tools, Schematic schematic, int x, int y) throws IOException {
        SchematicCodec.validate(schematic);
        if ((long)schematic.width * schematic.height > ArenaTools.MAX_AREA) throw new IOException("st.arena.error.area");
        Map<Integer, Block[]> cells = new LinkedHashMap<>();
        for (Stile tile : schematic.tiles) {
            int key = tile.x + tile.y * schematic.width;
            Block[] layers = cells.computeIfAbsent(key, ignored -> new Block[3]);
            if (tile.block.isOverlay()) layers[1] = tile.block;
            else if (tile.block instanceof Floor) layers[0] = tile.block;
            else if (ArenaTools.environmentBlock(tile.block)) layers[2] = tile.block;
            else throw new IOException("st.schematic.error.mixed");
        }
        List<ArenaTools.TerrainCell> paste = new ArrayList<>();
        for (var cell : cells.entrySet()) {
            Block[] layers = cell.getValue();
            paste.add(new ArenaTools.TerrainCell(x + cell.getKey() % schematic.width, y + cell.getKey() / schematic.width,
                layers[0], layers[1], layers[2]));
        }
        return tools.previewCells(paste);
    }

    /** Spatial batches retain all entries and keep each native transaction at most 64×64. */
    public static List<ArenaTools.Edit> terrainPreviews(ArenaTools tools,Schematic schematic,int x,int y)throws IOException{
        SchematicCodec.validate(schematic);
        Map<Integer,Seq<Stile>> groups=new LinkedHashMap<>();
        for(Stile tile:schematic.tiles)groups.computeIfAbsent(tile.x/64+(tile.y/64)*8,ignored->new Seq<>()).add(tile);
        List<ArenaTools.Edit> result=new ArrayList<>();
        for(var group:groups.entrySet()){
            int sx=(group.getKey()%8)*64,sy=(group.getKey()/8)*64;
            Seq<Stile> entries=new Seq<>();
            for(Stile tile:group.getValue())entries.add(new Stile(tile.block,tile.x-sx,tile.y-sy,tile.config,tile.rotation));
            result.add(terrainPreview(tools,new Schematic(entries,new StringMap(),Math.min(64,schematic.width-sx),Math.min(64,schematic.height-sy)),Math.addExact(x,sx),Math.addExact(y,sy)));
        }
        return List.copyOf(result);
    }
}
