package org.schemetools.client;

import arc.Core;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.event.VisibilityEvent;
import arc.scene.style.Drawable;
import arc.scene.ui.layout.Table;
import arc.input.KeyCode;
import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.ui.Styles;
import org.schemetools.Edition;

/** A single nonmodal context, leaving the map, building UI and minimap usable. */
public final class ToolWorkspace {
    static ToolWorkspace active;
    private final HostPanel panel;
    private final Table root = new Table(), context = new Table(Tex.paneSolid), body = new Table();
    private final Table rail = new Table(Tex.paneSolid);
    private ToolDialog current;
    private boolean expanded = true;
    private boolean suspended;
    private String selectedTool;
    private final java.util.ArrayDeque<ToolDialog> history = new java.util.ArrayDeque<>();
    private boolean primary;
    public void suspend(boolean value) { suspended=value; root.visible=!value; }
    private float lastWidth = -1, lastHeight = -1;
    private int lastLeft=-1,lastTop=-1,lastModules=-1;
    private final java.util.List<String> modules=new java.util.ArrayList<>();
    public ToolWorkspace(HostPanel panel, Edition edition, RouteOverlay routes, ModKeys keys) {
        active = this; this.panel=panel;
        root.name = "scheme-size-hud";
        root.setFillParent(true);
        root.touchable = Touchable.childrenOnly;
        root.top().left();
        rail.defaults().size(44).pad(1);
        rail.button(edition == Edition.PLUS ? "S+" : "SG", () -> { expanded = !expanded; layout(); }).tooltip(Core.bundle.get(edition.titleKey)).row();
        tool(Icon.book, "st.schematic.title", "schematics", panel::openSchematics);
        tool(Icon.hammer, "st.build.title", "build", panel::openBuildTools);
        tool(Icon.map, "st.routes.title", "routes", () -> panel.openRoutes());
        tool(Icon.eye, "st.visual.title", "visual", panel::openVisuals);
        if (edition.events) {
            tool(Icon.players, "st.players", "players", () -> panel.openPage("players"));
            tool(Icon.box, "st.items", "players", () -> panel.openPage("items"));
            tool(Icon.units, "st.units", "players", () -> panel.openPage("units"));
            tool(Icon.play, "st.event", "event", () -> panel.openPage("event"));
        }
        tool(Icon.resize, "st.arena.title", "arena", panel::openArenaTools);
        tool(Icon.bookOpen, "st.workspace.more", null, () -> panel.openPage("overview"));
        tool(Icon.settings, "st.settings.title", null, () -> new ToolSettingsDialog(keys, routes, panel).show());
        context.name = "scheme-size-context";
        context.top();
        Core.scene.root.addCaptureListener(new InputListener() {
            @Override public boolean touchDown(InputEvent event,float x,float y,int pointer,KeyCode button){
                if(current != null && !Core.scene.hasDialog() && event.targetActor != null
                        && !event.targetActor.isDescendantOf(context) && !event.targetActor.isDescendantOf(rail)){
                    Core.scene.unfocus(current.cont);Core.scene.unfocus(current.buttons);
                }
                return false;
            }
            @Override public boolean keyDown(InputEvent event, KeyCode key) {
                if (key != KeyCode.escape || current == null || Core.scene.hasDialog()) return false;
                // Control.update also polls the tap independently of scene event consumption.
                event.stop(); InputDismissal.run(ToolWorkspace.this::close); return true;
            }
        });
        root.update(() -> {
            float w = Core.scene.getWidth(), h = Core.scene.getHeight();
            int left=Core.settings.getInt("schemesize-hud-left",8),top=Core.settings.getInt("schemesize-hud-top",96),mask=0;
            for(int i=0;i<modules.size();i++)if(enabled(modules.get(i)))mask|=1<<i;
            if (w != lastWidth || h != lastHeight || left!=lastLeft || top!=lastTop || mask!=lastModules) {
                lastWidth = w; lastHeight = h;lastLeft=left;lastTop=top;lastModules=mask;layout();if(current!=null)current.refreshWidth();
            }
            boolean hud=Core.settings.getBool("schemetools-hud", true);
            rail.visible=hud;
            root.visible = !suspended && Vars.state.isGame() && (hud || current != null);
            if (!Vars.state.isGame() && current != null) close();
        });
        Vars.ui.hudGroup.addChild(root);
        arc.Events.on(mindustry.game.EventType.ResetEvent.class, event -> {suspended=false;close();});
        arc.Events.on(mindustry.game.EventType.WorldLoadEvent.class, event -> {suspended=false;close();});
        layout();
    }
    private void tool(Drawable icon, String key, String module, Runnable run) {
        modules.add(module);
        var cell = rail.button(icon, Styles.clearTogglei, () -> {
            selectedTool=key; history.clear(); primary=true;
            try{run.run();}finally{primary=false;}
        }).tooltip(Core.bundle.get(key));
        cell.get().update(() -> cell.get().setChecked(current != null && key.equals(selectedTool)));
        cell.get().name="scheme-tool-"+key;
        cell.get().visible(() -> expanded && (module == null || Core.settings.getBool("schemesize-module-" + module, true)));
        rail.row();
    }
    private boolean enabled(String module){return module==null || Core.settings.getBool("schemesize-module-"+module,true);}
    static float contentWidth() { return Math.max(200, Math.min(460, Core.scene.getWidth() / UiSizing.scale() - 390)); }
    private static float topInset(){return Math.min(Core.settings.getInt("schemesize-hud-top",96),UiSizing.height()*.2f);}
    static float contextHeight(){return Math.max(120,UiSizing.height()-topInset()-Math.max(80,160/UiSizing.scale()));}
    public ToolDialog current() { return current; }
    public void open(ToolDialog next) {
        if (current != next) {
            if(history.contains(next)){while(history.pop()!=next){} }
            else if(current != null && !primary)history.push(current);
            detach(); current = next;
            next.fire(new VisibilityEvent(false));
        }
        context.clearChildren();
        context.table(header -> {
            if(!history.isEmpty())header.button(Icon.left, Styles.clearNonei, this::back).size(32);
            header.add(next.title.getText()).ellipsis(true).growX().left().tooltip(panel.authorityDescription());
            header.button(Icon.cancel, Styles.clearNonei, this::close).size(36).tooltip(Core.bundle.get("st.workspace.close"));
        }).growX().row();
        body.clearChildren(); body.top();
        next.cont.remove(); next.buttons.remove();
        body.add(next.cont).width(next.compactContent()?ToolUi.width():contentWidth()-32).top().row();
        if(!next.compactContent())body.add(next.buttons).width(contentWidth()-32).row();
        context.pane(body).grow().scrollX(false);
        layout();
    }
    public void close() {
        history.clear(); detach(); layout();
    }
    public void back(){
        if(history.isEmpty()){close();return;}
        ToolDialog previous=history.pop();detach();open(previous);
    }
    private void detach() {
        if (current == null) return;
        ToolDialog old = current; current = null;
        Core.scene.unfocus(old.cont); Core.scene.unfocus(old.buttons);
        old.fire(new VisibilityEvent(true));
        context.clearChildren();
    }
    private void layout() {
        root.clearChildren();
        if(current!=null){
            var contentCell=body.getCell(current.cont);
            if(contentCell!=null)contentCell.width(current.compactContent()?ToolUi.width():contentWidth()-32);
        }
        for(int i=1;i<rail.getCells().size;i++){
            boolean show=expanded && enabled(modules.get(i-1));
            rail.getCells().get(i).size(show?44:0).pad(show?1:0);
        }
        root.marginTop(topInset())
            .marginLeft(Math.max(0,Math.min(Core.settings.getInt("schemesize-hud-left",8),Core.scene.getWidth()/UiSizing.scale()-320-contentWidth()-54)));
        root.pane(rail).width(50).maxHeight(contextHeight()).top().scrollX(false);
        if (current != null) root.add(context).width(contentWidth()).height(contextHeight()).padLeft(4).top();
    }
}
