package org.schemetools.client;

import arc.Core;
import arc.input.InputProcessor;
import arc.input.KeyCode;
import arc.math.geom.Vec2;
import arc.scene.Group;
import arc.scene.Element;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.game.Schematic;
import mindustry.gen.Tex;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import org.schemetools.schematic.SchematicStore;
import org.schemetools.schematic.SchematicTools;
import java.util.*;

/** Independent six-group picker. No upstream code, geometry assets or sprites are included. */
final class HeldQuickMenu {
    private final SchematicStore store;
    private final Group root=new Group(){};
    private final Table cards=new Table(Tex.paneSolid), grid=new Table();
    private final ScrollPane pane=new ScrollPane(grid);
    private final List<Schematic> library=new ArrayList<>();
    private ModKeys.Entry entry;
    private boolean shown,latched,mouseHeld,placing;
    private KeyCode heldMouse;
    private int group=-1;
    private float anchorX,anchorY,scale;
    private boolean left;
    HeldQuickMenu(SchematicStore store){
        this.store=store;root.name="st-quick-menu";root.setFillParent(true);root.touchable=Touchable.childrenOnly;root.visible=false;
        Vars.ui.hudGroup.addChild(root);
        Core.input.getInputMultiplexer().addProcessor(0,new InputProcessor(){
            @Override public boolean touchDown(int x,int y,int pointer,KeyCode button){
                if(entry==null||entry.binding().value==null||entry.binding().value.key!=button||!allowed()||!ModKeys.chord(entry).matches()||Core.scene.hasMouse())return false;
                mouseHeld=true;heldMouse=button;latched=true;open();return true;
            }
            @Override public boolean touchUp(int x,int y,int pointer,KeyCode button){if(button!=heldMouse)return false;mouseHeld=false;heldMouse=null;latched=false;close();return true;}
            @Override public boolean scrolled(float x,float y){
                if(!shown)return false;var p=pointer();Element hit=Core.scene.hit(p.x,p.y,true);
                Element focus=Core.scene.getScrollFocus();if(focus!=null&&focus.isDescendantOf(root)&&(hit==null||!hit.isDescendantOf(root)))Core.scene.setScrollFocus(null);
                return false;
            }
        });
        arc.Events.on(mindustry.game.EventType.WorldLoadEvent.class,e->reset());arc.Events.on(mindustry.game.EventType.ResetEvent.class,e->reset());
    }
    private boolean allowed(){return Vars.state.isGame()&&!Core.scene.hasKeyboard()&&!Core.scene.hasDialog()&&!Vars.ui.chatfrag.shown()&&!Vars.ui.consolefrag.shown()&&Core.settings.getBool("schemesize-module-schematics",true);}
    void update(ModKeys.Entry entry){
        this.entry=entry;
        boolean down=mouseHeld||(!entry.binding().isUnset()&&Core.input.keyDown(entry.binding())&&ModKeys.chord(entry).matches());
        if(!down){latched=false;close();return;}
        if(!allowed()){close();return;}
        if(!latched&&Core.input.keyTap(entry.binding())&&!Core.scene.hasMouse()){latched=true;open();}
        if(shown)updateDirection();
    }
    /** Directional selection also works in the gaps; entering the preview keeps its group. */
    private void updateDirection(){
        Vec2 p=pointer();
        if(p.x>=cards.x&&p.x<=cards.x+cards.getWidth()&&p.y>=cards.y&&p.y<=cards.y+cards.getHeight())return;
        float dx=p.x-anchorX,dy=p.y-anchorY;
        if(dx*dx+dy*dy<28*28*scale*scale)return;
        double clockwise=90-Math.toDegrees(Math.atan2(dy,dx));
        int slot=Math.floorMod((int)Math.floor((clockwise+30)/60),6);
        showGroup(slot);
    }
    private Vec2 pointer(){return Core.scene.screenToStageCoordinates(new Vec2(Core.input.mouseX(),Core.input.mouseY()));}
    boolean visible(){return shown;}
    private void reset(){latched=false;mouseHeld=false;heldMouse=null;close();}
    void close(){
        if(!shown)return;
        shown=false;root.visible=false;Core.scene.unfocus(root);
        if(ToolWorkspace.active!=null)ToolWorkspace.active.suspend(false);
    }
    private void open(){
        library.clear();library.addAll(library(store));placing=false;shown=true;root.visible=true;root.clearChildren();group=-1;
        if(ToolWorkspace.active!=null)ToolWorkspace.active.suspend(true);
        scale=Math.min(UiSizing.scale()*sizePercent()/100f,Core.scene.getWidth()/512f);float radius=76*scale,gw=84*scale,gh=34*scale,pw=240*scale;
        var p=pointer();left=p.x>Core.scene.getWidth()/2;
        float half=radius+gw/2+8*scale;
        anchorX=arc.math.Mathf.clamp(p.x,half,Core.scene.getWidth()-half);float spaceLeft=anchorX-half-12*scale-8,spaceRight=Core.scene.getWidth()-anchorX-half-12*scale-8;left=spaceLeft>=spaceRight;pw=Math.min(pw,Math.max(116*scale,left?spaceLeft:spaceRight));float reserve=Math.min(96*UiSizing.scale(),Core.scene.getHeight()*.3f);float vertical=radius+gh/2+8*scale;anchorY=arc.math.Mathf.clamp(p.y,vertical,Math.max(vertical,Core.scene.getHeight()-reserve-vertical));
        Table center=new Table(Tex.paneSolid);center.add("S").color(mindustry.graphics.Pal.accent);center.setBounds(anchorX-18*scale,anchorY-18*scale,36*scale,36*scale);root.addChild(center);
        for(int i=0;i<6;i++){
            final int slot=i;String label=tag(i);if(label.isBlank())label=(i+1)+" +";
            TextButton button=new TextButton(label,ToolUi.toggleStyle());button.name="st-quick-group-"+i;button.getLabel().setEllipsis(true);button.getLabel().setFontScale(.8f);
            double angle=Math.toRadians(90-i*60);button.setBounds(anchorX+(float)Math.cos(angle)*radius-gw/2,anchorY+(float)Math.sin(angle)*radius-gh/2,gw,gh);
            button.addListener(new InputListener(){@Override public void enter(InputEvent event,float x,float y,int pointer,Element from){if(pointer==-1)showGroup(slot);}});
            button.clicked(()->{close();configure(slot,store);});button.addListener(new Tooltip(t->t.add(Core.bundle.get("st.quick.change-tag"))));root.addChild(button);
        }
        cards.clearChildren();cards.margin(8);cards.top();cards.name="st-quick-cards";cards.setSize(pw,Math.min(280*scale,Core.scene.getHeight()-32*scale));
        cards.setPosition(arc.math.Mathf.clamp(left?anchorX-half-12*scale-pw:anchorX+half+12*scale,4,Core.scene.getWidth()-pw-4),arc.math.Mathf.clamp(anchorY-cards.getHeight()/2,8,Core.scene.getHeight()-cards.getHeight()-8));
        pane.setScrollingDisabled(true,false);cards.add(pane).grow();root.addChild(cards);showGroup(0);root.toFront();
    }
    void showGroup(int slot){
        if(!shown||group==slot)return;group=slot;grid.clearChildren();String filter=tag(slot);
        for(int i=0;i<6;i++){Button b=root.find("st-quick-group-"+i);b.setChecked(slot==i);}
        float card=104*sizePercent()/100f;int columns=cards.getWidth()>=(card+12)*UiSizing.scale()*2?2:1;int count=0;
        if(!filter.isBlank())for(Schematic schematic:library)if(QuickSchematicsDialog.matches(schematic,filter,"")&&!SchematicTools.terrain(schematic)){
            var b=grid.button(t->{t.add(new SchematicsDialog.Preview(schematic)).size(card,60*sizePercent()/100f).row();var label=t.add(schematic.name()).width(card).get();label.setFontScale(.75f);label.setEllipsis(true);},ToolUi.toggleStyle(),()->{
                try{if(placing)return;placing=true;SchematicTools.use(schematic);close();}catch(Exception ex){Vars.ui.showInfoFade(Core.bundle.get(ex.getMessage(),Core.bundle.get("st.schematic.error.data")));}
            }).width(card+8).minHeight(86*sizePercent()/100f).pad(2).get();b.name="st-quick-card-"+count;if(++count%columns==0)grid.row();
        }
        if(count==0){grid.add(Core.bundle.get(filter.isBlank()?"st.quick.empty-group":"st.schematic.no-matches")).wrap().width(cards.getWidth()/UiSizing.scale()-24).row();grid.button(Core.bundle.get("st.quick.assign-tag"),()->{close();configure(slot,store);}).width(cards.getWidth()/UiSizing.scale()-24).minHeight(42).get().name="st-quick-assign";grid.row();}
        float reserve=Math.min(96*UiSizing.scale(),Core.scene.getHeight()*.3f);float height=Math.min(Math.max(100,((count+columns-1)/columns)*90+16)*scale,Math.min(280*scale,Core.scene.getHeight()-reserve-16*scale));cards.setHeight(height);cards.setPosition(cards.x,arc.math.Mathf.clamp(anchorY-height/2,8,Core.scene.getHeight()-reserve-height-8));grid.invalidateHierarchy();pane.setScrollY(0);cards.invalidateHierarchy();
    }
    static int sizePercent(){return Math.max(70,Math.min(130,Core.settings.getInt("schemesize-quick-size",100)));}
    static String tag(int slot){return Core.settings.getString("schemesize-quick-group-"+slot,"");}
    static List<Schematic> library(SchematicStore store){
        List<Schematic> result=new ArrayList<>();for(var schematic:Vars.schematics.all())result.add(schematic);
        try{for(var stored:store.list().entries())result.add(stored.schematic());}catch(java.io.IOException ex){Vars.ui.showInfoFade(Core.bundle.get("st.schematic.error.file"));}
        return result;
    }
    static void configure(int slot,SchematicStore store){
        BaseDialog dialog=new BaseDialog(Core.bundle.format("st.quick.group",slot+1));dialog.name="st-modal";dialog.addCloseButton();String[] value={tag(slot)};dialog.hidden(()->{if(ToolWorkspace.active!=null&&ToolWorkspace.active.current() instanceof QuickSchematicsDialog q)q.refreshWidth();});
        float width=Math.min(320,Core.scene.getWidth()/UiSizing.scale()-90);
        dialog.cont.field(value[0],s->value[0]=s).width(width).maxTextLength(64).row();
        Set<String> tags=new TreeSet<>();for(var s:library(store))for(String label:s.labels)tags.add(label);
        dialog.cont.pane(t->{for(String label:tags)t.button(label,()->{Core.settings.put("schemesize-quick-group-"+slot,label);dialog.hide();}).width(width-24).height(36).row();}).width(width).maxHeight(UiSizing.height()*.35f).scrollX(false).row();
        dialog.buttons.button(Core.bundle.get("save"),()->{Core.settings.put("schemesize-quick-group-"+slot,value[0].strip());dialog.hide();}).height(42);
        dialog.show();
    }
}
