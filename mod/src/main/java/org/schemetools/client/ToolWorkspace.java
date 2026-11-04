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
    private boolean expanded = Core.settings.getBool("schemesize-dock-labels",true);
    private boolean suspended;
    private String selectedTool;
    private final java.util.ArrayDeque<ToolDialog> history = new java.util.ArrayDeque<>();
    private boolean primary;
    public void suspend(boolean value) { suspended=value; root.visible=!value; }
    private float lastWidth = -1, lastHeight = -1;
    private int lastLeft=-1,lastTop=-1,lastModules=-1;
    private final java.util.List<String> modules=new java.util.ArrayList<>();
    private record Entry(Drawable icon,String key,String module,Runnable action){}
    private final java.util.List<Entry> entries=new java.util.ArrayList<>();
    private final Edition edition;
    public ToolWorkspace(HostPanel panel, Edition edition, RouteOverlay routes, ModKeys keys) {
        active = this; this.panel=panel;this.edition=edition;
        root.name = "scheme-size-hud";
        root.setFillParent(true);
        root.touchable = Touchable.childrenOnly;
        root.top().left();
        tool(Icon.book, "st.schematic.title", "schematics", panel::openSchematics);
        tool(Icon.hammer, "st.build.title", "build", panel::openBuildTools);
        tool(Icon.map, "st.routes.title", "routes", () -> panel.openRoutes());
        tool(Icon.eye, "st.visual.title", "visual", panel::openVisuals);
        if (edition.events) {
        tool(Icon.players, "st.players", "players", () -> panel.openPage("players"));
            tool(Icon.units, "st.units", "players", () -> panel.openPage("units"));
            tool(Icon.box, "st.items", "players", () -> panel.openPage("items"));
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
        entries.add(new Entry(icon,key,module,run));
    }
    private boolean labelled(){return expanded&&Core.scene.getWidth()/UiSizing.scale()>=960;}
    private float railWidth(){return labelled()?152:48;}
    private void rebuildRail(){
        rail.clearChildren();rail.margin(0);boolean labels=labelled();float width=railWidth();
        var brand=rail.button(edition==Edition.PLUS?"S+":"SG",()->{
            expanded=!expanded;Core.settings.put("schemesize-dock-labels",expanded);layout();
        }).width(width-4).height(40).pad(2).get();
        brand.name="st-dock-labels";brand.addListener(new arc.scene.ui.Tooltip(t->t.add(Core.bundle.get("st.ui.dock-help"))));rail.row();
        for(Entry entry:entries){
            if(!enabled(entry.module))continue;
            var cell=rail.button(button->{
                button.margin(6);button.left();
                // Native icons have different intrinsic sizes. Draw into one fixed slot
                // instead of letting those sizes determine each button's columns.
                Element glyph=new Element(){
                    @Override public void draw(){
                        float w=Math.max(1,entry.icon.getMinWidth()),h=Math.max(1,entry.icon.getMinHeight());
                        float scale=Math.min(width/w,height/h);
                        arc.graphics.g2d.Draw.color(arc.graphics.Color.white,parentAlpha);
                        entry.icon.draw(x+(width-w*scale)/2,y+(height-h*scale)/2,w*scale,h*scale);
                        arc.graphics.g2d.Draw.reset();
                    }
                };
                glyph.name="st-dock-icon";button.add(glyph).size(26).center();
                if(labels){String shortKey="st.nav."+entry.key;
                    button.add().width(10);
                    var label=button.add(Core.bundle.get(shortKey,Core.bundle.get(entry.key))).left().growX().minWidth(0).get();label.setFontScale(.75f);label.setEllipsis(true);label.name="st-dock-label";}
            },ToolUi.toggleStyle(),()->{
                selectedTool=entry.key;history.clear();primary=true;
                try{entry.action.run();}finally{primary=false;}
            }).width(width-4).height(42).pad(2);
            cell.get().update(()->cell.get().setChecked(current!=null&&entry.key.equals(selectedTool)));
            cell.get().name="scheme-tool-"+entry.key;cell.get().addListener(new arc.scene.ui.Tooltip(t->t.add(Core.bundle.get(entry.key))));rail.row();
        }
    }
    private boolean enabled(String module){return module==null || Core.settings.getBool("schemesize-module-"+module,true);}
    static float contentWidth() { return Math.max(240, Math.min(440, Core.scene.getWidth() / UiSizing.scale() - 360)); }
    private static float topInset(){return Math.min(Core.settings.getInt("schemesize-hud-top",96),UiSizing.height()*.2f);}
    static float contextHeight(){return Math.max(120,UiSizing.height()-topInset()-24);}
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
            if(contentWidth()>340)header.add("Esc").color(arc.graphics.Color.lightGray).padRight(8).get().setFontScale(.75f);
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
        rebuildRail();
        if(current!=null){
            var contentCell=body.getCell(current.cont);
            if(contentCell!=null)contentCell.width(current.compactContent()?ToolUi.width():contentWidth()-32);
        }
        root.marginTop(topInset())
            .marginLeft(Math.max(0,Math.min(Core.settings.getInt("schemesize-hud-left",8),Core.scene.getWidth()/UiSizing.scale()-320-contentWidth()-54)));
        root.pane(rail).width(railWidth()).maxHeight(contextHeight()).top().scrollX(false);
        if (current != null) root.add(context).width(contentWidth()).maxHeight(contextHeight()).padLeft(4).top();
    }
}
