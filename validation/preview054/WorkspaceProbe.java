package probe;
import arc.*;import arc.input.*;import arc.math.geom.*;import arc.scene.*;import arc.scene.ui.*;import arc.util.*;import arc.files.Fi;import arc.graphics.PixmapIO;
import mindustry.*;import mindustry.content.*;import mindustry.gen.*;import mindustry.game.*;
import java.lang.reflect.*;import java.util.*;

public class WorkspaceProbe{
 static Object workspace,panel,keys,quick;static String edition;static final String TAG=String.valueOf(Iconc.power);
 static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static void set(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
 static Object call(Object o,String n)throws Exception{Method m=o.getClass().getDeclaredMethod(n);m.setAccessible(true);return m.invoke(o);}
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static void later(Runnable r){Time.runTask(25,()->{try{r.run();}catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});}
 static void point(float x,float y)throws Exception{set(Core.input,"mouseX",(int)x);set(Core.input,"mouseY",(int)y);if(Core.input.mouseY()!=(int)y)set(Core.input,"mouseY",Core.graphics.getHeight()-(int)y);Core.scene.mouseMoved((int)x,(int)y);Core.scene.act();}
 static Vec2 center(Element e){return e.localToStageCoordinates(new Vec2(e.getWidth()/2,e.getHeight()/2));}
 static void hover(Element e)throws Exception{Vec2 p=center(e);point(p.x,p.y);}
 static boolean visible()throws Exception{return (boolean)call(quick,"visible");}
 static void worldPoint()throws Exception{for(int y=220;y<500;y+=40)for(int x=400;x<800;x+=40){point(x,y);if(!Core.scene.hasMouse())return;}throw new AssertionError("no world point");}
 static boolean down(KeyCode key){Core.input.getKeyboard().keyDown(key);return Core.input.getInputMultiplexer().touchDown(Core.input.mouseX(),Core.input.mouseY(),0,key);}
 static boolean up(KeyCode key){boolean used=Core.input.getInputMultiplexer().touchUp(Core.input.mouseX(),Core.input.mouseY(),0,key);Core.input.getKeyboard().keyUp(key);Core.input.getKeyboard().postUpdate();return used;}
 static void open()throws Exception{worldPoint();check(down(KeyCode.mouseMiddle),"middle not consumed");check(up(KeyCode.mouseMiddle),"middle up not consumed");call(keys,"dispatch");check(visible(),"menu closed on release");}
 static void click(Element e)throws Exception{hover(e);down(KeyCode.mouseLeft);up(KeyCode.mouseLeft);}
 static void walk(Element e,List<Element> out){out.add(e);if(e instanceof Group g)for(var c:g.getChildren())walk(c,out);}
 static Element textButton(String s){List<Element> all=new ArrayList<>();walk(Core.scene.root,all);return all.stream().filter(e->e instanceof TextButton b&&b.getText().toString().equals(s)).reduce((a,b)->b).orElseThrow();}
 public static void main(String[] args){edition=args[0];Events.on(mindustry.game.EventType.ClientLoadEvent.class,e->Core.app.post(()->{try{
  Core.graphics.setWindowSize(1280,720);Core.settings.put("backgroundpause",false);Core.settings.put("schemesize-welcome",false);
  Vars.logic.reset();Vars.world.loadMap(Vars.maps.defaultMaps().first());Vars.state.rules.canGameOver=false;Vars.state.rules.waves=false;Vars.logic.play();Vars.player.team(Team.sharded);Vars.player.set(620,608);Vars.player.add();Vars.player.unit(UnitTypes.mega.spawn(Team.sharded,620,608));Core.camera.position.set(620,608);
  for(int x=55;x<96;x++)for(int y=55;y<91;y++){Vars.world.tile(x,y).setBlock(Blocks.air);Vars.world.tile(x,y).setFloorNet(Blocks.stone,Blocks.air);}Vars.world.tile(85,76).setBlock(Blocks.coreShard,Team.sharded);
  Vars.state.rules.sector=Planets.serpulo.sectors.first();Vars.state.rules.fog=false;Vars.state.rules.lighting=false;Blocks.router.unlock();Blocks.surgeTower.clearUnlock();Blocks.duo.unlock();Vars.state.rules.bannedBlocks.add(Blocks.duo);Vars.player.team().core().items.clear();
  check(!Blocks.surgeTower.unlockedNow()&&Blocks.duo.isBanned()&&Blocks.router.unlockedNow(),"campaign fixture not actually restricted");
  var tiles=new arc.struct.Seq<Schematic.Stile>();tiles.add(new Schematic.Stile(Blocks.router,0,0,null,(byte)0));tiles.add(new Schematic.Stile(Blocks.surgeTower,2,0,new Point2[]{new Point2(1,0)},(byte)2));tiles.add(new Schematic.Stile(Blocks.duo,4,0,null,(byte)0));var s=new Schematic(tiles,arc.struct.StringMap.of("name","Смешанная"),6,2);s.labels.add(TAG);Vars.schematics.add(s);
  for(int i=0;i<6;i++)Core.settings.remove("schemesize-quick-group-"+i);Core.settings.put("schemesize-quick-group-0",TAG);
  var loader=Vars.mods.list().first().main.getClass().getClassLoader();var c=Class.forName("org.schemetools.client.ToolWorkspace",true,loader);var f=c.getDeclaredField("active");f.setAccessible(true);workspace=f.get(null);panel=field(workspace,"panel");keys=field(panel,"keys");quick=field(panel,"quickMenu");
  later(WorkspaceProbe::start);
 }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}));mindustry.desktop.DesktopLauncher.main(new String[0]);}
 static void start(){try{open();later(()->{try{
  check(visible(),"menu did not stay open for several frames");hover(Core.scene.root.find("st-quick-group-0"));later(WorkspaceProbe::choose);
 }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});}catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
 static void choose(){try{
  Element card=Core.scene.root.find("st-quick-card-0");check(card!=null,"missing preview");hover(card);down(KeyCode.mouseLeft);Core.input.getInputMultiplexer().touchUp(Core.input.mouseX(),Core.input.mouseY(),0,KeyCode.mouseLeft);Core.input.getKeyboard().keyUp(KeyCode.mouseLeft);Vars.control.input.update();check(!visible(),"select did not close menu");check(Vars.control.input.selectPlans.size==3&&Vars.control.input.selectPlans.contains(p->p.block==Blocks.router)&&Vars.control.input.selectPlans.contains(p->p.block==Blocks.surgeTower&&p.rotation==Blocks.surgeTower.planRotation(2)&&p.config instanceof Point2[]&&((Point2[])p.config).length==1)&&Vars.control.input.selectPlans.contains(p->p.block==Blocks.duo),"complete preview is missing available, locked or banned block");Core.input.getKeyboard().postUpdate();later(WorkspaceProbe::outside);
 }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
 static void outside(){try{
  worldPoint();var image=ScreenUtils.getFrameBufferPixmap(0,0,Core.graphics.getWidth(),Core.graphics.getHeight(),true);try{PixmapIO.writePng(new Fi("complete-preview.png"),image);}finally{image.dispose();}
  open();point(600,500);check(!Core.scene.hasMouse(),"test did not point at free world");check(down(KeyCode.mouseLeft),"outside down was not consumed");check(!visible(),"outside click did not close");check(Core.scene.hasDialog(),"world click protection missing");check(Core.input.keyTap(mindustry.input.Binding.select),"test did not register select tap");Vars.control.input.update();check(Vars.control.input.selectPlans.size==3,"outside click altered preview");check(up(KeyCode.mouseLeft),"outside up was not consumed");later(WorkspaceProbe::placeMixed);
 }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
 static void placeMixed(){try{
  worldPoint();int tx=mindustry.core.World.toTile(Core.input.mouseWorldX()),ty=mindustry.core.World.toTile(Core.input.mouseWorldY());for(int x=tx-5;x<=tx+5;x++)for(int y=ty-5;y<=ty+5;y++){var t=Vars.world.tile(x,y);if(t!=null&&t.build==null){t.setBlock(Blocks.air);t.setFloorNet(Blocks.stone,Blocks.air);}}
  down(KeyCode.mouseLeft);check(Vars.control.input.selectPlans.size==1&&Vars.control.input.selectPlans.first().block==Blocks.router,"restricted tiles were not filtered before placement");Vars.control.input.update();boolean queued=false;for(var p:Vars.player.unit().plans){check(p.block!=Blocks.surgeTower&&p.block!=Blocks.duo,"restricted blocks reached builder queue");if(p.block==Blocks.router)queued=true;}check(queued,"available block was not queued through the native input");up(KeyCode.mouseLeft);Vars.control.input.selectPlans.clear();
  var tiles=new arc.struct.Seq<Schematic.Stile>();tiles.add(new Schematic.Stile(Blocks.surgeTower,0,0,null,(byte)0));var s=new Schematic(tiles,arc.struct.StringMap.of("name","Только закрытый"),2,2);
  var type=Class.forName("org.schemetools.schematic.SchematicTools",true,quick.getClass().getClassLoader());type.getMethod("use",Schematic.class).invoke(null,s);check(Vars.control.input.selectPlans.size==1&&Vars.control.input.selectPlans.first().block==Blocks.surgeTower,"all-locked schematic has no preview");worldPoint();check(down(KeyCode.mouseLeft),"all-locked placement was not consumed");check(Vars.control.input.selectPlans.isEmpty()&&Core.scene.hasDialog(),"all-locked placement not blocked");Vars.control.input.update();for(var p:Vars.player.unit().plans)check(p.block!=Blocks.surgeTower,"locked-only block reached builder queue");up(KeyCode.mouseLeft);
  later(WorkspaceProbe::nonBuilder);
 }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
 static void nonBuilder(){try{
  var previous=Vars.player.unit();var fighter=UnitTypes.dagger.spawn(Team.sharded,620,608);Vars.player.unit(fighter);check(!fighter.canBuild(),"fighter fixture unexpectedly builds");Vars.state.rules.schematicsAllowed=false;
  var tiles=new arc.struct.Seq<Schematic.Stile>();tiles.add(new Schematic.Stile(Blocks.router,0,0,null,(byte)0));var s=new Schematic(tiles,arc.struct.StringMap.of("name","Нет строителя"),2,2);
  var type=Class.forName("org.schemetools.schematic.SchematicTools",true,quick.getClass().getClassLoader());type.getMethod("use",Schematic.class).invoke(null,s);check(Vars.control.input.selectPlans.size==1,"non-builder cannot preview");worldPoint();check(down(KeyCode.mouseLeft),"non-builder placement not blocked");check(Vars.control.input.selectPlans.isEmpty(),"non-builder plan survived placement");up(KeyCode.mouseLeft);Vars.state.rules.schematicsAllowed=true;Vars.player.unit(previous);fighter.remove();
  Log.info("SCHEMETOOLS_PREVIEW_PASS: campaign locked + banned + available all visible; restricted blocks removed at placement; available block queued with empty core; all-locked and non-builder preview and placement blocked");later(WorkspaceProbe::otherControls);
 }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
 static void otherControls(){try{
  open();check(down(KeyCode.mouseMiddle),"second middle not consumed");check(!visible(),"second middle did not close");up(KeyCode.mouseMiddle);
  open();hover(Core.scene.root.find("st-quick-group-1"));later(WorkspaceProbe::assign);
 }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
 static void assign(){try{check(Core.scene.root.find("st-quick-assign")!=null,"missing assign visible="+visible()+" group="+field(quick,"group")+" tag="+Core.settings.getString("schemesize-quick-group-1","")+" pointer="+Core.input.mouseX()+","+Core.input.mouseY());click(Core.scene.root.find("st-quick-assign"));later(()->{try{check(Core.scene.hasDialog(),"tag dialog missing");click(textButton(TAG));later(WorkspaceProbe::finish);}catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});}catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
 static void finish(){try{
  check(Core.settings.getString("schemesize-quick-group-1","").equals(TAG),"tag did not persist");open();Core.input.getKeyboard().keyDown(KeyCode.escape);check(Core.input.getInputMultiplexer().keyDown(KeyCode.escape),"Escape not consumed");Core.input.getKeyboard().keyUp(KeyCode.escape);Core.input.getKeyboard().postUpdate();check(!visible(),"Escape did not close");
  later(()->{try{
   var entry=((List<?>)call(keys,"entries")).stream().filter(e->{try{return call(e,"action").toString().equals("QUICK_SCHEMATICS");}catch(Exception x){throw new RuntimeException(x);}}).findFirst().orElseThrow();KeyBind binding=(KeyBind)call(entry,"binding");binding.value=new KeyBind.Axis(KeyCode.g);worldPoint();Core.input.getKeyboard().keyDown(KeyCode.g);call(keys,"dispatch");check(visible(),"G did not open");Core.input.getKeyboard().keyUp(KeyCode.g);Core.input.getKeyboard().postUpdate();call(keys,"dispatch");check(visible(),"G release closed menu");Core.input.getKeyboard().keyDown(KeyCode.g);call(keys,"dispatch");check(!visible(),"second G did not close");Core.input.getKeyboard().keyUp(KeyCode.g);Core.input.getKeyboard().postUpdate();
   Log.info("SCHEMETOOLS_WORKSPACE_PASS toggle edition=@ scale=@: click/release, tag, preview placement, outside-click consume, middle toggle, Escape, G toggle; rendered=true syntheticArc=true",edition,Core.settings.getInt("uiscale",100));
  }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
 }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
}
