package probe;
import arc.*;import arc.util.*;import mindustry.*;import mindustry.content.*;import mindustry.game.*;import mindustry.gen.*;
public class RemoteTeleportServer{
 static boolean strict;
 public static void main(String[]args){strict=Boolean.parseBoolean(args[0]);
 Events.on(mindustry.game.EventType.ServerLoadEvent.class,e->Core.app.post(()->{try{
  mindustry.net.Administration.Config.strict.set(strict);
  Vars.logic.reset();Vars.world.loadMap(Vars.maps.defaultMaps().first());Vars.state.rules.canGameOver=false;Vars.state.rules.waves=false;Vars.state.rules.disableUnitCap=true;Vars.logic.play();
  Events.on(mindustry.game.EventType.PlayerJoin.class,j->Time.runTask(60,()->{var u=UnitTypes.mega.spawn(j.player.team(),Vars.world.unitWidth()*.25f,Vars.world.unitHeight()*.25f);Call.unitControl(j.player,u);}));
  Vars.netServer.addPacketHandler("st-teleport-test",(p,data)->Time.runTask(120,()->{boolean at=p.unit().within(Vars.world.unitWidth()*.7f,Vars.world.unitHeight()*.7f,48);if(at==!strict)Log.info("SCHEMETOOLS_REMOTE_SERVER_PASS strict=@ mods=@ playerAdmin=@ x=@ y=@",strict,Vars.mods.list().size,p.admin,p.x,p.y);else Log.err("SCHEMETOOLS_REMOTE_FAIL server position strict=@ atTarget=@",strict,at);}));
  Vars.net.host(16589);Log.info("SCHEMETOOLS_REMOTE_READY strict=@",strict);
 }catch(Throwable x){Log.err("SCHEMETOOLS_REMOTE_FAIL",x);}}));
 mindustry.server.ServerLauncher.main(new String[0]);}
}