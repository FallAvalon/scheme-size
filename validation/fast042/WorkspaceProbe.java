package probe;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.graphics.PixmapIO;
import arc.input.KeyCode;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.ui.Button;
import arc.scene.ui.TextButton;
import arc.util.Log;
import arc.util.ScreenUtils;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.UnitTypes;
import mindustry.game.Team;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.ui.dialogs.BaseDialog;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Observer of unmodified edition artifacts. Arc events are synthetic, never OS input. */
public final class WorkspaceProbe {
    static Object workspace, panel;
    static String edition;
    static ClassLoader loader;
    static boolean portraitChecked,conceptChecked;
    static boolean terrainChecked;
    static boolean godControlsChecked, newPanelsChecked, replacementChecked, menuChecked, unitUiChecked;
    static Object field(Object object,String key)throws Exception {Field f=object.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(object);}
    static void set(Object object,String key,Object value)throws Exception {Field f=object.getClass().getDeclaredField(key);f.setAccessible(true);f.set(object,value);}
    static Object call(Object object,String method)throws Exception {return object.getClass().getMethod(method).invoke(object);}
    static Object current()throws Exception{return call(workspace,"current");}
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void collect(Element e,List<Element> out){out.add(e);if(e instanceof Group g)for(Element c:g.getChildren())collect(c,out);}
    static List<Element> all(){List<Element> out=new ArrayList<>();collect(Core.scene.root,out);return out;}
    static void click(String key){
        TextButton b=(TextButton)all().stream().filter(e->e instanceof TextButton t&&t.getText().toString().equals(Core.bundle.get(key))).reduce((first,last)->last).orElseThrow(()->new AssertionError("missing button "+key));
        require(!b.isDisabled(),"disabled "+key);b.fireClick();
    }
    static void tool(String key){((Button)Core.scene.root.find("scheme-tool-"+key)).fireClick();}
    static void typeField(String old,String value)throws Exception{
        List<Element> elements=new ArrayList<>();collect(((BaseDialog)current()).cont,elements);
        var input=(arc.scene.ui.TextField)elements.stream().filter(e->e instanceof arc.scene.ui.TextField t&&t.getText().equals(old)).findFirst().orElseThrow();
        input.setText("");Core.scene.setKeyboardFocus(input);
        for(char c:value.toCharArray())Core.scene.keyTyped(c);
    }
    static BaseDialog selection(){return (BaseDialog)all().stream().filter(e->e.getClass().getSimpleName().equals("AreaSelectionDialog")).findFirst().orElseThrow();}
    static void capture(String file){var p=ScreenUtils.getFrameBufferPixmap(0,0,Core.graphics.getWidth(),Core.graphics.getHeight(),true);try{PixmapIO.writePng(new Fi(file),p);}finally{p.dispose();}}
    static void later(Runnable r){Time.runTask(35,()->{try{r.run();}catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});}
    public static void main(String[] args)throws Exception{
        edition=args[0];
        Events.on(ClientLoadEvent.class,event->Core.app.post(()->{
            try{
                Core.graphics.setWindowSize(1280,720);
                require(!Core.settings.getBool("schemesize-confirm-actions",false),"routine confirmations must default off");Core.settings.put("schemesize-confirm-actions",true);
                Core.settings.put("schemesize-welcome",false);
                Vars.logic.reset();Vars.world.loadMap(Vars.maps.defaultMaps().first());
                Vars.state.rules.canGameOver=false;Vars.state.rules.waves=false;
                for(int x=55;x<=95;x++)for(int y=55;y<=90;y++){Vars.world.tile(x,y).setBlock(Blocks.air);Vars.world.tile(x,y).setFloorNet(Blocks.stone,Blocks.air);}
                Vars.world.tile(85,76).setBlock(Blocks.coreShard,Team.sharded);
                Vars.world.tile(76,76).setBlock(Blocks.router,Team.sharded);
                Vars.world.tile(60,76).setOverlayNet(Blocks.spawn);Vars.spawner.getSpawns().clear();Vars.spawner.getSpawns().add(Vars.world.tile(60,76));
                Vars.logic.play();Vars.player.team(Team.sharded);Vars.player.set(620,608);Vars.player.add();Vars.player.unit(UnitTypes.mega.spawn(Team.sharded,620,608));
                Core.camera.position.set(620,608);
                loader=Vars.mods.list().first().main.getClass().getClassLoader();
                Class<?> type=Class.forName("org.schemetools.client.ToolWorkspace",true,loader);Field active=type.getDeclaredField("active");active.setAccessible(true);workspace=active.get(null);panel=field(workspace,"panel");
                require(all().stream().filter(e->"scheme-size-hud".equals(e.name)).count()==1,"duplicate HUD");
                require((Core.scene.root.find("scheme-tool-st.event")!=null)==edition.equals("god"),"edition tools");
                tool("st.schematic.title");
                require(current().getClass().getSimpleName().equals("SchematicsDialog"),"schematic context");
                require(!Core.scene.hasDialog(),"workspace must not block vanilla map input");
                typeField("","keep-search");require(field(current(),"search").equals("keep-search"),"typed search not applied");
                tool("st.routes.title");tool("st.schematic.title");require(field(current(),"search").equals("keep-search"),"search was lost");
                set(current(),"search","");
                click("st.selection.title");
                BaseDialog select=selection();select.hide();
                require(current().getClass().getSimpleName().equals("SchematicsDialog"),"cancel lost context");
                click("st.selection.title");select=selection();
                select.getClass().getMethod("accept",int.class,int.class,int.class,int.class).invoke(select,75,75,77,77);
                require(field(current(),"selected")!=null,"capture did not select a schematic");
                click("st.schematic.use");require(current()==null,"paste did not release context");
                require(Vars.control.input.selectPlans.size>0,"native placement preview absent");
                Vars.control.input.selectPlans.clear();
                tool("st.build.title");click("st.selection.title");
                select=selection();select.getClass().getMethod("accept",int.class,int.class,int.class,int.class).invoke(select,74,74,78,78);
                require(field(current(),"width").equals("5"),"build area width");
                tool("st.routes.title");click("st.routes.enabled");
                later(WorkspaceProbe::routes);
            }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}
        }));
        mindustry.desktop.DesktopLauncher.main(new String[0]);
    }
    static void routes(){try{
        Object routes=field(panel,"routes");Object result=call(routes,"result");
        if(result==null){later(WorkspaceProbe::routes);return;}
        require(!((List<?>)call(result,"routes")).isEmpty(),"no route");
        capture("routes-context.png");
        tool("st.schematic.title");click("st.selection.title");later(WorkspaceProbe::pointer);
    }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
    static void pointer(){try{
        Vec2 a=Core.camera.project(new Vec2(76*8,76*8)),b=Core.camera.project(new Vec2(78*8,78*8));
        Core.scene.touchDown(Math.round(a.x),Math.round(a.y),0,KeyCode.mouseLeft);
        Core.scene.touchDragged(Math.round(b.x),Math.round(b.y),0);
        Core.scene.touchUp(Math.round(b.x),Math.round(b.y),0,KeyCode.mouseLeft);
        require(Boolean.TRUE.equals(field(selection(),"ready")),"pointer selection did not preview");
        later(()->{try{
            capture("map-selection.png");
            Core.scene.keyDown(KeyCode.enter);Core.scene.keyUp(KeyCode.enter);
            require(!Core.scene.hasDialog(),"Enter did not commit selection");
            require(field(current(),"x1").equals("76") && field(current(),"x2").equals("78"),"pointer-to-tile projection differs: "+field(current(),"x1")+","+field(current(),"x2")+" y="+field(current(),"y1")+","+field(current(),"y2"));
            click("st.selection.title");
            Core.input.getKeyboard().keyDown(KeyCode.escape);Core.scene.keyDown(KeyCode.escape);
            later(()->{Core.input.getKeyboard().keyUp(KeyCode.escape);Core.scene.keyUp(KeyCode.escape);later(()->{require(!Core.scene.hasDialog()&&!Vars.ui.paused.isShown(),"Escape leaked into vanilla pause");organizer();});});
        }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
    }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
    static void organizer(){try{
        if(edition.equals("god")){
            tool("st.players");require(current()==panel,"players context");
            click("st.workspace.player-point");BaseDialog target=selection();
            target.getClass().getMethod("accept",int.class,int.class,int.class,int.class).invoke(target,Vars.player.tileX(),Vars.player.tileY(),Vars.player.tileX(),Vars.player.tileY());
            require(field(panel,"selectedPlayerUuid").equals(Vars.player.uuid()),"map player target");
            tool("st.items");click("st.workspace.core-point");target=selection();
            target.getClass().getMethod("accept",int.class,int.class,int.class,int.class).invoke(target,85,76,85,76);
            typeField("100","37");require(field(panel,"itemDelta").equals("37"),"typed resources not applied");tool("st.units");
            click("st.roster.tab-create");typeField("1","2");require(field(panel,"spawnCount").equals("2"),"typed unit count not applied");
            click("st.workspace.point");BaseDialog select=selection();
            select.getClass().getMethod("accept",int.class,int.class,int.class,int.class).invoke(select,79,76,79,76);
            // Native confirm must still guard the shared-world operation.
            require(Core.scene.hasDialog(),"spawn confirmation absent");
            later(WorkspaceProbe::confirmSpawn);
        }else {tool("st.routes.title");later(WorkspaceProbe::finish);}
    }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
    static void confirmSpawn(){try{
        click("ok");later(()->{try{
            require(Team.sharded.data().units.size>=3,"unit spawn not applied");
            tool("st.items");require(field(panel,"itemDelta").equals("37"),"resource delta lost");
            Team.sharded.core().items.set(mindustry.content.Items.copper,0);
            set(panel,"itemIndex",(int)mindustry.content.Items.copper.id);
            tool("st.items");click("st.apply");
            later(()->{click("ok");later(WorkspaceProbe::event);});
        }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
    }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
    static void event(){try{
        require(Team.sharded.core().items.get(mindustry.content.Items.copper)==37,"resource action did not apply");
        mindustry.gen.Player guest=mindustry.gen.Player.create();guest.name="Тестовый босс";guest.con=new FakeConnection();guest.team(Team.sharded);guest.set(640,608);guest.add();guest.unit(UnitTypes.reign.spawn(Team.sharded,640,608));
        set(panel,"selectedPlayerUuid",guest.uuid());set(panel,"bossTeamIndex",1);set(panel,"hunterTeamIndex",0);set(panel,"preparationSeconds","0");set(panel,"durationSeconds","60");set(panel,"deathPenaltySeconds","0");
        tool("st.event");require(field(panel,"page").equals("event"),"event context absent");
        click("st.boss.start");later(()->{click("ok");later(WorkspaceProbe::pauseEvent);});
    }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
    static void pauseEvent(){try{
        Object boss=field(panel,"bossRounds");require(Boolean.TRUE.equals(call(boss,"isActive")),"boss did not start");
        capture("event-context.png");click("st.boss.pause");
        later(()->{click("ok");later(()->{try{
            require(call(boss,"phase").toString().equals("PAUSED"),"pause did not apply: "+call(boss,"phase")+" / "+call(boss,"outcome"));
            click("st.boss.finish");later(()->{click("ok");later(()->{try{require(!Boolean.TRUE.equals(call(boss,"isActive")),"finish did not apply");tool("st.units");later(WorkspaceProbe::finish);}catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});});
        }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});});
    }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
    static void finish(){try{
        require(!Core.scene.hasDialog(),"unexpected modal");
        TableCheck.check(Core.scene.root.find("scheme-size-context"));
        capture(edition+"-workspace.png");
        Core.scene.setScrollFocus(((BaseDialog)current()).cont);
        Core.input.getKeyboard().keyDown(KeyCode.escape);Core.scene.keyDown(KeyCode.escape);
        require(current()==null,"Escape did not close the workspace");
        later(WorkspaceProbe::keyboard);
    }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
    static void keyboard(){Core.input.getKeyboard().keyUp(KeyCode.escape);Core.scene.keyUp(KeyCode.escape);later(WorkspaceProbe::keyboardReleased);}
    static void keyboardReleased(){try{
        require(!Core.scene.hasKeyboard(),"keyboard focus leaked");
        require(Core.scene.getScrollFocus()==null,"scroll focus leaked");
        require(!Vars.state.isPaused()&&!Vars.ui.paused.isShown(),"Escape paused game behind context");
        Object keys=field(panel,"keys"),entry=((List<?>)call(keys,"entries")).get(0);
        Class<?> chordType=Class.forName("org.schemetools.client.ModKeys$Chord",true,loader);
        Object chord=chordType.getConstructor(boolean.class,boolean.class,boolean.class).newInstance(false,false,false);
        keys.getClass().getMethod("assign",entry.getClass(),KeyCode.class,chordType).invoke(null,entry,KeyCode.f8,chord);
        Core.input.getKeyboard().keyDown(KeyCode.f8);call(keys,"dispatch");Core.input.getKeyboard().keyUp(KeyCode.f8);
        require(current()==panel,"bound key did not open workspace");
        call(panel,"openRoutes");require(current()!=panel,"child context absent");
        ((BaseDialog)current()).hide();require(current()==panel,"Back did not restore parent context");
        call(workspace,"close");
        teleportChecks();
        fastChecks();
        helpScreens();
    }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
    static void mapPointer()throws Exception{
        for(int y=Core.graphics.getHeight()-150;y>=Core.graphics.getHeight()/2;y-=40)for(int x=Core.graphics.getWidth()/2;x<Core.graphics.getWidth()-180;x+=60){
            set(Core.input,"mouseX",x);set(Core.input,"mouseY",Core.graphics.getHeight()-y);Core.scene.mouseMoved(x,Core.graphics.getHeight()-y);Core.scene.act();
            if(!Core.scene.hasMouse()&&Vars.world.tileWorld(Core.input.mouseWorldX(),Core.input.mouseWorldY())!=null)return;
        }
        throw new AssertionError("no exposed test map point");
    }
    static void teleportChecks() throws Exception {
        Object keys=field(panel,"keys"),actions=field(panel,"actions");
        Object entry=((List<?>)call(keys,"entries")).stream().filter(e->{try{return call(e,"action").toString().equals("TELEPORT");}catch(Exception x){throw new RuntimeException(x);}}).findFirst().orElseThrow();
        Object chord=keys.getClass().getMethod("chord",entry.getClass()).invoke(null,entry);
        require((boolean)call(chord,"alt")&&!(boolean)call(chord,"ctrl")&&!(boolean)call(chord,"shift"),"default Alt chord");
        require(((arc.input.KeyBind)call(entry,"binding")).value.key==KeyCode.mouseBack,"default mouse back");
        var unit=Vars.player.unit(); float startX=unit.x,startY=unit.y;
        mapPointer();
        require(!Core.scene.hasMouse(),"test pointer must be on map");
        float x=Core.input.mouseWorldX(),y=Core.input.mouseWorldY();
        var keyboard=Core.input.getKeyboard();
        keyboard.postUpdate();keyboard.keyDown(KeyCode.mouseBack);call(keys,"dispatch");keyboard.keyUp(KeyCode.mouseBack);keyboard.postUpdate();
        require(unit.within(startX,startY,.01f),"unmodified mouse back teleported");
        unit.vel.set(6,7);
        keyboard.keyDown(KeyCode.altLeft);keyboard.keyDown(KeyCode.mouseBack);call(keys,"dispatch");keyboard.postUpdate();
        require(unit.within(x,y,.01f)&&Vars.player.within(x,y,.01f),"Alt+Mouse Back did not teleport to world cursor");
        require(unit.vel.isZero(),"velocity not cleared");
        set(Core.input,"mouseX",950);call(keys,"dispatch");
        require(unit.within(x,y,.01f),"held button repeated teleport");
        keyboard.keyUp(KeyCode.mouseBack);keyboard.keyUp(KeyCode.altLeft);keyboard.postUpdate();
        // UI, chat, disabled module, and remote-client authority must not mutate the player.
        Core.settings.put("schemesize-module-players",false);pulseTeleport(keys);require(unit.within(x,y,.01f),"disabled module");Core.settings.put("schemesize-module-players",true);
        BaseDialog modal=new BaseDialog("Teleport guard");modal.show();pulseTeleport(keys);require(unit.within(x,y,.01f),"modal guard");modal.hide(null);
        var input=new arc.scene.ui.TextField("");Core.scene.add(input);Core.scene.setKeyboardFocus(input);pulseTeleport(keys);require(unit.within(x,y,.01f),"chat/keyboard guard");Core.scene.unfocus(input);input.remove();
        tool("st.schematic.title");Core.scene.act();set(Core.input,"mouseX",30);set(Core.input,"mouseY",30);Core.scene.mouseMoved(30,30);Core.scene.act();
        require(Core.scene.hasMouse(),"test pointer must hit panel");pulseTeleport(keys);require(unit.within(x,y,.01f),"UI mouse guard");call(workspace,"close");
        mapPointer();
        boolean active=(boolean)field(Vars.net,"active"),server=(boolean)field(Vars.net,"server");
        try{set(Vars.net,"active",true);set(Vars.net,"server",false);pulseTeleport(keys);require(unit.within(Core.input.mouseWorldX(),Core.input.mouseWorldY(),.01f),"remote self movement blocked");}
        finally{set(Vars.net,"active",active);set(Vars.net,"server",server);}
        var teleport=actions.getClass().getMethod("teleport",mindustry.gen.Player.class,float.class,float.class);
        try{teleport.invoke(actions,Vars.player,Float.NaN,y);throw new AssertionError("NaN accepted");}catch(java.lang.reflect.InvocationTargetException expected){require(expected.getCause() instanceof IllegalArgumentException,"NaN validation");}
        try{teleport.invoke(actions,Vars.player,-10000f,y);throw new AssertionError("outside map accepted");}catch(java.lang.reflect.InvocationTargetException expected){require(expected.getCause() instanceof IllegalArgumentException,"bounds validation");}
        teleport.invoke(actions,Vars.player,startX,startY);
        Log.info("SCHEMETOOLS_TELEPORT_PASS: Alt+Mouse Back world cursor, player/unit positions, zero velocity, no repeat while held, no plain click, modal/keyboard/UI/module guards, remote self snapshot movement, NaN/bounds rejection; synthetic Arc + SDL pointer fields");
    }
    static void pulseTeleport(Object keys)throws Exception{
        var k=Core.input.getKeyboard();k.postUpdate();k.keyDown(KeyCode.altLeft);k.keyDown(KeyCode.mouseBack);call(keys,"dispatch");k.keyUp(KeyCode.mouseBack);k.keyUp(KeyCode.altLeft);k.postUpdate();
    }
    static void fastChecks()throws Exception{
        tool("st.build.title");Core.scene.act();
        mapPointer();
        Core.scene.setScrollFocus(((BaseDialog)current()).cont);
        float middle=(Vars.renderer.minScale()+Vars.renderer.maxScale())/2;Vars.renderer.setScale(middle);
        float before=Vars.renderer.targetscale;Core.input.getKeyboard().postUpdate();Core.input.getKeyboard().scrolled(0,-1);Core.input.getInputMultiplexer().scrolled(0,-1);
        require(Core.scene.getScrollFocus()==null,"panel retained map wheel focus");
        Vars.control.input.update();require(Vars.renderer.targetscale>before,"native camera zoom in blocked by panel");
        before=Vars.renderer.targetscale;Core.input.getKeyboard().postUpdate();Core.input.getKeyboard().scrolled(0,1);Core.input.getInputMultiplexer().scrolled(0,1);Vars.control.input.update();
        require(Vars.renderer.targetscale<before,"native camera zoom out blocked");Core.input.getKeyboard().postUpdate();
        named("st-build-pick-block");Core.scene.act();require(Core.scene.hasDialog(),"content picker absent");
        set(Core.input,"mouseX",Core.graphics.getWidth()-15);set(Core.input,"mouseY",400);Core.scene.mouseMoved(Core.graphics.getWidth()-15,320);Core.scene.act();
        before=Vars.renderer.targetscale;Core.input.getInputMultiplexer().scrolled(0,-1);if(Vars.renderer.targetscale<=before){var pt=Core.scene.screenToStageCoordinates(new Vec2(Core.input.mouseX(),Core.graphics.getHeight()-Core.input.mouseY()));Element hit=Core.scene.hit(pt.x,pt.y,true);Log.info("ZOOM_DEBUG dialog=@ name=@ hit=@",Core.scene.getDialog(),Core.scene.getDialog().name,hit);for(int q=0;q<5&&hit!=null;q++,hit=hit.parent)Log.info("ZOOM_ANCESTOR @ name=@ class=@",hit,hit.name,hit.getClass());capture("zoom-failure.png");}require(Vars.renderer.targetscale>before,"camera zoom outside mod picker blocked");Core.scene.getDialog().hide(null);
        Core.settings.put("schemesize-confirm-actions",false);
        if(edition.equals("god")){
            click("st.arena.edit-map");require(current().getClass().getSimpleName().equals("ArenaDialog"),"terrain shortcut absent");
            Object arena=current();named("st-terrain-BLOCK");clickPrefix("st.arena.layer.block");named("st-content-stone-wall");if(Core.scene.getDialog()!=null)Core.scene.getDialog().hide(null);
            String[] coords=(String[])field(arena,"coords");coords[0]="90";coords[1]="80";coords[2]="91";coords[3]="81";
            var original=Vars.world.tile(90,80).block();click("st.arena.apply");
            require(!Core.scene.hasDialog()&&Vars.world.tile(90,80).block()==Blocks.stoneWall&&Vars.world.tile(91,81).block()==Blocks.stoneWall,"quick natural wall placement failed");
            click("st.arena.undo");require(Vars.world.tile(90,80).block()==original,"wall undo failed");
            tool("st.build.title");click("st.arena.edit-map");require(field(current(),"block")==Blocks.stoneWall,"terrain picker parameters lost");
            call(panel,"openUnitTools");Object roster=current();set(roster,"allTypes",false);((java.util.Set<Integer>)field(roster,"typeIds")).add((int)UnitTypes.flare.id);
            var doomed=UnitTypes.flare.spawn(Vars.player.team(),800,600);call(roster,"refreshWidth");named("st-roster-kill-matching");
            require(doomed.dead&&!Vars.player.dead()&&!Core.scene.hasDialog(),"routine kill did not execute immediately/protected player");
        }
        Core.settings.put("schemesize-confirm-actions",true);call(workspace,"close");
        Log.info("SCHEMETOOLS_FAST_UI_PASS: native map zoom in/out with panel scroll focus, zoom outside real content picker, routine confirmations default off; God natural wall selection/apply/undo/persistence and immediate filtered unit death with protected player");
    }
    static void helpScreens(){try{
        tool("st.schematic.title");set(current(),"advanced",false);set(current(),"imports",false);call(current(),"refreshWidth");
        later(()->{try{
            bounds();capture("compact-schematics.png");
            Object selected=field(current(),"selected");
            List<Element> content=new ArrayList<>();collect(((BaseDialog)current()).cont,content);
            TextButton help=(TextButton)content.stream().filter(e->e instanceof TextButton b&&b.getText().toString().equals("?")).findFirst().orElseThrow();help.fireClick();
            require(Core.scene.hasDialog(),"help did not open");require(field(current(),"selected")==selected,"help changed selected schematic");
            later(()->{capture("inline-help.png");click("st.help.close");later(()->{
                require(!Core.scene.hasDialog(),"help did not release focus after close");tool("st.visual.title");later(WorkspaceProbe::visualScreen);
            });});
        }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
    }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
    static void bounds()throws Exception{
        Element context=Core.scene.root.find("scheme-size-context");Vec2 edge=context.localToStageCoordinates(new Vec2());
        List<Element> elements=new ArrayList<>();collect(((BaseDialog)current()).cont,elements);
        for(Element element:elements)if(element instanceof Button){Vec2 pos=element.localToStageCoordinates(new Vec2());require(pos.x>=edge.x-1 && pos.x+element.getWidth()<=edge.x+context.getWidth()+1,"control clipped horizontally: "+element);}
    }
    static void visualScreen(){try{
        bounds();capture("compact-visuals.png");
        var check=(arc.scene.ui.CheckBox)all().stream().filter(e->e instanceof arc.scene.ui.CheckBox b&&b.getText().toString().equals(Core.bundle.get("st.short.visual.grid"))).findFirst().orElseThrow();
        boolean old=Core.settings.getBool("schemesize-visual-grid",false);check.fireClick();require(Core.settings.getBool("schemesize-visual-grid",false)!=old,"grid toggle failed");check.fireClick();
        if(edition.equals("god")){tool("st.players");later(()->{try{bounds();capture("compact-players.png");helpDone();}catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});}else helpDone();
    }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
    static void helpDone(){try{
        if(!conceptChecked){conceptChecked=true;conceptChecks();return;}
        if(!portraitChecked && Core.settings.getInt("uiscale",100)==100){
            portraitChecked=true;tool("st.schematic.title");Core.graphics.setWindowSize(800,900);
            later(()->{try{bounds();capture("compact-portrait.png");Core.graphics.setWindowSize(1280,720);later(WorkspaceProbe::helpDone);}catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});return;
        }
        if(!terrainChecked){
            terrainChecked=true;tool("st.schematic.title");
            Vars.world.tile(76,76).setFloorNet(Blocks.stone,Blocks.air);Vars.world.tile(80,80).setFloorNet(Blocks.sand,Blocks.air);
            Class<?> layer=Class.forName("org.schemetools.schematic.SchematicTools$Layer",true,loader);
            Object floor=java.util.Arrays.stream(layer.getEnumConstants()).filter(v->v.toString().equals("FLOOR")).findFirst().orElseThrow();
            Object schematic=Class.forName("org.schemetools.schematic.SchematicTools",true,loader).getMethod("capture",int.class,int.class,int.class,int.class,layer).invoke(null,76,76,76,76,floor);
            var select=current().getClass().getDeclaredMethod("select",mindustry.game.Schematic.class);select.setAccessible(true);select.invoke(current(),schematic);
            set(current(),"pasteX","80");set(current(),"pasteY","80");
            var paste=current().getClass().getDeclaredMethod("pasteTerrain");paste.setAccessible(true);paste.invoke(current());
            later(()->{try{
                click("ok");Class<?> batches=Class.forName("org.schemetools.client.TerrainPaste",true,loader);
                require(Boolean.TRUE.equals(batches.getMethod("active").invoke(null)),"paste did not start");
                click("st.schematic.batch-cancel");require(Boolean.FALSE.equals(batches.getMethod("active").invoke(null)),"cancel did not stop paste");
                require(Vars.world.tile(80,80).floor()==Blocks.sand,"cancelled paste changed destination");
                Log.info("SCHEMETOOLS_TERRAIN_CANCEL_UI_PASS: status/cancel appear immediately after confirm; cancel stops job before world mutation");later(WorkspaceProbe::helpDone);
            }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});return;
        }
        if(!newPanelsChecked){
            newPanelsChecked=true;tool("st.build.title");later(()->{try{bounds();capture("compact-build.png");
                ((TextButton)all().stream().filter(e->e instanceof TextButton t&&t.getText().toString().startsWith(Core.bundle.get("st.workspace.advanced"))).findFirst().orElseThrow()).fireClick();later(()->{try{bounds();capture("compact-build-advanced.png");
                    if(edition.equals("god")){tool("st.items");later(()->{try{bounds();capture("compact-items.png");
                        String label=Core.bundle.get("st.item")+": "+mindustry.content.Items.copper.localizedName;
                        ((TextButton)all().stream().filter(e->e instanceof TextButton t&&t.getText().toString().equals(label)).findFirst().orElseThrow()).fireClick();
                        later(()->{try{capture("native-item-grid.png");((Button)Core.scene.root.find("st-content-graphite")).fireClick();require(((Integer)field(panel,"itemIndex"))==mindustry.content.Items.graphite.id,"icon did not select graphite");
                            presetControls();
                        }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
                    }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});}else helpDone();
                }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
            }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});return;
        }
        if(edition.equals("god")&&!godControlsChecked){godControlsChecked=true;godControls();return;}
        if(!replacementChecked){replacementChecked=true;newPanelChecks();return;}
        if(!unitUiChecked){unitUiChecked=true;unitUi();return;}
        if(!menuChecked){menuChecked=true;menuSettings();return;}
        call(workspace,"close");require(!Core.scene.hasKeyboard(),"help left keyboard focus");
        Log.info("SCHEMETOOLS_WORKSPACE_PASS edition=@ syntheticArc=schematics,capture,paste,cancel,build,route,drag-preview,Enter,Escape,F8,back,focus-release@ rendered=true scale=@",edition,edition.equals("god")?",map-player,map-core,resources+37,unit-spawn2,event-start-pause-finish":"",Core.settings.getInt("uiscale",100));
        Log.info("SCHEMETOOLS_INLINE_HELP_PASS: help opens without changing selection, closes, visual toggle works, compact controls inside context");
    }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
    static void clickPrefix(String key){
        TextButton button=(TextButton)all().stream().filter(e->e instanceof TextButton t&&t.getText().toString().startsWith(Core.bundle.get(key))).findFirst().orElseThrow();
        require(!button.isDisabled(),"disabled "+key);button.fireClick();
    }
    static void unitUi(){try{
        Object actions=field(panel,"actions");Class<?> playerClass=Class.forName("org.schemetools.client.PlayerToolsDialog",true,loader);
        BaseDialog player=(BaseDialog)playerClass.getConstructor(actions.getClass(),mindustry.gen.Player.class).newInstance(actions,Vars.player);player.show();
        later(()->{try{bounds();capture("player-general-fixed.png");click("st.player-tab-1");
            later(()->{try{bounds();capture("player-unit-fixed.png");typeField("60","125");call(current(),"refreshWidth");require(field(current(),"seconds").equals("125"),"player parameters lost on resize");click("st.player-tab-2");require(field(current(),"seconds").equals("125"),"player parameters lost on tab switch");
                later(()->{try{bounds();capture("player-access-fixed.png");
                    panel.getClass().getMethod("openPage",String.class).invoke(panel,"units");click("st.roster.tab-map");
                    later(()->{try{bounds();capture("units-actions-fixed.png");scrollRoster(actions);}catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
                }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
            }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
        }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
    }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
    static void scrollRoster(Object actions)throws Exception{
        Element e=((BaseDialog)current()).cont;
        while(e!=null&&!(e instanceof arc.scene.ui.ScrollPane))e=e.parent;
        if(!(e instanceof arc.scene.ui.ScrollPane pane)||pane.getMaxY()<=1){unitSelectionTests(actions);return;}
        pane.setScrollY(0);pane.updateVisualScroll();Core.scene.setScrollFocus(pane);Core.scene.scrolled(0,3);
        later(()->{try{require(pane.getScrollY()>0,"native wheel did not scroll roster");capture("roster-scrolled.png");pane.setScrollY(0);pane.updateVisualScroll();Core.scene.setScrollFocus(null);Log.info("SCHEMETOOLS_ROSTER_SCROLL_PASS: native Arc wheel scrolled real HUD pane");unitSelectionTests(actions);}catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
    }
    static Object roster()throws Exception{return ((java.util.Map<?,?>)field(panel,"views")).get("openUnitTools");}
    static void named(String name){Button button=(Button)Core.scene.root.find(name);require(button!=null,"missing "+name);require(!button.isDisabled(),"disabled "+name);button.fireClick();}
    static boolean disabledPrefix(String key){return all().stream().filter(e->e instanceof TextButton t&&t.getText().toString().startsWith(Core.bundle.get(key))).map(e->(TextButton)e).findFirst().orElseThrow().isDisabled();}
    static void unitSelectionTests(Object actions){try{
        Vars.state.rules.disableUnitCap=true;
        var original=Vars.player.unit();
        var a=UnitTypes.flare.spawn(Team.sharded,80*8,70*8);var b=UnitTypes.flare.spawn(Team.sharded,81*8,70*8);var c=UnitTypes.flare.spawn(Team.green,82*8,70*8);var d=UnitTypes.horizon.spawn(Team.sharded,84*8,70*8);
        require(!a.dead&&!b.dead&&!c.dead&&!d.dead,"fixture targets must begin alive");
        call(panel,"refreshWidth");named("st-roster-team-1");named("st-roster-type-flare");
        require(((java.util.Set<?>)field(roster(),"typeIds")).equals(java.util.Set.of((int)UnitTypes.flare.id)),"single icon filter");
        named("st-roster-kill-matching");click("cancel");require(!a.dead&&!b.dead&&!d.dead,"cancel killed units");
        named("st-roster-kill-matching");a.team(Team.green);Vars.player.unit(b);click("ok");
        require(!a.dead&&!b.dead&&!c.dead&&!d.dead,"server failed changed team or player protection");Vars.player.unit(original);b.resetController();a.team(Team.sharded);call(panel,"refreshWidth");
        named("st-roster-type-flare");require(((java.util.Set<?>)field(roster(),"typeIds")).isEmpty()&&((Button)Core.scene.root.find("st-roster-kill-matching")).isDisabled(),"empty filter implicitly selected all");
        named("st-roster-type-flare");named("st-roster-type-horizon");require(((java.util.Set<?>)field(roster(),"typeIds")).size()==2,"multiple icon filter");
        var saved=java.util.Set.copyOf((java.util.Set<?>)field(roster(),"typeIds"));click("st.roster.tab-create");click("st.roster.tab-map");call(panel,"refreshWidth");require(field(roster(),"typeIds").equals(saved),"tabs/resize lost filters");
        click("st.units-select-area");selection().getClass().getMethod("accept",int.class,int.class,int.class,int.class).invoke(selection(),79,69,83,71);
        require(((java.util.Set<?>)field(roster(),"chosen")).containsAll(java.util.List.of(a,b))&&!((java.util.Set<?>)field(roster(),"chosen")).contains(c),"area ignored team/type scope");
        clickPrefix("st.roster.individuals");named("st-unit-"+a.id);named("st-unit-"+d.id);
        require(((java.util.Set<?>)field(roster(),"chosen")).containsAll(java.util.List.of(b,d)),"individual multi-select failed");
        later(()->{try{bounds();capture("roster-multi.png");clickPrefix("st.units-kill-selected");click("ok");require(b.dead&&d.dead&&!a.dead&&!c.dead&&!original.dead,"selected death escaped scope");
            var fixed=UnitTypes.flare.spawn(Team.sharded,86*8,70*8);call(panel,"refreshWidth");named("st-roster-kill-matching");var late=UnitTypes.flare.spawn(Team.sharded,88*8,70*8);a.team(Team.green);click("ok");
            require(fixed.dead&&!late.dead&&!a.dead&&!c.dead,"snapshot/team exclusion failed");
            clickPrefix("st.roster.all-teams");clickPrefix("st.roster.all-types");
            var acquire=UnitTypes.flare.spawn(Team.sharded,80*8,70*8);call(panel,"refreshWidth");named("st-roster-kill-matching");Vars.player.unit(acquire);var newborn=UnitTypes.flare.spawn(Team.green,86*8,70*8);click("ok");
            require(!acquire.dead&&!newborn.dead&&a.dead&&c.dead&&late.dead,"all teams/types native death failed");Vars.player.unit(original);acquire.resetController();
            backendScopeChecks(actions,newborn);
            call(panel,"refreshWidth");
            later(()->{try{bounds();capture("roster-all.png");
                Vars.logic.reset();Vars.world.loadMap(Vars.maps.defaultMaps().first());Vars.state.rules.canGameOver=false;Vars.state.rules.waves=false;Vars.logic.play();Vars.player.team(Team.sharded);
                panel.getClass().getMethod("openPage",String.class).invoke(panel,"units");click("st.roster.tab-map");
                require(!Boolean.TRUE.equals(field(roster(),"allTeams"))&&Boolean.TRUE.equals(field(roster(),"allTypes"))&&((java.util.Set<?>)field(roster(),"chosen")).isEmpty(),"new world kept old filters or selection");
                Log.info("SCHEMETOOLS_UNIT_DEATH_UI_PASS: team/type icons, multiple types, empty filter, rectangle intersection, individual selection, cancel, server team/player recheck, all-team death, new-spawn exclusion, stale identity/world rejection, filters preserved on tab/resize and reset on new world");
                call(workspace,"close");require(!Core.scene.hasKeyboard(),"close left focus");helpDone();
            }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
        }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
    }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
    static void backendScopeChecks(Object actions,mindustry.gen.Unit live)throws Exception{
        Class<?> scope=Class.forName("org.schemetools.host.UnitScope",true,loader),target=Class.forName("org.schemetools.host.UnitScope$Target",true,loader),request=Class.forName("org.schemetools.host.UnitScope$Request",true,loader);
        Object filter=scope.getMethod("all").invoke(null),wrong=target.getConstructor(int.class,mindustry.gen.Unit.class).newInstance(live.id+1,live);
        Object stale=request.getConstructor(Object.class,scope,java.util.List.class).newInstance(Vars.world.tiles,filter,java.util.List.of(wrong));
        require((int)actions.getClass().getMethod("killUnits",request).invoke(actions,stale)==0&&!live.dead,"stale target accepted");
        Object otherWorld=request.getConstructor(Object.class,scope,java.util.List.class).newInstance(new Object(),filter,java.util.List.of());
        try{actions.getClass().getMethod("killUnits",request).invoke(actions,otherWorld);throw new AssertionError("wrong world accepted");}catch(java.lang.reflect.InvocationTargetException expected){require(expected.getCause() instanceof IllegalArgumentException,"wrong world error");}
        Object invalid=scope.getConstructor(boolean.class,int.class,boolean.class,java.util.Set.class).newInstance(true,0,false,java.util.Set.of(999999));
        Object bad=request.getConstructor(Object.class,scope,java.util.List.class).newInstance(Vars.world.tiles,invalid,java.util.List.of(target.getMethod("capture",mindustry.gen.Unit.class).invoke(null,live)));
        try{actions.getClass().getMethod("killUnits",request).invoke(actions,bad);throw new AssertionError("unknown type accepted");}catch(java.lang.reflect.InvocationTargetException expected){require(expected.getCause() instanceof IllegalArgumentException&&!live.dead,"validation mutated world");}
    }
    static void conceptChecks(){try{
        for(int i=0;i<4;i++){
            var tiles=new arc.struct.Seq<mindustry.game.Schematic.Stile>();
            for(int x=0;x<3;x++)for(int y=0;y<3;y++){
                var block=i==0?Blocks.conveyor:i==1?Blocks.copperWall:i==2?(x==1&&y==1?Blocks.sorter:Blocks.conveyor):Blocks.powerNode;
                if(i==3&&(x==1||y==1))continue;
                tiles.add(new mindustry.game.Schematic.Stile(block,x,y,null,(byte)0));
            }
            Vars.schematics.add(new mindustry.game.Schematic(tiles,arc.struct.StringMap.of("name",new String[]{"Конвейеры","Стена","Сортировщик","Узлы"}[i]),3,3));
        }
        tool("st.schematic.title");
        var first=((java.util.List<?>)field(current(),"library")).get(0);
        named("st-schematic-card-0");require(field(current(),"selected")==first,"schematic card selected wrong object");
        click("st.ui.from-map");require(Boolean.TRUE.equals(field(current(),"captureTab")),"capture tab missing");
        click("st.ui.library");require(field(current(),"selected")==first,"tab discarded selected schematic");
        named("st-dock-labels");require(!Core.settings.getBool("schemesize-dock-labels",true),"dock label setting not saved");
        named("st-dock-labels");require(Core.settings.getBool("schemesize-dock-labels",false)&&field(current(),"selected")==first,"dock layout lost schematic");
        later(()->{try{bounds();capture("concept-schematics.png");
            if(edition.equals("god"))conceptSpawn();else{Log.info("SCHEMETOOLS_CONCEPT_UI_PASS: live schematic cards, library/capture tabs, persistent dock labels, retained selection");helpDone();}
        }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
    }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
    static void conceptSpawn(){try{
        tool("st.units");click("st.roster.tab-create");
        var units=panel.getClass().getDeclaredMethod("availableUnits");units.setAccessible(true);
        set(panel,"unitIndex",((java.util.List<?>)units.invoke(panel)).indexOf(UnitTypes.flare));set(panel,"spawnCount","10");set(panel,"teamIndex",0);call(panel,"refreshWidth");
        later(()->{try{bounds();capture("concept-create.png");int before=Team.sharded.data().countType(UnitTypes.flare);
            click("st.workspace.point");require(current()==panel&&field(selection(),"previewType")==UnitTypes.flare,"point preview lost panel or type");
            int x=(int)(Core.scene.getWidth()*.75f),y=(int)(Core.scene.getHeight()*.5f);Core.scene.mouseMoved(x,y);
            Core.scene.touchDown(x,y,0,KeyCode.mouseLeft);selection().hide();Core.scene.touchUp(x,y,0,KeyCode.mouseLeft);
            require(!Core.scene.hasDialog()&&Team.sharded.data().countType(UnitTypes.flare)==before,"close while pressed created units");
            click("st.workspace.point");Core.scene.mouseMoved(x,y);
            later(()->{try{capture("concept-spawn-preview.png");
                Core.scene.touchDown(x,y,0,KeyCode.mouseRight);Core.scene.touchUp(x,y,0,KeyCode.mouseRight);
                later(()->{try{require(!Core.scene.hasDialog()&&current()==panel&&field(panel,"spawnCount").equals("10")&&Team.sharded.data().countType(UnitTypes.flare)==before,"right-click cancel mutated state");
                click("st.workspace.point");Core.scene.mouseMoved(x,y);Core.scene.touchDown(x,y,0,KeyCode.mouseLeft);Core.scene.touchUp(x,y,0,KeyCode.mouseLeft);
                require(Core.scene.hasDialog(),"map click bypassed confirmation");click("ok");
                require(Team.sharded.data().countType(UnitTypes.flare)==before+10,"map click did not create exact batch");
                click("st.roster.tab-map");named("st-roster-team-1");named("st-roster-type-flare");
                later(()->{try{bounds();capture("concept-units.png");Log.info("SCHEMETOOLS_CONCEPT_UI_PASS: live schematic cards, tabs, dock labels, unit picker, persistent count10, mouse pointer preview, right-click cancellation without mutation, map click confirmation and native spawn10");helpDone();}catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
            }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
            }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
        }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
    }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
    static void menuSettings(){try{
        call(workspace,"close");Vars.state.set(mindustry.core.GameState.State.menu);
        for(var mod:Vars.mods.list()){require(mod.iconTexture!=null&&mod.iconTexture.width==256&&mod.iconTexture.height==256,"packaged icon did not load");}
        Class<?> settings=Class.forName("org.schemetools.client.ToolSettingsDialog",true,loader),keys=Class.forName("org.schemetools.client.ModKeys",true,loader),routes=Class.forName("org.schemetools.client.RouteOverlay",true,loader);
        BaseDialog dialog=(BaseDialog)settings.getConstructor(keys,routes,panel.getClass()).newInstance(field(panel,"keys"),field(panel,"routes"),panel);dialog.show();
        later(()->{try{TableCheck.check(dialog);
            var pane=(arc.scene.ui.ScrollPane)dialog.cont.getChildren().first();float right=pane.localToStageCoordinates(new Vec2(pane.getWidth()-pane.getScrollBarWidth(),0)).x;
            List<Element> controls=new ArrayList<>();collect(pane.getWidget(),controls);for(Element element:controls)if(element instanceof Button){float edge=element.localToStageCoordinates(new Vec2(element.getWidth(),0)).x;require(edge<=right+1,"menu control clipped by vertical scrollbar");}
            capture("fixed-menu-settings.png");dialog.hide();Vars.ui.mods.show();later(()->{capture("installed-mod-icons.png");Vars.ui.mods.hide();Log.info("SCHEMETOOLS_ICON_PASS: native mod list loaded 256px shared icons");helpDone();});Log.info("SCHEMETOOLS_MENU_SETTINGS_PASS: native modal scrolling outside world");}catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
    }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
    static void newPanelChecks(){try{
        tool("st.routes.title");Object route=field(current(),"overlay");route.getClass().getMethod("enabled",boolean.class).invoke(route,false);call(current(),"refreshWidth");
        require(call(route,"status").equals(Core.bundle.get("st.routes.off")),"disabled routes show stale progress");click("st.routes.refresh");require(Boolean.TRUE.equals(call(route,"enabled")),"refresh did not enable lines");
        Core.settings.put("schemesize-module-routes",false);tool("st.workspace.more");
        require(all().stream().noneMatch(e->e instanceof TextButton t&&t.getText().toString().equals(Core.bundle.get("st.routes.title"))),"disabled route module still in More");
        Core.settings.put("schemesize-module-routes",true);tool("st.workspace.more");
        require(all().stream().anyMatch(e->e instanceof TextButton t&&t.getText().toString().equals(Core.bundle.get("st.players"))),"common admin access lost in Plus");
        later(()->{try{bounds();capture("fixed-overview.png");click("st.match-overview");require(Core.scene.hasDialog(),"match metrics missing");click("st.help.close");tool("st.settings.title");
            later(()->{try{bounds();capture("fixed-settings.png");click("st.settings.tab.keys");
                later(()->{try{bounds();capture("fixed-keys.png");click("st.settings.tab.other");
                    later(()->{try{bounds();capture("fixed-other.png");tool("st.arena.title");
                        later(()->{try{bounds();capture("fixed-arena.png");
                            ((TextButton)all().stream().filter(e->e instanceof TextButton t&&t.getText().toString().startsWith(Core.bundle.get("st.workspace.advanced"))).findFirst().orElseThrow()).fireClick();click("st.arena.preview");require(field(current(),"preview")!=null,"arena calculation discarded");call(current(),"refreshWidth");require(field(current(),"preview")!=null,"resize discarded arena preview");
                            if(edition.equals("god"))replacementChecks();else {Log.info("SCHEMETOOLS_MORE_PANELS_PASS");helpDone();}
                        }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
                    }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
                }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
            }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
        }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
    }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
    static void replacementChecks(){try{
        Object actions=field(panel,"actions");Class<?> type=actions.getClass();
        Object stamp=Class.forName("org.schemetools.host.BuildingStamp",true,loader).getConstructor(type).newInstance(actions);
        var previewMethod=stamp.getClass().getMethod("preview",int.class,int.class,int.class,int.class,mindustry.world.Block.class,Team.class,int.class);
        for(int x=66;x<=75;x++)for(int y=80;y<=88;y++){Vars.world.tile(x,y).setBlock(Blocks.air);Vars.world.tile(x,y).setFloor(Blocks.stone.asFloor());}
        Vars.world.tile(70,84).setBlock(Blocks.vault,Team.green,1);var vault=Vars.world.build(70,84);vault.items.set(mindustry.content.Items.lead,789);vault.health=vault.maxHealth-123;
        Vars.world.tile(73,84).setBlock(Blocks.sorter,Team.green,2);Vars.world.build(73,84).configureAny(mindustry.content.Items.graphite);
        Object preview=previewMethod.invoke(stamp,69,83,5,3,Blocks.scrapWall,Team.sharded,0);
        require((int)preview.getClass().getMethod("replacements").invoke(preview)>0,"replacement not reported");
        stamp.getClass().getMethod("apply",preview.getClass()).invoke(stamp,preview);require(Vars.world.tile(70,84).block()==Blocks.scrapWall,"occupied stamp denied");
        later(()->{try{
            stamp.getClass().getMethod("undo").invoke(stamp);
            require(Vars.world.build(70,84).block==Blocks.vault&&Vars.world.build(70,84).items.get(mindustry.content.Items.lead)==789,"undo lost old inventory");
            require(Vars.world.build(70,84).health==vault.maxHealth-123,"undo lost old health");
            require(Vars.world.build(73,84).config()==mindustry.content.Items.graphite&&Vars.world.build(73,84).rotation==2&&Vars.world.build(73,84).team==Team.green,"undo lost config/team/rotation");
            stamp.getClass().getMethod("clear").invoke(stamp);
            Object giant=previewMethod.invoke(stamp,70,84,1,1,Blocks.scrapWallGigantic,Team.sharded,0);
            stamp.getClass().getMethod("apply",giant.getClass()).invoke(stamp,giant);
            require(Vars.world.build(70,84).block==Blocks.scrapWallGigantic,"large block on point selection denied");
            later(()->{try{stamp.getClass().getMethod("undo").invoke(stamp);arenaReplacementCheck();}catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
        }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
    }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
    static void arenaReplacementCheck(){try{
            Object arena=field(panel,"arenaTools");Class<?> area=Class.forName("org.schemetools.host.ArenaTools$Area",true,loader),shape=Class.forName("org.schemetools.host.ArenaTools$Shape",true,loader),layer=Class.forName("org.schemetools.host.ArenaTools$Layer",true,loader);
            Object rect=java.util.Arrays.stream(shape.getEnumConstants()).filter(v->v.toString().equals("RECTANGLE")).findFirst().orElseThrow();
            Object blocks=java.util.Arrays.stream(layer.getEnumConstants()).filter(v->v.toString().equals("BLOCK")).findFirst().orElseThrow();
            Object box=area.getConstructor(int.class,int.class,int.class,int.class,shape).newInstance(70,84,70,84,rect);
            Object edit=arena.getClass().getMethod("preview",area,layer,mindustry.world.Block.class,mindustry.world.Block.class,mindustry.world.Block.class).invoke(arena,box,blocks,Blocks.stone,Blocks.air,Blocks.air);
            arena.getClass().getMethod("apply",edit.getClass()).invoke(arena,edit);require(Vars.world.build(70,84)==null,"arena still protects occupied building");
            later(()->{try{call(arena,"undo");require(Vars.world.build(70,84).items.get(mindustry.content.Items.lead)==789,"arena undo lost full multiblock data");
                Log.info("SCHEMETOOLS_GOD_REPLACE_PASS: occupied multiblock replacement and terrain erase; undo restored vault/inventory/health, sorter config/team/rotation");helpDone();
            }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
    }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
    static void presetControls(){try{
        tool("st.items");click("st.loadout.title");later(()->{try{
            bounds();click("st.loadout.add");later(()->{try{
                ((Button)Core.scene.root.find("st-content-graphite")).fireClick();typeField("0","123");click("st.apply");
                later(()->{try{click("ok");later(()->{try{
                    require(Team.sharded.core().items.get(mindustry.content.Items.graphite)==123,"unnamed resource set did not apply");bounds();capture("compact-loadout.png");
                    tool("st.units");later(()->{try{bounds();capture("compact-units.png");helpDone();}catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
                }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});}catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
            }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
        }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
    }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
    static void godControls(){try{
        Object actions=field(panel,"actions");Class<?> type=actions.getClass();
        require((boolean)type.getMethod("godMode").invoke(actions),"God bootstrap used strict policy");
        Vars.state.rules.unitCap=0;Vars.state.rules.disableUnitCap=false;Vars.state.rules.bannedUnits.add(UnitTypes.dagger);
        Object strict=type.getConstructor().newInstance();
        try{type.getMethod("spawn",Team.class,mindustry.type.UnitType.class,float.class,float.class,int.class).invoke(strict,Team.green,UnitTypes.dagger,560f,560f,15);throw new AssertionError("strict spawn ignored cap");}catch(java.lang.reflect.InvocationTargetException expected){require(expected.getCause() instanceof IllegalStateException,"wrong strict error");}
        type.getMethod("spawn",Team.class,mindustry.type.UnitType.class,float.class,float.class,int.class).invoke(actions,Team.green,UnitTypes.dagger,560f,560f,15);
        var core=Team.sharded.core();int before=core.items.get(mindustry.content.Items.copper);
        type.getMethod("adjustItem",Team.class,mindustry.world.blocks.storage.CoreBlock.CoreBuild.class,mindustry.type.Item.class,int.class).invoke(actions,Team.sharded,core,mindustry.content.Items.copper,100000);
        core.onProximityUpdate();require(core.items.get(mindustry.content.Items.copper)==before+100000,"proximity clamped God inventory");
        try{type.getMethod("adjustItem",Team.class,mindustry.world.blocks.storage.CoreBlock.CoreBuild.class,mindustry.type.Item.class,int.class).invoke(actions,Team.sharded,core,mindustry.content.Items.copper,Integer.MAX_VALUE);throw new AssertionError("overflow accepted");}catch(java.lang.reflect.InvocationTargetException expected){require(core.items.get(mindustry.content.Items.copper)==before+100000,"overflow mutated inventory");}
        var segmented=Vars.content.units().find(t->t.segmentUnits>1);if(segmented!=null){
        type.getMethod("spawn",Team.class,mindustry.type.UnitType.class,float.class,float.class,int.class).invoke(actions,Team.green,segmented,560f,560f,1);
        require(mindustry.gen.Groups.unit.count(u->u.team==Team.green&&u instanceof mindustry.gen.Segmentc)>=segmented.segmentUnits,"native segmented chain missing");}else Log.info("SCHEMETOOLS_SEGMENTS_NOT_AVAILABLE: installed 160.7 content has no segmentUnits>1; native API branch remains unverified");
        // Create a banned buildable block instantly on liquid without using the player's builder or items.
        Vars.world.tile(90,80).setBlock(Blocks.air);Vars.world.tile(90,80).setFloor(Blocks.deepwater.asFloor());Vars.state.rules.bannedBlocks.add(Blocks.router);
        Object stamp=Class.forName("org.schemetools.host.BuildingStamp",true,loader).getConstructor(type).newInstance(actions);
        Object preview=stamp.getClass().getMethod("preview",int.class,int.class,int.class,int.class,mindustry.world.Block.class,Team.class,int.class).invoke(stamp,90,80,1,1,Blocks.router,Team.sharded,0);
        stamp.getClass().getMethod("apply",preview.getClass()).invoke(stamp,preview);require(Vars.world.tile(90,80).block()==Blocks.router,"God instant build denied");
        later(()->{try{
            require(mindustry.gen.Groups.unit.count(u->u.team==Team.green&&u.type==UnitTypes.dagger&&!u.dead)>=15,"God units died after update");
            require(Vars.state.rules.disableUnitCap,"God cap rule not applied");stamp.getClass().getMethod("undo").invoke(stamp);require(Vars.world.tile(90,80).block()==Blocks.air,"instant undo failed");
            var save=new Fi("god-check.msav");mindustry.io.SaveIO.save(save);Vars.logic.reset();mindustry.io.SaveIO.load(save);Vars.state.set(mindustry.core.GameState.State.playing);
            require(Team.sharded.core().items.get(mindustry.content.Items.copper)==before+100000,"God inventory lost on save/load actual="+Team.sharded.core().items.get(mindustry.content.Items.copper)+" expected="+(before+100000)+" cores="+Team.sharded.cores().size);Team.sharded.core().onProximityUpdate();
            require(Team.sharded.core().items.get(mindustry.content.Items.copper)==before+100000,"loaded inventory clamped on proximity");
            stamp.getClass().getMethod("clear").invoke(stamp);
            Vars.world.tile(91,80).setBlock(Blocks.air);Vars.world.tile(91,80).setFloor(Blocks.stone.asFloor());
            Object configured=stamp.getClass().getMethod("preview",int.class,int.class,int.class,int.class,mindustry.world.Block.class,Team.class,int.class).invoke(stamp,91,80,1,1,Blocks.sorter,Team.sharded,0);
            configured=configured.getClass().getMethod("configured",Object.class).invoke(configured,mindustry.content.Items.lead);
            stamp.getClass().getMethod("apply",configured.getClass()).invoke(stamp,configured);
            require(Vars.world.build(91,80).config()==mindustry.content.Items.lead,"instant placement lost copied config");
            Log.info("SCHEMETOOLS_GOD_CONTROLS_PASS: configured sorter, save/load and proximity preserved, banned dagger15 at cap0 survive, copper+100000 survives proximity, overflow atomic, banned router on deepwater and undo, graphite icon selection");helpDone();
        }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}});
    }catch(Throwable t){Log.err("SCHEMETOOLS_WORKSPACE_FAIL",t);}}
    private static final class FakeConnection extends mindustry.net.NetConnection{
        FakeConnection(){super("test-only");uuid="workspace-guest";}
        @Override public void send(Object value,boolean reliable){}
        @Override public void close(){}
    }
}
final class TableCheck{
    static void check(Element e){
        Vec2 p=e.localToStageCoordinates(new Vec2());
        if(p.x<0||p.y<0||p.x+e.getWidth()>Core.scene.getWidth()+1||p.y+e.getHeight()>Core.scene.getHeight()+1)throw new AssertionError("context outside viewport "+p+" "+e.getWidth()+"x"+e.getHeight());
    }
}
