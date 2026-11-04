package org.schemetools.client;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import mindustry.Vars;
import mindustry.ui.dialogs.BaseDialog;

import java.util.function.Consumer;

/** Modal pointer selection does not replace or mutate the game's input handler. */
public final class AreaSelectionDialog extends BaseDialog {
    public record Selection(int x1, int y1, int x2, int y2) { }
    private final Consumer<Selection> selected;
    private final int maximum;
    private final Object world=Vars.world.tiles;
    private int startX, startY;
    private float sx, sy, ex, ey;
    private boolean dragging;
    private boolean ready;
    private int endX, endY;
    private final mindustry.type.UnitType previewType;
    private final mindustry.game.Team previewTeam;
    private final int previewCount;
    private boolean pointerSet;
    private boolean cancelling;
    public AreaSelectionDialog(int maximum, Consumer<Selection> selected) {
        this(maximum,selected,null,null,0);
    }
    public AreaSelectionDialog(int maximum,Consumer<Selection> selected,mindustry.type.UnitType type,mindustry.game.Team team,int count){
        super(Core.bundle.get("st.selection.title")); this.maximum = maximum; this.selected = selected;
        previewType=type;previewTeam=team;previewCount=count;
        var style = new arc.scene.ui.Dialog.DialogStyle();
        style.titleFont = getStyle().titleFont; style.titleFontColor = getStyle().titleFontColor;
        setStyle(style); // The real map remains visible, without the normal modal dimmer.
        addCloseButton();
        if(previewType!=null){
            titleTable.clearChildren();
            buttons.clearChildren();buttons.button(Core.bundle.get("cancel"),this::hide).width(180).height(42);
        }
        if(previewType==null){
            cont.add(Core.bundle.get("st.workspace.selection-help")).wrap().width(Math.min(520,Core.scene.getWidth()/UiSizing.scale()-60)).row();
            buttons.button(Core.bundle.get("st.workspace.confirm"), this::commit).disabled(b -> !ready).height(48);
        }
        keyDown(KeyCode.enter, this::commit);
        keyDown(KeyCode.escape, this::hide);
        shown(() -> { if(ToolWorkspace.active != null && previewType==null) ToolWorkspace.active.suspend(true); });
        hidden(() -> { if(ToolWorkspace.active != null) ToolWorkspace.active.suspend(false); });
        var hint=new arc.scene.ui.layout.Table(mindustry.gen.Tex.paneSolid);
        if(previewType!=null){
            hint.margin(8);hint.image(previewType.uiIcon).size(28).padRight(6);
            hint.add(previewType.localizedName+" ×"+previewCount).color(mindustry.graphics.Pal.accent).row();
            hint.add(Core.bundle.get("st.ui.point-hint")).colspan(2).width(Math.min(300,Core.scene.getWidth()*.5f)).wrap();hint.pack();hint.touchable=arc.scene.event.Touchable.disabled;
        }
        arc.scene.Group canvas = new arc.scene.Group() {
            @Override public void draw() {
                if(previewType!=null){
                    if(world!=Vars.world.tiles||!Vars.state.isGame())return;
                    if(!pointerSet){var point=Core.camera.unproject(new arc.math.geom.Vec2(Core.input.mouseX(),Core.input.mouseY()));
                        endX=tile(point.x,Vars.world.width());endY=tile(point.y,Vars.world.height());}
                    var p=Core.camera.project(new arc.math.geom.Vec2(endX*Vars.tilesize,endY*Vars.tilesize));
                    var edge=Core.camera.project(new arc.math.geom.Vec2((endX+.5f)*Vars.tilesize,(endY+.5f)*Vars.tilesize));
                    float size=Math.max(16,Math.abs(edge.x-p.x)*2);
                    Draw.color(previewTeam.color,.65f);Draw.rect(previewType.uiIcon,p.x,p.y,size*2,size*2);Draw.reset();
                    Draw.color(mindustry.graphics.Pal.accent);Lines.stroke(2);Lines.rect(p.x-size/2,p.y-size/2,size,size);Draw.reset();
                    var local=stageToLocalCoordinates(new arc.math.geom.Vec2(p.x+20,p.y+28));
                    hint.setPosition(Math.max(0,Math.min(getWidth()-hint.getWidth(),local.x)),Math.max(0,Math.min(getHeight()-hint.getHeight(),local.y)));
                    super.draw();return;
                }
                if (!dragging && !ready) return;
                var a = Core.camera.project(new arc.math.geom.Vec2((Math.min(startX,endX)-.5f)*Vars.tilesize,(Math.min(startY,endY)-.5f)*Vars.tilesize));
                var b = Core.camera.project(new arc.math.geom.Vec2((Math.max(startX,endX)+.5f)*Vars.tilesize,(Math.max(startY,endY)+.5f)*Vars.tilesize));
                Draw.color(validSize() ? mindustry.graphics.Pal.accent : Color.scarlet); Lines.stroke(2f);
                Lines.rect(a.x,a.y,b.x-a.x,b.y-a.y); Draw.reset();
            }
        };
        canvas.name="st-map-selection-canvas";canvas.touchable=arc.scene.event.Touchable.enabled;
        canvas.addListener(new InputListener() {
            @Override public boolean mouseMoved(InputEvent event,float x,float y){
                if(previewType==null)return false;
                var point=worldPoint(event);endX=tile(point.x,Vars.world.width());endY=tile(point.y,Vars.world.height());pointerSet=true;return true;
            }
            @Override public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                // Arc registers touch focus after touchDown returns. Removing the actor here
                // makes Element.notify dereference a missing scene; close on the next UI turn.
                if(button == KeyCode.mouseRight){cancelling=true;dragging=false;ready=false;Core.app.post(()->{if(isShown())hide();});return true;}
                if(cancelling)return true;
                if (button != KeyCode.mouseLeft || !Vars.state.isGame()) return false;
                sx = ex = x; sy = ey = y; startX = tile(worldPoint(event).x, Vars.world.width());
                startY = tile(worldPoint(event).y, Vars.world.height()); endX=startX;endY=startY;ready=false;dragging = true; return true;
            }
            @Override public void touchDragged(InputEvent event, float x, float y, int pointer) { ex = x; ey = y; endX=tile(worldPoint(event).x,Vars.world.width());endY=tile(worldPoint(event).y,Vars.world.height()); }
            @Override public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
                if (button!=KeyCode.mouseLeft||cancelling||!dragging) return; dragging = false;
                endX=tile(worldPoint(event).x,Vars.world.width());endY=tile(worldPoint(event).y,Vars.world.height());ready=true;
                if(previewType!=null){startX=endX;startY=endY;commit();}
            }
        });
        cont.add(canvas).size(Math.max(180, Core.scene.getWidth() / UiSizing.scale() - 40), Math.max(100, UiSizing.height() - 160)).row();
        if(previewType!=null)canvas.addChild(hint);
        update(()->{if(world!=Vars.world.tiles||!Vars.state.isGame())hide();});
    }
    private static arc.math.geom.Vec2 worldPoint(InputEvent event){return Core.camera.unproject(new arc.math.geom.Vec2(event.stageX,event.stageY));}
    private boolean validSize(){return Math.abs(endX-startX)+1<=maximum && Math.abs(endY-startY)+1<=maximum;}
    private void commit(){
        if(!ready||cancelling)return;
        try{accept(startX,startY,endX,endY);}catch(IllegalArgumentException failure){Vars.ui.showInfoFade(Core.bundle.get("st.schematic.error.selection"));}
    }
    @Override public void hide(){cancelling=true;dragging=false;ready=false;InputDismissal.run(() -> super.hide(null));}
    private static int tile(float value, int limit) { return Math.max(0, Math.min(limit - 1, mindustry.core.World.toTile(value))); }
    public void accept(int x1, int y1, int x2, int y2) {
        if (world != Vars.world.tiles || !Vars.state.isGame() || Math.abs((long)x2 - x1) + 1 > maximum || Math.abs((long)y2 - y1) + 1 > maximum
                || x1 < 0 || x2 < 0 || y1 < 0 || y2 < 0 || x1 >= Vars.world.width() || x2 >= Vars.world.width()
                || y1 >= Vars.world.height() || y2 >= Vars.world.height()) throw new IllegalArgumentException("st.schematic.error.selection");
        Selection value = new Selection(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2));
        hide(); selected.accept(value);
    }
}
