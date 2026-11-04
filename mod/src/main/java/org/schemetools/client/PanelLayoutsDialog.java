package org.schemetools.client;
import arc.Core;
import mindustry.Vars;
import mindustry.ui.dialogs.BaseDialog;

public final class PanelLayoutsDialog extends ToolDialog {
    private String name="";
    private final HostPanel panel;
    private final RouteOverlay routes;
    public PanelLayoutsDialog(HostPanel panel,RouteOverlay routes){
        super(Core.bundle.get("st.layout.title"));this.panel=panel;this.routes=routes;addCloseButton();shown(this::rebuild);
    }
    private static String text(String key){return Core.bundle.get(key);}
    private void rebuild(){
        cont.clear();cont.pane(root->{
            root.defaults().pad(4).left();root.add(text("st.layout.help")).wrap().width(UiSizing.width(560)).row();
            root.field(name,value->name=value).width(UiSizing.width(560)).maxTextLength(48).row();
            root.button(text("st.layout.save"),()->run(()->{
                var layout=PanelLayouts.capture(name);Vars.ui.showConfirm(text("st.layout.title"),Core.bundle.format("st.layout.confirm-save",layout.name()),()->run(()->{PanelLayouts.save(layout);rebuild();}));
            })).row();
            try{for(var layout:PanelLayouts.list())root.table(row->{
                row.button(layout.name(),()->Vars.ui.showConfirm(text("st.layout.title"),Core.bundle.format("st.layout.confirm-load",layout.name()),()->run(()->{
                    PanelLayouts.apply(layout);panel.stopLocalTasks();routes.enabled(false);panel.refreshLayout();name=layout.name();rebuild();
                }))).width(UiSizing.width(420));
                row.button("×",()->Vars.ui.showConfirm(text("st.layout.title"),Core.bundle.format("st.layout.confirm-delete",layout.name()),()->run(()->{PanelLayouts.remove(layout.name());rebuild();}))).size(44);
            }).row();}catch(RuntimeException failure){root.add(text("st.layout.invalid")).wrap().width(UiSizing.width(560)).row();}
        }).width(UiSizing.width(600)).maxHeight(UiSizing.height()*.65f);
    }
    private static void run(Runnable action){try{action.run();}catch(RuntimeException failure){Vars.ui.showErrorMessage(text("st.layout.invalid"));}}
}
