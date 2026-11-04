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
    public AreaSelectionDialog(int maximum, Consumer<Selection> selected) {
        super(Core.bundle.get("st.selection.title")); this.maximum = maximum; this.selected = selected;
        var style = new arc.scene.ui.Dialog.DialogStyle();
        style.titleFont = getStyle().titleFont; style.titleFontColor = getStyle().titleFontColor;
        setStyle(style); // The real map remains visible, without the normal modal dimmer.
        addCloseButton();
        cont.add(Core.bundle.get("st.workspace.selection-help")).wrap().width(520).row();
        buttons.button(Core.bundle.get("st.workspace.confirm"), this::commit).disabled(b -> !ready).height(48);
        keyDown(KeyCode.enter, this::commit);
        keyDown(KeyCode.escape, this::hide);
        shown(() -> { if(ToolWorkspace.active != null) ToolWorkspace.active.suspend(true); });
        hidden(() -> { if(ToolWorkspace.active != null) ToolWorkspace.active.suspend(false); });
        Element canvas = new Element() {
            @Override public void draw() {
                if (!dragging && !ready) return;
                var a = Core.camera.project(new arc.math.geom.Vec2((Math.min(startX,endX)-.5f)*Vars.tilesize,(Math.min(startY,endY)-.5f)*Vars.tilesize));
                var b = Core.camera.project(new arc.math.geom.Vec2((Math.max(startX,endX)+.5f)*Vars.tilesize,(Math.max(startY,endY)+.5f)*Vars.tilesize));
                Draw.color(validSize() ? mindustry.graphics.Pal.accent : Color.scarlet); Lines.stroke(2f);
                Lines.rect(a.x,a.y,b.x-a.x,b.y-a.y); Draw.reset();
            }
        };
        canvas.addListener(new InputListener() {
            @Override public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                if(button == KeyCode.mouseRight){hide();return true;}
                if (button != KeyCode.mouseLeft || !Vars.state.isGame()) return false;
                sx = ex = x; sy = ey = y; startX = tile(worldPoint(event).x, Vars.world.width());
                startY = tile(worldPoint(event).y, Vars.world.height()); endX=startX;endY=startY;ready=false;dragging = true; return true;
            }
            @Override public void touchDragged(InputEvent event, float x, float y, int pointer) { ex = x; ey = y; endX=tile(worldPoint(event).x,Vars.world.width());endY=tile(worldPoint(event).y,Vars.world.height()); }
            @Override public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
                if (!dragging) return; dragging = false;
                endX=tile(worldPoint(event).x,Vars.world.width());endY=tile(worldPoint(event).y,Vars.world.height());ready=true;
            }
        });
        cont.add(canvas).size(Math.max(180, Core.scene.getWidth() / UiSizing.scale() - 40), Math.max(100, UiSizing.height() - 160)).row();
    }
    private static arc.math.geom.Vec2 worldPoint(InputEvent event){return Core.camera.unproject(new arc.math.geom.Vec2(event.stageX,event.stageY));}
    private boolean validSize(){return Math.abs(endX-startX)+1<=maximum && Math.abs(endY-startY)+1<=maximum;}
    private void commit(){
        if(!ready)return;
        try{accept(startX,startY,endX,endY);}catch(IllegalArgumentException failure){Vars.ui.showInfoFade(Core.bundle.get("st.schematic.error.selection"));}
    }
    @Override public void hide(){ InputDismissal.run(() -> super.hide(null)); }
    private static int tile(float value, int limit) { return Math.max(0, Math.min(limit - 1, mindustry.core.World.toTile(value))); }
    public void accept(int x1, int y1, int x2, int y2) {
        if (world != Vars.world.tiles || !Vars.state.isGame() || Math.abs((long)x2 - x1) + 1 > maximum || Math.abs((long)y2 - y1) + 1 > maximum
                || x1 < 0 || x2 < 0 || y1 < 0 || y2 < 0 || x1 >= Vars.world.width() || x2 >= Vars.world.width()
                || y1 >= Vars.world.height() || y2 >= Vars.world.height()) throw new IllegalArgumentException("st.schematic.error.selection");
        Selection value = new Selection(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2));
        hide(); selected.accept(value);
    }
}
