package org.schemetools.host;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.Tile;
import arc.util.io.Reads;
import arc.util.io.Writes;
import java.io.*;
import java.util.*;

/** Native save data for a bounded God edit, including whole overlapping buildings. */
final class WorldSnapshot {
    private record Cell(int x,int y,Block floor,Block overlay,Block block){}
    private record Structure(int x,int y,Block block,Team team,int rotation,byte revision,byte[] data,Object config){}
    private final Object world=Vars.world.tiles;
    private final Map<Integer,Cell> cells=new LinkedHashMap<>();
    private final Map<Integer,Structure> structures=new LinkedHashMap<>();
    static WorldSnapshot capture(Collection<Tile> tiles){
        WorldSnapshot out=new WorldSnapshot();long bytes=0;
        for(Tile tile:tiles){
            out.cell(tile);
            if(tile.build!=null&&!out.structures.containsKey(tile.build.pos())){
                Building b=tile.build;b.tile.getLinkedTiles(out::cell);
                try{
                    var buffer=new ByteArrayOutputStream();b.writeAll(new Writes(new DataOutputStream(buffer)));
                    byte[] data=buffer.toByteArray();bytes+=data.length;
                    if(bytes>64L*1024*1024)throw new IllegalArgumentException("st.snapshot.too-large");
                    out.structures.put(b.pos(),new Structure(b.tileX(),b.tileY(),b.block,b.team,b.rotation,b.version(),data,b.config()));
                }catch(RuntimeException failure){throw failure;}
            }
        }
        return out;
    }
    private void cell(Tile t){if(t==null)throw new IllegalArgumentException("st.arena.error.area");cells.putIfAbsent(t.pos(),new Cell(t.x,t.y,t.floor(),t.overlay(),t.block()));}
    int buildings(){return structures.size();}
    void restore(){
        if(world!=Vars.world.tiles)throw new IllegalStateException("st.arena.error.world");
        // Native removal clears complete footprints; all affected original footprints are captured.
        Set<Building> removed=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Cell c:cells.values()){Tile t=Vars.world.tile(c.x,c.y);if(t.build!=null){if(removed.add(t.build))t.build.tile.removeNet();}else if(t.block()!=Blocks.air)t.removeNet();}
        for(Cell c:cells.values()){
            Tile t=Vars.world.tile(c.x,c.y);t.setFloorNet(c.floor,c.overlay);
            if(c.block!=Blocks.air&&!c.block.hasBuilding())t.setNet(c.block,Team.derelict,0);
        }
        for(Structure b:structures.values())Vars.world.tile(b.x,b.y).setNet(b.block,b.team,b.rotation);
        // Broadcast configs first, then restore the full native data (logic memory, health, inventories, etc.).
        for(Structure b:structures.values())if(b.config!=null)Vars.world.build(b.x,b.y).configureAny(b.config);
        for(Structure b:structures.values()){
            Building restored=Vars.world.build(b.x,b.y);
            restored.readAll(new Reads(new DataInputStream(new ByteArrayInputStream(b.data))),b.revision);
            restored.afterRead();
        }
        for(Structure b:structures.values())Vars.world.build(b.x,b.y).afterReadAll();
    }
}
