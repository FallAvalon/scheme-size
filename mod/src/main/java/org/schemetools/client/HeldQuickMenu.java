package org.schemetools.client;

import arc.Core;
import arc.input.InputProcessor;
import arc.input.KeyCode;
import arc.math.geom.Vec2;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.scene.Group;
import arc.scene.Element;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.game.Schematic;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.SchematicsDialog.SchematicImage;
import arc.util.Scaling;
import mindustry.ui.dialogs.BaseDialog;
import org.schemetools.schematic.SchematicStore;
import org.schemetools.schematic.SchematicTools;
import java.util.*;

/** Independent six-group picker. No upstream code, geometry assets or sprites are included. */
final class HeldQuickMenu {
    private final SchematicStore store;
    private final Group root=new Group(){@Override public void draw(){drawRing();super.draw();}};
    private final Table cards=new Table(Styles.black6), grid=new Table();
    private final ScrollPane pane=new ScrollPane(grid);
    private final List<Schematic> library=new ArrayList<>();
    private ModKeys.Entry entry;
    private boolean shown,latched,mouseHeld,placing,configurationPending;
    private long worldGeneration;
    private KeyCode heldMouse;
    private KeyCode dismissedMouse;
    private BaseDialog inputGate;
    private int group=-1;
    private float anchorX,anchorY,scale;
    private static final Color ringColor=new Color(0,0,0,.65f);
    private final TextButton[] groups=new TextButton[6];
    private final float[] labelX=new float[6],labelY=new float[6];
    HeldQuickMenu(SchematicStore store){
        this.store=store;root.name="st-quick-menu";root.setFillParent(true);root.touchable=Touchable.childrenOnly;root.visible=false;
        Vars.ui.hudGroup.addChild(root);
        Core.input.getInputMultiplexer().addProcessor(0,new InputProcessor(){
            @Override public boolean touchDown(int x,int y,int pointer,KeyCode button){
                if(entry!=null&&entry.binding().value!=null&&entry.binding().value.key==button&&allowed()&&ModKeys.chord(entry).matches()){
                    if(shown){close();}else if(!Core.scene.hasMouse()){open();}else return false;
                    mouseHeld=true;heldMouse=button;latched=true;return true;
                }
                if(shown){
                    Vec2 p=pointer();Element hit=Core.scene.hit(p.x,p.y,true);
                    if(hit==null||!hit.isDescendantOf(root)){dismissedMouse=button;close();guardWorldClick(button);return true;}
                }
                return false;
            }
            @Override public boolean touchUp(int x,int y,int pointer,KeyCode button){
                if(button==heldMouse){mouseHeld=false;heldMouse=null;latched=false;return true;}
                if(button==dismissedMouse){dismissedMouse=null;return true;}
                return false;
            }
            @Override public boolean keyDown(KeyCode key){if(shown&&key==KeyCode.escape){InputDismissal.run(HeldQuickMenu.this::close);return true;}return false;}
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
        if(!allowed()){close();return;}
        var axis=entry.binding().value;
        boolean mouse=axis!=null&&axis.key!=null&&axis.key.type==KeyCode.KeyType.mouse;
        if(!mouse){
            boolean down=!entry.binding().isUnset()&&Core.input.keyDown(entry.binding())&&ModKeys.chord(entry).matches();
            if(!down)latched=false;
            else if(!latched&&Core.input.keyTap(entry.binding())&&(shown||!Core.scene.hasMouse())){latched=true;if(shown)close();else open();}
        }
        if(shown)updateDirection();
    }
    /** Directional selection also works in the gaps; entering the preview keeps its group. */
    private void updateDirection(){
        Vec2 p=pointer();
        float padding=8*scale;
        if(cards.visible&&p.x>=cards.x-padding&&p.x<=cards.x+cards.getWidth()+padding&&p.y>=cards.y-padding&&p.y<=cards.y+cards.getHeight()+padding)return;
        float dx=p.x-anchorX,dy=p.y-anchorY;
        if(dx*dx+dy*dy<24*24*scale*scale){showGroup(-1);return;}
        int slot=Math.floorMod((int)Math.floor((Math.toDegrees(Math.atan2(dy,dx))+30)/60),6);
        showGroup(slot);
    }
    /** Twelve independent quadrilaterals form six vertex-centred sectors of a hollow hexagon. */
    private void drawRing(){
        Draw.color(ringColor);
        for(int vertex=0;vertex<6;vertex++)drawSector(vertex,46,74,anchorX,anchorY,.5f);
        if(group>=0){
            Draw.color(mindustry.graphics.Pal.accent);
            double a=Math.toRadians(group*60);
            drawSector(group,54,66,anchorX+(float)Math.cos(a)*12*scale,anchorY+(float)Math.sin(a)*12*scale,1);
        }
        Draw.reset();
    }
    private void drawSector(int vertex,float inner,float outer,float cx,float cy,float portion){
        for(int side=-1;side<=1;side+=2){
            double a=Math.toRadians(vertex*60),b=Math.toRadians((vertex+side)*60);
            float x=(float)Math.cos(a),y=(float)Math.sin(a),mx=x+((float)Math.cos(b)-x)*portion,my=y+((float)Math.sin(b)-y)*portion;
            Fill.quad(cx+x*inner*scale,cy+y*inner*scale,cx+x*outer*scale,cy+y*outer*scale,
                cx+mx*outer*scale,cy+my*outer*scale,cx+mx*inner*scale,cy+my*inner*scale);
        }
    }
    private Vec2 pointer(){return Core.scene.screenToStageCoordinates(new Vec2(Core.input.mouseX(),Core.input.mouseY()));}
    boolean visible(){return shown;}
    private void reset(){worldGeneration++;latched=false;mouseHeld=false;heldMouse=null;dismissedMouse=null;close();if(inputGate!=null){inputGate.hide(null);inputGate=null;}}
    /** DesktopInput also polls clicks after the scene consumes them. */
    private void guardWorldClick(KeyCode button){
        if(inputGate!=null)inputGate.hide(null);
        BaseDialog gate=new BaseDialog("");inputGate=gate;
        Dialog.DialogStyle style=new Dialog.DialogStyle();style.titleFont=gate.getStyle().titleFont;style.titleFontColor=gate.getStyle().titleFontColor;
        gate.setStyle(style);gate.clearChildren();gate.touchable=Touchable.disabled;gate.show(Core.scene,null);gate.setSize(0,0);int[] frames={0};
        gate.update(()->{if(!Vars.state.isGame()||(++frames[0]>=2&&!Core.input.keyDown(button)&&!Core.input.keyTap(button))){gate.hide(null);if(inputGate==gate)inputGate=null;}});
    }
    /** Open outside the current mouse event so releasing either button cannot cancel the modal. */
    private void requestConfiguration(int slot){
        if(configurationPending)return;
        configurationPending=true;long generation=worldGeneration;close();
        Core.app.post(()->{
            configurationPending=false;
            if(generation==worldGeneration&&Vars.state.isGame())configure(slot,store);
        });
    }
    private void configureOnPress(Button button,int slot){
        button.addCaptureListener(new InputListener(){
            @Override public boolean touchDown(InputEvent event,float x,float y,int pointer,KeyCode key){
                if(key!=KeyCode.mouseLeft)return false;
                event.stop();requestConfiguration(slot);return true;
            }
        });
        button.clicked(()->requestConfiguration(slot));
    }
    void close(){
        if(!shown)return;
        shown=false;root.visible=false;Core.scene.unfocus(root);
        if(ToolWorkspace.active!=null)ToolWorkspace.active.suspend(false);
    }
    private void open(){
        library.clear();library.addAll(library(store));placing=false;shown=true;root.visible=true;root.clearChildren();group=-1;
        if(ToolWorkspace.active!=null)ToolWorkspace.active.suspend(true);
        scale=Math.min(UiSizing.scale()*sizePercent()/100f,Math.min(Core.scene.getWidth()/400f,Core.scene.getHeight()/240f));
        var p=pointer();float margin=86*scale;
        anchorX=arc.math.Mathf.clamp(p.x,margin,Core.scene.getWidth()-margin);anchorY=arc.math.Mathf.clamp(p.y,margin,Core.scene.getHeight()-margin);
        Element shield=new Element();shield.touchable=Touchable.enabled;shield.setBounds(anchorX-78*scale,anchorY-78*scale,156*scale,156*scale);root.addChild(shield);
        for(int i=0;i<6;i++){
            final int slot=i;String label=tag(i);if(label.isBlank())label=String.valueOf(mindustry.gen.Iconc.none);
            var labelStyle=new TextButton.TextButtonStyle(Styles.cleart);labelStyle.up=null;labelStyle.down=null;labelStyle.over=null;
            TextButton button=new TextButton(label,labelStyle);groups[i]=button;button.name="st-quick-group-"+i;button.getLabel().setEllipsis(true);
            double angle=Math.toRadians(i*60);float dx=(float)Math.cos(angle),dy=(float)Math.sin(angle);
            float width=label.length()==1?28:Math.min(128,Math.max(48,label.length()*9));width*=scale;
            float x=anchorX+dx*60*scale-width/2;
            if(label.length()>1)x=dx>0?anchorX+dx*70*scale:anchorX+dx*70*scale-width;
            button.setBounds(arc.math.Mathf.clamp(x,4,Core.scene.getWidth()-width-4),anchorY+dy*60*scale-14*scale,width,28*scale);
            labelX[i]=button.x;labelY[i]=button.y;
            button.addListener(new InputListener(){@Override public void enter(InputEvent event,float x,float y,int pointer,Element from){if(pointer==-1)showGroup(slot);}});
            configureOnPress(button,slot);root.addChild(button);
        }
        cards.clearChildren();cards.margin(4);cards.top();cards.name="st-quick-cards";cards.visible=false;
        pane.setScrollingDisabled(true,false);cards.add(pane).grow();root.addChild(cards);root.toFront();
    }
    void showGroup(int slot){
        if(!shown||group==slot)return;group=slot;grid.clearChildren();cards.visible=slot>=0;
        for(int i=0;i<6;i++){double a=Math.toRadians(i*60);float offset=i==slot?8*scale:0;groups[i].setPosition(arc.math.Mathf.clamp(labelX[i]+(float)Math.cos(a)*offset,4,Core.scene.getWidth()-groups[i].getWidth()-4),labelY[i]+(float)Math.sin(a)*offset);}
        if(slot<0)return;
        String filter=tag(slot);
        double angle=Math.toRadians(slot*60);float dx=(float)Math.cos(angle),dy=(float)Math.sin(angle);
        boolean left=dx<0;
        float rightEdge=anchorX+90*scale,leftEdge=anchorX-90*scale;
        for(TextButton button:groups){if(button.x>anchorX)rightEdge=Math.max(rightEdge,button.x+button.getWidth()+8*scale);if(button.x+button.getWidth()<anchorX)leftEdge=Math.min(leftEdge,button.x-8*scale);}
        float available=left?leftEdge:Core.scene.getWidth()-rightEdge;
        if(available<92*scale){left=!left;available=left?leftEdge:Core.scene.getWidth()-rightEdge;}
        int columns=Math.max(1,Math.min(4,(int)((available-26*scale)/(84*scale))));
        float pw=(columns*84+26)*scale;cards.setWidth(pw);
        float card=80*scale/UiSizing.scale();int count=0;
        if(!filter.isBlank())for(Schematic schematic:library)if(QuickSchematicsDialog.matches(schematic,filter,"")&&!SchematicTools.terrain(schematic)){
            var b=grid.button(t->{t.margin(0);t.stack(new SchematicImage(schematic).setScaling(Scaling.fit),new Table(title->{title.top();title.table(Styles.black3,name->{var label=name.add(schematic.name()).width(card-8).get();label.setFontScale(.6f);label.setEllipsis(true);}).growX();})).grow();},Styles.cleart,()->{
                try{if(placing)return;placing=true;SchematicTools.use(schematic);close();guardWorldClick(KeyCode.mouseLeft);}catch(Exception ex){Vars.ui.showInfoFade(Core.bundle.get(ex.getMessage(),Core.bundle.get("st.schematic.error.data")));}
            }).size(card).pad(2*scale/UiSizing.scale()).get();b.name="st-quick-card-"+count;if(++count%columns==0)grid.row();
        }
        if(count==0){var assign=grid.button(Core.bundle.get("st.quick.assign-tag"),Styles.cleart,()->{}).width(pw/UiSizing.scale()-8).minHeight(40).get();assign.name="st-quick-assign";configureOnPress(assign,slot);grid.row();}
        int maxRows=Math.max(1,Math.min(4,(int)((Core.scene.getHeight()*.65f/scale-8)/84)));
        float height=(count==0?48:Math.min(maxRows,(count+columns-1)/columns)*84+8)*scale;
        cards.setHeight(height);
        float x=left?leftEdge-pw:rightEdge;
        float y=dy>.1f?anchorY+40*scale:dy<-.1f?anchorY-40*scale-height:anchorY-height/2;
        cards.setPosition(arc.math.Mathf.clamp(x,8,Core.scene.getWidth()-pw-8),arc.math.Mathf.clamp(y,8,Core.scene.getHeight()-height-8));
        grid.invalidateHierarchy();cards.invalidateHierarchy();cards.validate();pane.validate();pane.setScrollY(0);pane.updateVisualScroll();
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
        dialog.cont.button(String.valueOf(mindustry.gen.Iconc.none),()->{Core.settings.put("schemesize-quick-group-"+slot,"");dialog.hide();}).width(width).height(36).row();
        Set<String> tags=new TreeSet<>();for(var s:library(store))for(String label:s.labels)tags.add(label);
        dialog.cont.pane(t->{for(String label:tags)t.button(label,()->{Core.settings.put("schemesize-quick-group-"+slot,label);dialog.hide();}).width(width-24).height(36).row();}).width(width).maxHeight(UiSizing.height()*.35f).scrollX(false).row();
        dialog.buttons.button(Core.bundle.get("save"),()->{Core.settings.put("schemesize-quick-group-"+slot,value[0].strip());dialog.hide();}).height(42);
        dialog.show();
    }
}
