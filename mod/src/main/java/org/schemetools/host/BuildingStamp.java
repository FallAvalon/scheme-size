package org.schemetools.host;

import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.Build;
import mindustry.world.blocks.storage.CoreBlock;

import java.util.*;

/** Explicit host arena placement, separate from ordinary paid construction plans. */
public final class BuildingStamp {
    private record Cell(int x,int y) { }
    private record Placed(Building building,Object config,Team team,int rotation) { }
    public static final class Preview {
        private final Object world;
        private final Block block;
        private final Team team;
        private final int rotation;
        private final List<Cell> cells;
        private Object configuration;
        private Preview(Block block,Team team,int rotation,List<Cell> cells) {
            this.world=Vars.world.tiles;this.block=block;this.team=team;this.rotation=rotation;this.cells=List.copyOf(cells);
        }
        public int count(){return cells.size();}
        public int replacements(){
            if(world!=Vars.world.tiles)throw new IllegalStateException("st.arena.error.world");
            java.util.Set<Integer> occupied=new java.util.HashSet<>();
            for(Cell c:cells)Vars.world.tile(c.x,c.y).getLinkedTilesAs(block,t->{if(t.block()!=Blocks.air)occupied.add(t.build==null?t.pos():t.build.pos());});
            return occupied.size();
        }
        public Preview configured(Object value){if(world!=Vars.world.tiles)throw new IllegalStateException("st.arena.error.world");Preview next=new Preview(block,team,rotation,cells);next.configuration=value;return next;}
    }
    private record Undo(Object world,List<Placed> buildings,WorldSnapshot before) { }
    private final HostActions authority;
    private final ArrayDeque<Undo> undo=new ArrayDeque<>();
    private long lastChange;
    public BuildingStamp(HostActions authority){this.authority=Objects.requireNonNull(authority);}
    public boolean godMode(){return authority.godMode();}
    private void require(){if(!authority.isAvailable())throw new IllegalStateException("st.error.authority");}
    private void rate(){if(authority.godMode())return;if(lastChange!=0&&System.nanoTime()-lastChange<250_000_000L)throw new IllegalStateException("st.arena.error.rate");}
    public void clear(){undo.clear();lastChange=0;}
    public Preview preview(int x,int y,int width,int height,Block block,Team team,int rotation){
        return preview(x,y,width,height,block,team,rotation,ArenaTools.Shape.RECTANGLE,false);
    }
    public Preview preview(int x,int y,int width,int height,Block block,Team team,int rotation,ArenaTools.Shape shape,boolean outline){
        require();
        if(block==null||!Vars.content.blocks().contains(block,true)||!block.hasBuilding()||(!authority.godMode()&&block instanceof CoreBlock)
                ||block.isFloor()||(!authority.godMode()&&(block.privileged||block.isBanned()||!block.isVisible()||!block.supportsEnv(Vars.state.rules.env)))
                ||shape==null||!HostActions.isValidTeam(team)||rotation<0||rotation>3||x<0||y<0||width<1||height<1
                ||(long)width*height>4096||(long)x+width>Vars.world.width()||(long)y+height>Vars.world.height())
            throw new IllegalArgumentException("st.stamp.error");
        List<Cell> cells=new ArrayList<>();int low=-(block.size-1)/2,high=block.size/2;
        for(int by=y-low;by+high<y+height;by+=block.size)for(int bx=x-low;bx+high<x+width;bx+=block.size){
            float cx=x+(width-1)/2f,cy=y+(height-1)/2f;
            float nx=(bx-cx)/Math.max(.5f,width/2f),ny=(by-cy)/Math.max(.5f,height/2f);
            if(shape==ArenaTools.Shape.CIRCLE&&nx*nx+ny*ny>1)continue;
            if(outline&&bx>x-low&&by>y-low&&bx+high+block.size<x+width&&by+high+block.size<y+height){
                if(shape!=ArenaTools.Shape.CIRCLE)continue;
            }
            if(outline&&shape==ArenaTools.Shape.CIRCLE){
                double ix=Math.max(0,width/2d-block.size),iy=Math.max(0,height/2d-block.size);
                if(ix>0&&iy>0&&Math.pow((bx-cx)/ix,2)+Math.pow((by-cy)/iy,2)<1)continue;
            }
            cells.add(new Cell(bx,by));if(cells.size()>(authority.godMode()?ArenaTools.MAX_AREA:256))throw new IllegalArgumentException("st.stamp.error");
        }
        if(cells.isEmpty()&&authority.godMode()&&(width<block.size||height<block.size))cells.add(new Cell(x,y));
        if(cells.isEmpty())throw new IllegalArgumentException("st.stamp.error");
        Preview preview=new Preview(block,team,rotation,cells);validate(preview);return preview;
    }
    private void validate(Preview preview){
        if(preview==null||preview.world!=Vars.world.tiles)throw new IllegalStateException("st.arena.error.world");
        if(!Vars.content.blocks().contains(preview.block,true)||(!authority.godMode()&&(preview.block.isBanned()||preview.block.privileged||!preview.block.supportsEnv(Vars.state.rules.env)))||!HostActions.isValidTeam(preview.team))throw new IllegalArgumentException("st.stamp.error");
        int low=-(preview.block.size-1)/2,high=preview.block.size/2;
        for(Cell cell:preview.cells){
            for(int dy=low;dy<=high;dy++)for(int dx=low;dx<=high;dx++){
                var tile=Vars.world.tile(cell.x+dx,cell.y+dy);
                if(tile==null)throw new IllegalArgumentException("st.arena.error.area");
                if((!authority.godMode()&&(tile.build!=null||tile.block()!=Blocks.air)))throw new IllegalStateException("st.arena.error.occupied");
            }
            if(!authority.godMode()&&(!Build.validPlaceIgnoreUnits(preview.block,preview.team,cell.x,cell.y,preview.rotation,false,false)
                    ||!Build.checkNoUnitOverlap(preview.block,cell.x,cell.y)))throw new IllegalStateException("st.build.error.placement");
        }
    }
    public int apply(Preview preview){
        require();rate();validate(preview);
        List<Placed> placed=new ArrayList<>();
        List<mindustry.world.Tile> affected=new ArrayList<>();
        for(Cell cell:preview.cells)Vars.world.tile(cell.x,cell.y).getLinkedTilesAs(preview.block,affected::add);
        WorldSnapshot before=authority.godMode()?WorldSnapshot.capture(affected):null;
        try{
            for(Cell cell:preview.cells){
                Vars.world.tile(cell.x,cell.y).setNet(preview.block,preview.team,preview.rotation);
                Building building=Vars.world.build(cell.x,cell.y);
                if(building==null||building.block!=preview.block||building.team!=preview.team)throw new IllegalStateException("st.stamp.error");
                placed.add(new Placed(building,building.config(),building.team,building.rotation));
                if(preview.configuration!=null){building.configureAny(preview.configuration);placed.set(placed.size()-1,new Placed(building,building.config(),building.team,building.rotation));}
            }
        }catch(RuntimeException failure){
            for(Placed entry:placed)if(entry.building.isValid())try{entry.building.tile.removeNet();}catch(RuntimeException rollback){failure.addSuppressed(rollback);}
            if(before!=null)try{before.restore();}catch(RuntimeException rollback){failure.addSuppressed(rollback);}
            throw failure;
        }
        undo.push(new Undo(Vars.world.tiles,List.copyOf(placed),before));while(undo.size()>8)undo.removeLast();lastChange=System.nanoTime();return placed.size();
    }
    public int undo(){
        require();rate();Undo entry=undo.peek();
        if(entry==null||entry.world!=Vars.world.tiles)throw new IllegalStateException("st.arena.error.no-undo");
        if(authority.godMode()&&entry.before!=null){entry.before.restore();undo.pop();lastChange=System.nanoTime();return entry.buildings.size();}
        for(Placed placed:entry.buildings){
            Building building=placed.building;
            boolean[] liquid={false};if(building.liquids!=null)building.liquids.each((type,amount)->{if(amount>0)liquid[0]=true;});
            if(!building.isValid()||building.team!=placed.team||building.rotation!=placed.rotation||building.health!=building.maxHealth||!Objects.deepEquals(placed.config,building.config())
                    ||building.items!=null&&building.items.total()>0
                    ||liquid[0]
                    ||building.power!=null&&building.block.consPower!=null&&building.block.consPower.buffered&&building.power.status>0)
                throw new IllegalStateException("st.arena.error.conflict");
        }
        for(Placed placed:entry.buildings)placed.building.tile.removeNet();
        undo.pop();lastChange=System.nanoTime();return entry.buildings.size();
    }
}
