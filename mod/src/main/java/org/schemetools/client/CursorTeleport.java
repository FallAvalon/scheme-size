package org.schemetools.client;

import arc.Core;
import mindustry.Vars;
import org.schemetools.host.HostActions;

/** Self movement uses normal client snapshots; a remote server may correct the position. */
final class CursorTeleport {
    private CursorTeleport(){}
    static boolean move(HostActions actions){
        float x=Core.input.mouseWorldX(),y=Core.input.mouseWorldY();
        if(!Vars.net.client()){actions.teleport(Vars.player,x,y);return false;}
        if(!Vars.state.isGame()||Vars.player==null||Vars.player.dead())throw new IllegalStateException("st.error.input");
        var tile=Float.isFinite(x)&&Float.isFinite(y)&&x>=0&&y>=0?Vars.world.tileWorld(x,y):null;
        var unit=Vars.player.unit();
        if(tile==null||unit instanceof mindustry.gen.BlockUnitc||(!actions.godMode()&&!unit.canPass(tile.x,tile.y)))throw new IllegalArgumentException("st.error.spawn-location");
        unit.set(x,y);unit.vel.setZero();unit.snapInterpolation();
        Vars.player.set(x,y);Vars.player.snapInterpolation();
        return true;
    }
}
