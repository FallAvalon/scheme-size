package org.schemetools.client;

import arc.Events;
import mindustry.Vars;
import mindustry.game.EventType.*;
import org.schemetools.host.ArenaTools;
import java.util.List;

/** One shared, cancellable paste; every batch revalidates host rights and the original cells. */
public final class TerrainPaste {
    private static boolean installed;
    private static ArenaTools tools;
    private static List<ArenaTools.Edit> edits=List.of();
    private static Object world;
    private static long next;
    private static int index,total;
    private static String state="st.schematic.batch-idle";
    private TerrainPaste(){}
    public static boolean active(){return tools!=null;}
    public static String status(){return arc.Core.bundle.format(state,index,total);}
    public static void cancel(){if(active()){tools=null;edits=List.of();state="st.schematic.batch-cancelled";}}
    public static void start(ArenaTools target,List<ArenaTools.Edit> preview){
        if(Vars.headless||Vars.net.client()||!Vars.state.isGame()||active()||preview==null||preview.isEmpty()||preview.size()>64)
            throw new IllegalStateException("st.error.authority");
        if(!installed){installed=true;Events.run(Trigger.update,TerrainPaste::update);Events.on(ResetEvent.class,e->cancel());Events.on(WorldLoadEvent.class,e->cancel());}
        tools=target;edits=List.copyOf(preview);world=Vars.world.tiles;index=0;total=edits.size();next=0;state="st.schematic.batch-running";
    }
    private static void update(){
        if(!active())return;
        if(Vars.world.tiles!=world||!Vars.state.isGame()||Vars.net.client()||!arc.Core.settings.getBool("schemesize-module-schematics",true)){cancel();return;}
        if(System.nanoTime()<next)return;
        try{
            tools.apply(edits.get(index));index++;next=System.nanoTime()+300_000_000L;
            if(index==total){tools=null;edits=List.of();state="st.schematic.batch-done";}
        }catch(RuntimeException failure){tools=null;edits=List.of();state="st.schematic.batch-conflict";}
    }
}
