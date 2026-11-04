package probe;
import arc.*;import arc.files.Fi;import arc.graphics.PixmapIO;import arc.input.*;import arc.math.geom.*;import arc.scene.*;import arc.scene.ui.*;import arc.util.*;
import mindustry.*;import mindustry.content.*;import mindustry.gen.*;import mindustry.game.*;import mindustry.ui.dialogs.BaseDialog;
import java.util.*;import java.lang.reflect.*;
public final class WorkspaceProbe{
 static final String POWER=String.valueOf(Iconc.power),DEFENSE=String.valueOf(Iconc.defense);
 static Object workspace,panel,keys,quick,boss;static ClassLoader loader;static String edition;static Schematic a,b;
 static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static void set(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
 static Object call(Object o,String n)throws Exception{Method m=o.getClass().getDeclaredMethod(n);m.setAccessible(true);return m.invoke(o);}
 static void require(boolean b,String s){if(!b)throw new AssertionError(s);}
 static void later(Runnable r){Time.runTask(25,()->{try{r.run();}catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});}
 static void capture(String f){var p=ScreenUtils.getFrameBufferPixmap(0,0,Core.graphics.getWidth(),Core.graphics.getHeight(),true);try{PixmapIO.writePng(new Fi(f),p);}finally{p.dispose();}}
 static void named(String n){Button b=Core.scene.root.find(n);require(b!=null,"missing "+n);require(!b.isDisabled(),"disabled "+n);b.fireClick();}
 static void walk(Element e,List<Element> out){out.add(e);if(e instanceof Group g)for(var c:g.getChildren())walk(c,out);}
 static List<Element> all(){List<Element> result=new ArrayList<>();walk(Core.scene.root,result);return result;}
 static void click(String text){var b=(TextButton)all().stream().filter(e->e instanceof TextButton t&&t.getText().toString().equals(text)).reduce((x,y)->y).orElseThrow(()->new AssertionError("missing text "+text));b.fireClick();}
 static void point(float x,float rawY)throws Exception{set(Core.input,"mouseX",(int)x);set(Core.input,"mouseY",(int)rawY);if(Core.input.mouseY()!=(int)rawY)set(Core.input,"mouseY",Core.graphics.getHeight()-(int)rawY);Core.scene.mouseMoved((int)x,(int)rawY);Core.scene.act();}
 static Vec2 center(Element e){return e.localToStageCoordinates(new Vec2(e.getWidth()/2,e.getHeight()/2));}
 static void hover(Element e)throws Exception{Vec2 p=center(e);point(p.x,p.y);}
 static void mouseClick(Element e)throws Exception{Vec2 p=center(e);point(p.x,p.y);var q=Core.scene.screenToStageCoordinates(new Vec2(p.x,p.y));var hit=Core.scene.hit(q.x,q.y,true);Log.info("QUICK_CLICK target=@ raw=@ stage=@ hitClass=@ hitName=@",e.name,p,q,hit==null?null:hit.getClass().getSimpleName(),hit==null?null:hit.name);Core.scene.touchDown((int)p.x,(int)p.y,0,KeyCode.mouseLeft);Core.scene.touchUp((int)p.x,(int)p.y,0,KeyCode.mouseLeft);}
 static void mapPointer()throws Exception{for(int y=Core.graphics.getHeight()/2;y<Core.graphics.getHeight()/2+1;y+=30)for(int x=Core.graphics.getWidth()/2;x<Core.graphics.getWidth()-170;x+=50){point(x,y);if(!Core.scene.hasMouse())return;}throw new AssertionError("no map pointer");}
 static boolean visible()throws Exception{return (boolean)call(quick,"visible");}
 static void hold()throws Exception{mapPointer();Core.input.getKeyboard().keyDown(KeyCode.mouseMiddle);boolean used=Core.input.getInputMultiplexer().touchDown(Core.input.mouseX(),Core.input.mouseY(),0,KeyCode.mouseMiddle);require(used&&visible(),"middle press not captured by held picker");}
 static void release()throws Exception{Core.input.getInputMultiplexer().touchUp(Core.input.mouseX(),Core.input.mouseY(),0,KeyCode.mouseMiddle);Core.input.getKeyboard().keyUp(KeyCode.mouseMiddle);Core.input.getKeyboard().postUpdate();call(keys,"dispatch");require(!visible(),"release did not close picker");}
 public static void main(String[]args){edition=args[0];
  Events.on(mindustry.game.EventType.ClientLoadEvent.class,e->Core.app.post(()->{try{
   Core.graphics.setWindowSize(1280,720);Core.settings.put("backgroundpause",false);Core.settings.put("schemesize-confirm-actions",false);Core.settings.put("schemesize-welcome",false);
   Vars.logic.reset();Vars.world.loadMap(Vars.maps.defaultMaps().first());Vars.state.rules.canGameOver=false;Vars.state.rules.waves=false;Vars.state.rules.disableUnitCap=true;
   for(int x=55;x<96;x++)for(int y=55;y<91;y++){Vars.world.tile(x,y).setBlock(Blocks.air);Vars.world.tile(x,y).setFloorNet(Blocks.stone,Blocks.air);}
   Vars.world.tile(85,76).setBlock(Blocks.coreShard,Team.sharded);Vars.world.tile(76,76).setBlock(Blocks.router,Team.sharded);
   Vars.logic.play();Vars.player.name="Ведущий";Vars.player.team(Team.sharded);Vars.player.set(620,608);Vars.player.add();Vars.player.unit(UnitTypes.mega.spawn(Team.sharded,620,608));Core.camera.position.set(620,608);
   loader=Vars.mods.list().first().main.getClass().getClassLoader();var c=Class.forName("org.schemetools.client.ToolWorkspace",true,loader);var f=c.getDeclaredField("active");f.setAccessible(true);workspace=f.get(null);panel=field(workspace,"panel");keys=field(panel,"keys");quick=field(panel,"quickMenu");
   a=schematic("Энергия",POWER,Blocks.powerNode);b=schematic("Защита",DEFENSE,Blocks.copperWall);Vars.schematics.add(a);Vars.schematics.add(b);for(int n=0;n<30;n++)Vars.schematics.add(schematic("Схема "+(n+1),POWER,new mindustry.world.Block[]{Blocks.router,Blocks.battery,Blocks.solarPanel,Blocks.junction,Blocks.duo,Blocks.copperWall,Blocks.powerNode}[n%7]));
   for(int i=0;i<6;i++)Core.settings.remove("schemesize-quick-group-"+i);
   later(WorkspaceProbe::firstHold);
  }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}));mindustry.desktop.DesktopLauncher.main(new String[0]);
 }
 static void originalReference(){try{
  var file=new java.io.File("C:/DiskSpasenie/ShemeSize/work/audit/o279/Scheme-Size-2.7.79.jar");
  if(!file.isFile()){Log.info("ORIGINAL_REFERENCE_SKIPPED: provide the unmodified O279 release under work/audit/o279");firstHold();return;}
  var referenceLoader=new java.net.URLClassLoader(new java.net.URL[]{file.toURI().toURL()},Core.class.getClassLoader());
  var type=Class.forName("scheme.ui.HexSelection",true,referenceLoader);var ring=(Element)type.getConstructor().newInstance();
  String[] labels={POWER,DEFENSE,String.valueOf(Iconc.crafting),String.valueOf(Iconc.logic),String.valueOf(Iconc.distribution),String.valueOf(Iconc.production)};
  for(String label:labels)type.getMethod("add",String.class,arc.func.Cons.class).invoke(ring,label,(arc.func.Cons<TextButton>)button->{});
  type.getMethod("updateAlignment").invoke(ring);var parent=new arc.scene.ui.layout.Table();parent.setFillParent(true);parent.add(ring).size(24);Core.scene.add(parent);Core.scene.act();point(Core.scene.getWidth()/2+60,Core.scene.getHeight()/2);
  later(()->{try{capture("original-hex-reference.png");parent.remove();referenceLoader.close();Log.info("ORIGINAL_HEX_REFERENCE: actual unmodified O279 HexSelection binary rendered for comparison only; not included in mod");firstHold();}catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
 }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
 static Schematic schematic(String name,String tag,mindustry.world.Block block){var tiles=new arc.struct.Seq<Schematic.Stile>();tiles.add(new Schematic.Stile(block,0,0,null,(byte)0));var s=new Schematic(tiles,arc.struct.StringMap.of("name",name),2,2);s.labels.add(tag);return s;}
 static void firstHold(){try{
  require(((List<?>)call(keys,"entries")).stream().anyMatch(e->{try{return call(e,"action").toString().equals("QUICK_SCHEMATICS")&&((arc.input.KeyBind)call(e,"binding")).value.key==KeyCode.mouseMiddle;}catch(Exception x){throw new RuntimeException(x);}}),"middle default missing");
  Vars.control.input.block=Blocks.copperWall;hold();Vars.control.input.update();hover(Core.scene.root.find("st-quick-group-0"));require(Vars.control.input.block==Blocks.copperWall,"vanilla pick conflicted");
  for(int i=0;i<6;i++)require(Core.scene.root.find("st-quick-group-"+i)!=null,"group missing "+i);
  later(()->{try{capture("quick-empty.png");pressAssignAndRelease();}catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
 }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
 static void mouseText(String text)throws Exception{var b=(TextButton)all().stream().filter(e->e instanceof TextButton t&&t.getText().toString().equals(text)).reduce((x,y)->y).orElseThrow();mouseClick(b);}
 static void pressAssignAndRelease()throws Exception{Element button=Core.scene.root.find("st-quick-assign");Vec2 p=center(button);point(p.x,p.y);Core.scene.touchDown((int)p.x,(int)p.y,0,KeyCode.mouseLeft);release();Core.scene.touchUp((int)p.x,(int)p.y,0,KeyCode.mouseLeft);later(()->{try{require(Core.scene.hasDialog(),"assignment lost when Middle released before Left");capture("tag-dialog.png");mouseText(POWER);later(WorkspaceProbe::releaseOnly);}catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});}
 static void releaseOnly(){try{
  require(Core.settings.getString("schemesize-quick-group-0","").equals(POWER),"mouse tag click not saved");hold();hover(Core.scene.root.find("st-quick-group-1"));
  later(()->{try{hover(Core.scene.root.find("st-quick-assign"));release();later(()->{try{require(Core.scene.hasDialog(),"release over Assign did not keep tag picker open");Core.scene.keyDown(KeyCode.escape);Core.scene.keyUp(KeyCode.escape);later(()->{try{require(!Core.scene.hasDialog()&&Core.settings.getString("schemesize-quick-group-1","").isEmpty(),"Escape changed tag or leaked dialog");directGroup();}catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});}catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});}catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
 }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
 static void directGroup(){try{
  hold();hover(Core.scene.root.find("st-quick-group-2"));mouseClick(Core.scene.root.find("st-quick-group-2"));
  later(()->{try{require(Core.scene.hasDialog(),"sector press did not open tag dialog while Middle held");call(keys,"dispatch");require(Core.scene.hasDialog()&&!visible(),"held update closed modal or reopened ring");release();later(()->{try{require(Core.scene.hasDialog(),"Middle release closed independent tag dialog");mouseText(DEFENSE);later(()->{try{require(Core.settings.getString("schemesize-quick-group-2","").equals(DEFENSE),"direct sector tag not saved");Log.info("SCHEMETOOLS_TAG_TRANSITION_PASS: Left-down/Middle-up/Left-up, release over Assign, delayed native mouse tag selection, direct sector while Middle held, update/release leaves modal open, Escape preserves tag");assigned();}catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});}catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});}catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
 }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
 static void assigned(){try{
  require(Core.settings.getString("schemesize-quick-group-0","").equals(POWER),"tag assignment not persisted");Core.settings.put("schemesize-quick-group-1",DEFENSE);Core.settings.put("schemesize-quick-group-2",String.valueOf(Iconc.crafting));Core.settings.put("schemesize-quick-group-3",String.valueOf(Iconc.logic));Core.settings.put("schemesize-quick-group-4",String.valueOf(Iconc.distribution));Core.settings.put("schemesize-quick-group-5",String.valueOf(Iconc.production));Core.settings.forceSave();
  hold();hover(Core.scene.root.find("st-quick-group-0"));later(()->{try{
   require(Core.scene.root.find("st-quick-card-0")!=null&&((List<?>)field(quick,"library")).size()>=2,"tagged card missing");bounds((Element)field(quick,"cards"));capture("quick-schematics.png");
   var scroll=(ScrollPane)field(quick,"pane");hover(scroll);call(keys,"dispatch");require((int)field(quick,"group")==0,"preview hover changed active sector");Core.scene.setScrollFocus(scroll);Core.input.getInputMultiplexer().scrolled(0,4);Core.scene.act();require(scroll.getScrollY()>0,"thumbnail wheel did not scroll");scroll.setScrollY(0);scroll.updateVisualScroll();
   hover(Core.scene.root.find("st-quick-group-1"));require((int)field(quick,"group")==1,"hover did not switch group");
   // Native ScrollPane positions its widget in draw(), so click after a rendered frame.
   later(WorkspaceProbe::chooseAndFinish);
  }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
 }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
 static void chooseAndFinish(){try{
   mouseClick(Core.scene.root.find("st-quick-card-0"));require(Vars.control.input.selectPlans.size>0,"schematic did not enter ordinary placement");require(!visible(),"selection did not close picker");release();Vars.control.input.selectPlans.clear();
   // Keep a held menu open over a pause-free frame, then release without choosing.
   hold();release();require(!Core.scene.hasKeyboard()&&Core.scene.getScrollFocus()==null,"picker leaked input");
   var text=new TextField("");Core.scene.add(text);Core.scene.setKeyboardFocus(text);Core.input.getKeyboard().keyDown(KeyCode.mouseMiddle);call(keys,"dispatch");require(!visible(),"picker opened while typing");Core.input.getKeyboard().keyUp(KeyCode.mouseMiddle);Core.input.getKeyboard().postUpdate();Core.scene.unfocus(text);text.remove();
   extraQuickChecks();
   Log.info("SCHEMETOOLS_QUICK_PASS: held Middle Mouse, native pick prevented, six groups, empty assignment, saved tags, real hover previews, ordinary placement, release/cancel, direction gaps, G rebind, module guard and 70/130 size bounds");
   if(edition.equals("god"))startCombat();else pass();
 }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
 static void extraQuickChecks()throws Exception{
  Object entry=((List<?>)call(keys,"entries")).stream().filter(e->{try{return call(e,"action").toString().equals("QUICK_SCHEMATICS");}catch(Exception x){throw new RuntimeException(x);}}).findFirst().orElseThrow();
  KeyBind binding=(KeyBind)call(entry,"binding");
  Core.settings.put("schemesize-module-schematics",false);mapPointer();Core.input.getKeyboard().keyDown(KeyCode.mouseMiddle);call(keys,"dispatch");require(!visible(),"disabled module opened picker");Core.input.getKeyboard().keyUp(KeyCode.mouseMiddle);Core.input.getKeyboard().postUpdate();Core.settings.put("schemesize-module-schematics",true);
  binding.value=new KeyBind.Axis(KeyCode.g);mapPointer();Core.input.getKeyboard().keyDown(KeyCode.g);call(keys,"dispatch");require(visible(),"G binding did not open picker");Core.input.getKeyboard().keyUp(KeyCode.g);Core.input.getKeyboard().postUpdate();call(keys,"dispatch");require(!visible(),"G release failed");binding.value=new KeyBind.Axis(KeyCode.mouseMiddle);
  for(int size:new int[]{70,130}){Core.settings.put("schemesize-quick-size",size);hold();float ax=(float)field(quick,"anchorX"),ay=(float)field(quick,"anchorY"),scale=(float)field(quick,"scale");point(ax+55*scale,ay+5*scale);call(keys,"dispatch");require((int)field(quick,"group")==0,"direction gap failed");bounds((Element)field(quick,"cards"));for(int i=0;i<6;i++)bounds(Core.scene.root.find("st-quick-group-"+i));release();}
  Core.settings.put("schemesize-quick-size",100);
 }
 static void startCombat(){try{
  var guest=Player.create();guest.name="Босс";guest.con=new FakeConnection();guest.team(Team.sharded);guest.set(640,608);guest.add();guest.unit(UnitTypes.reign.spawn(Team.sharded,640,608));
  set(panel,"selectedPlayerUuid",guest.uuid());set(panel,"bossTeamIndex",1);set(panel,"hunterTeamIndex",0);set(panel,"preparationSeconds","0");set(panel,"durationSeconds","300");
  panel.getClass().getMethod("openPage",String.class).invoke(panel,"event");click(Core.bundle.get("st.boss.start"));click(Core.bundle.get("ok"));boss=field(panel,"bossRounds");require((boolean)call(boss,"isActive"),"round inactive");call(workspace,"close");later(WorkspaceProbe::combat);
 }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
 static void combat(){try{
  Element dock=Core.scene.root.find("st-combat-dock");require(dock!=null&&dock.visible,"God combat dock absent: actor="+(dock!=null)+" visible="+(dock!=null&&dock.visible)+" phase="+call(boss,"phase")+" outcome="+call(boss,"outcome")+" hud="+Core.settings.getBool("schemetools-hud",true));require(Core.scene.root.find("scheme-tool-st.schematic.title")==null,"full navigation still occupies fight HUD");
  capture("combat-expanded.png");named("st-combat-pause");require(call(boss,"phase").toString().equals("PAUSED")&&!Core.scene.hasDialog(),"quick pause failed");named("st-combat-pause");require(call(boss,"phase").toString().equals("RUNNING"),"quick resume failed");
  named("st-combat-collapse");later(()->{try{capture("combat-collapsed.png");require(Core.scene.root.find("st-combat-spawn")==null,"collapse left actions visible");named("st-combat-collapse");later(WorkspaceProbe::combatPlayers);}catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
 }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
 static void combatPlayers(){try{
  named("st-combat-players");later(()->{try{require(Core.scene.root.find("st-player-watch-inline")!=null&&Core.scene.root.find("st-player-bring-inline")!=null,"inline player commands missing");capture("combat-player-context.png");call(workspace,"close");Core.settings.put("schemesize-combat-action-finish",true);later(WorkspaceProbe::finishCombat);}catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
 }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
 static void finishCombat(){try{
  named("st-combat-finish");require(Core.scene.hasDialog(),"dangerous finish lacks confirmation");click(Core.bundle.get("cancel"));require((boolean)call(boss,"isActive"),"cancelled finish ended round");later(()->{try{named("st-combat-finish");click(Core.bundle.get("ok"));later(()->{try{require(!(boolean)call(boss,"isActive"),"finish failed");require(Core.scene.root.find("scheme-tool-st.schematic.title")!=null,"navigation not restored");Log.info("SCHEMETOOLS_COMBAT_PASS: phase/clock/boss HUD, hidden navigation, pause/resume, collapse/expand, visible player commands, selected quick actions, confirmed finish/cancel");pass();}catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});}catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
 }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
 static void bounds(Element e){Vec2 p=e.localToStageCoordinates(new Vec2());require(p.x>=-1&&p.y>=-1&&p.x+e.getWidth()<=Core.scene.getWidth()+1&&p.y+e.getHeight()<=Core.scene.getHeight()+1,"quick cards outside window");}
 static void pass(){Log.info("SCHEMETOOLS_WORKSPACE_PASS quick/combat edition=@ scale=@ rendered=true syntheticArc=true",edition,Core.settings.getInt("uiscale",100));}
 static final class FakeConnection extends mindustry.net.NetConnection{FakeConnection(){super("test-only");uuid="quick-combat-guest";}public void send(Object o,boolean reliable){}public void close(){}}
}
