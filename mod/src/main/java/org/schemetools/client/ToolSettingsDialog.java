package org.schemetools.client;

import arc.Core;
import arc.input.KeyCode;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import mindustry.Vars;
import mindustry.ui.dialogs.BaseDialog;

/** Settings only affect this mod's namespace. Key changes always display matching vanilla binds. */
public final class ToolSettingsDialog extends ToolDialog {
    private final ModKeys keys;
    private final RouteOverlay routes;
    private final HostPanel panel;
    public ToolSettingsDialog(ModKeys keys, RouteOverlay routes, HostPanel panel) {
        super(text("st.settings.title"));
        this.keys = keys; this.routes = routes; this.panel = panel;
        addCloseButton(); shown(this::rebuild);
    }
    private static String text(String key) { return Core.bundle.get(key); }
    private String section="interface";
    @Override public boolean compactContent(){return true;}
    @Override public void refreshWidth(){rebuild();}
    private void action(arc.scene.ui.layout.Table root,String label,String help,Runnable run){ToolUi.action(root,text(label),help,run);}
    private void toggle(arc.scene.ui.layout.Table root,String label,String help,boolean value,arc.func.Boolc changed){
        root.table(row->{var box=row.check(text(label),value,changed).width(ToolUi.width()-44).minHeight(40).left().get();box.getLabel().setWrap(true);box.getLabelCell().width(Math.max(40,ToolUi.width()-96));ToolUi.helpButton(row,text(label),help);}).row();
    }
    private void preference(arc.scene.ui.layout.Table root,String label,String key,boolean fallback){toggle(root,label,"st.settings.help",Core.settings.getBool(key,fallback),v->Core.settings.put(key,v));}
    private void rebuild(){
        var root=ToolUi.content(this);
        root.table(t->{int i=0;for(String id:new String[]{"interface","modules","keys","other"}){var button=t.button(text("st.settings.tab."+id),()->{section=id;rebuild();}).width((ToolUi.width()-8)/2).minHeight(40).get();button.getLabel().setWrap(true);if(++i%2==0)t.row();}}).row();
        switch(section){
            case "interface"->{
                preference(root,"setting.schemetools-hud.name","schemetools-hud",true);
                preference(root,"st.settings.welcome","schemesize-welcome",true);
                preference(root,"st.settings.wave-notice","schemesize-wave-notice",true);
                preference(root,"st.settings.view-custom","schemesize-view-custom",false);
                for(String name:new String[]{"pan-speed","zoom-out","zoom-in"})ToolUi.valueField(root,text("st.settings."+name),""+Core.settings.getInt("schemesize-"+name,1),v->number("schemesize-"+name,v,1,5));
                for(String name:new String[]{"hud-left","hud-top"})ToolUi.valueField(root,text("st.settings."+name),""+Core.settings.getInt("schemesize-"+name,name.equals("hud-left")?8:96),v->number("schemesize-"+name,v,0,600));
            }
            case "modules"->{
                String[] modules={"schematics","build","info","routes","visual","ai","console","players","arena"};
                String[] labels={"st.schematic.title","st.build.title","st.coreinfo.title","st.routes.title","st.visual.title","st.ai.title","st.console.title","st.players","st.arena.title"};
                for(int i=0;i<modules.length;i++){String module=modules[i];toggle(root,labels[i],"st.settings.help",Core.settings.getBool("schemesize-module-"+module,true),v->{
                    Core.settings.put("schemesize-module-"+module,v);
                    if(!v)switch(module){case "routes"->routes.enabled(false);case "ai"->panel.stopAutopilot();case "build"->panel.stopBuildingTasks();case "console"->panel.stopConsoleTasks();}
                    panel.refreshLayout();
                });}
            }
            case "keys"->{if(keys!=null){
                action(root,"st.actions.title","st.keys.help",()->{hide();new ActionSearchDialog(keys).show();});
                action(root,"st.keys.original","st.keys.help",()->Vars.ui.showConfirm(text("st.settings.title"),text("st.keys.original-warning"),()->{keys.assignOriginalPrimaryKeys();rebuild();}));
                for(ModKeys.Entry entry:keys.entries()){
                    root.add(text(entry.action().label)).wrap().row();
                    root.table(row->{var assign=row.button(ModKeys.display(entry),()->capture(entry)).width(ToolUi.width()-44).minHeight(44).get();assign.getLabel().setWrap(true);
                        row.button(mindustry.gen.Icon.refresh,mindustry.ui.Styles.clearNonei,()->{ModKeys.reset(entry);rebuild();}).size(40).padLeft(4).tooltip(text("st.keys.reset"));
                    }).row();
                }
            }}
            case "other"->{
                if(panel.organizerEdition())action(root,"st.layout.title","st.settings.help",()->new PanelLayoutsDialog(panel,routes).show());
                action(root,"st.update.title","st.settings.help",panel::openUpdates);
                action(root,"st.claj.title","st.settings.help",panel::openClaj);
                if(EditorResizeDialog.available())action(root,"st.editor.resize","st.settings.help",()->new EditorResizeDialog().show());
                toggle(root,"st.peer.share","st.peer.help",PeerInfo.enabled(),v->{Core.settings.put("schemesize-share-subtitle",v);PeerInfo.refresh();});
                ToolUi.field(root,text("st.short.subtitle"),Core.settings.getString("schemesize-subtitle",""),v->{try{Core.settings.put("schemesize-subtitle",PeerInfo.subtitle(v));}catch(IllegalArgumentException ignored){}});
                toggle(root,"st.settings.distributor","st.settings.distributor-warning",Core.settings.getBool("schemesize-controlled-distributor",false),v->Vars.ui.showConfirm(text("st.settings.title"),text("st.settings.distributor-warning"),()->Core.settings.put("schemesize-controlled-distributor",v)));
                action(root,"st.settings.reset","st.settings.help",()->Vars.ui.showConfirm(text("st.settings.title"),text("st.settings.reset-confirm"),this::resetPreferences));
            }
        }
    }
    private void resetPreferences(){
        for(String key:new String[]{"schemetools-hud","schemesize-hud-left","schemesize-hud-top"})Core.settings.remove(key);
        for(String name:new String[]{"welcome","wave-notice","view-custom","pan-speed","zoom-out","zoom-in","quick-tag","share-subtitle","subtitle","controlled-distributor"})Core.settings.remove("schemesize-"+name);
        for(String name:new String[]{"schematics","build","info","routes","visual","ai","console","players","arena"})Core.settings.remove("schemesize-module-"+name);
        for(VisualLayers.Option option:VisualLayers.Option.values())Core.settings.remove(option.key);
        PeerInfo.refresh();routes.enabled(false);panel.stopLocalTasks();if(keys!=null)keys.entries().forEach(ModKeys::reset);panel.refreshLayout();rebuild();
    }
    private static void number(String key, String text, int min, int max) {
        try { int value = Integer.parseInt(text); if (value >= min && value <= max) Core.settings.put(key, value); }
        catch (NumberFormatException ignored) { }
    }
    private void capture(ModKeys.Entry entry) {
        BaseDialog dialog = new BaseDialog(text(entry.action().label));
        dialog.cont.add(text("st.keys.capture")).wrap().width(UiSizing.width(480f)).row();
        dialog.buttons.button(text("st.keys.clear"), () -> { ModKeys.clear(entry); dialog.hide(); rebuild(); });
        dialog.addCloseButton();
        dialog.addCaptureListener(new InputListener() {
            @Override public boolean keyDown(InputEvent event, KeyCode key) {
                if (key == KeyCode.escape) return false;
                String name = key.name().toLowerCase(java.util.Locale.ROOT);
                if (name.contains("control") || name.startsWith("shift") || name.startsWith("alt")) return true;
                captured(entry, dialog, key);
                event.stop(); return true;
            }
            @Override public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                if (button != KeyCode.mouseBack && button != KeyCode.mouseForward && button != KeyCode.mouseMiddle) return false;
                // Leave the actor attached until Arc has registered this touch focus.
                ModKeys.Chord chord = new ModKeys.Chord(Core.input.ctrl(), Core.input.alt(), Core.input.shift());
                Core.app.post(() -> captured(entry, dialog, button, chord));
                event.stop(); return true;
            }
        });
        dialog.show();
    }
    private void captured(ModKeys.Entry entry, BaseDialog dialog, KeyCode key) {
        captured(entry, dialog, key, new ModKeys.Chord(Core.input.ctrl(), Core.input.alt(), Core.input.shift()));
    }
    private void captured(ModKeys.Entry entry, BaseDialog dialog, KeyCode key, ModKeys.Chord chord) {
        var conflicts = ModKeys.conflicts(entry, key);
        dialog.hide();
        Runnable apply = () -> { ModKeys.assign(entry, key, chord); rebuild(); };
        if (conflicts.isEmpty()) apply.run();
        else Vars.ui.showConfirm(text("st.settings.title"), Core.bundle.format("st.keys.conflicts", chord.prefix() + key, String.join(", ", conflicts)), apply);
    }
}
