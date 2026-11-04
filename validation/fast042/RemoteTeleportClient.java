package probe;
import arc.*;import arc.input.*;import arc.util.*;import mindustry.*;import mindustry.gen.*;
public class RemoteTeleportClient{
 static boolean done,strict;static long ready;
 static void set(Object o,String n,Object v)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
 public static void main(String[]args){strict=Boolean.parseBoolean(args[0]);
 Events.on(mindustry.game.EventType.ClientLoadEvent.class,e->Core.app.post(()->{Core.settings.put("schemesize-welcome",false);Vars.player.name="Teleport guest";Vars.ui.join.connect("127.0.0.1",16589);}));
 Events.run(mindustry.game.EventType.Trigger.update,()->{if(done||!Vars.net.client()||!Vars.state.isGame()||Vars.player.dead()||Vars.player.unit().type!=mindustry.content.UnitTypes.mega)return;if(ready==0){ready=System.currentTimeMillis();return;}if(System.currentTimeMillis()-ready<2500)return;done=true;try{
  for(int d=0;d<8&&Core.scene.getDialog()!=null;d++)Core.scene.getDialog().hide(null);Vars.ui.loadfrag.hide();Core.scene.unfocusAll();
  var mod=Vars.mods.list().first();var l=mod.main.getClass().getClassLoader();var w=Class.forName("org.schemetools.client.ToolWorkspace",true,l);var f=w.getDeclaredField("active");f.setAccessible(true);Object workspace=f.get(null);var pf=w.getDeclaredField("panel");pf.setAccessible(true);Object panel=pf.get(workspace);var kf=panel.getClass().getDeclaredField("keys");kf.setAccessible(true);Object keys=kf.get(panel);
  float x=Vars.world.unitWidth()*.7f,y=Vars.world.unitHeight()*.7f;Core.camera.position.set(x,y);Core.camera.update();set(Core.input,"mouseX",Core.graphics.getWidth()/2);set(Core.input,"mouseY",Core.graphics.getHeight()/2);Core.scene.mouseMoved(Core.graphics.getWidth()/2,Core.graphics.getHeight()/2);Core.scene.act();
  var k=Core.input.getKeyboard();k.keyDown(KeyCode.altLeft);k.keyDown(KeyCode.mouseBack);keys.getClass().getMethod("dispatch").invoke(keys);k.keyUp(KeyCode.mouseBack);k.keyUp(KeyCode.altLeft);k.postUpdate();
  if(!Vars.player.unit().within(x,y,1))throw new AssertionError("local chord failed unit="+Vars.player.unit().x+","+Vars.player.unit().y+" cursor="+Core.input.mouseWorldX()+","+Core.input.mouseWorldY()+" mouseUI="+Core.scene.hasMouse()+" keyboard="+Core.scene.hasKeyboard()+" dialog="+Core.scene.hasDialog());Call.serverPacketReliable("st-teleport-test","attempt");
  Time.runTask(180,()->{boolean at=Vars.player.unit().within(x,y,48);if(at==!strict)Log.info("SCHEMETOOLS_REMOTE_CLIENT_PASS strict=@ admin=@ accepted=@",strict,Vars.player.admin,at);else Log.err("SCHEMETOOLS_REMOTE_FAIL client strict=@ at=@",strict,at);});
 }catch(Throwable x){Log.err("SCHEMETOOLS_REMOTE_FAIL",x);}});
 mindustry.desktop.DesktopLauncher.main(new String[0]);}
}