package org.schemetools.client;
import arc.Core;
import mindustry.ai.Pathfinder;
import mindustry.ui.dialogs.BaseDialog;
public final class RoutesDialog extends ToolDialog {
    private final RouteOverlay overlay;
    private final int[] types={Pathfinder.costGround,Pathfinder.costLegs,Pathfinder.costNaval,Pathfinder.costHover};
    private final String[] labels={"st.routes.ground","st.routes.legs","st.routes.naval","st.routes.hover"};
    public RoutesDialog(RouteOverlay overlay){super(Core.bundle.get("st.routes.title"));this.overlay=overlay;addCloseButton();shown(this::rebuild);rebuild();}
    @Override public boolean compactContent(){return true;}
    @Override public void refreshWidth(){rebuild();}
    private void rebuild(){
        var root=ToolUi.content(this);
        root.table(row->{
            var check=row.check(Core.bundle.get("st.routes.enabled"),overlay.enabled(),overlay::enabled).width(ToolUi.width()-44).minHeight(40).left().get();
            check.getLabel().setWrap(true);check.getLabelCell().width(Math.max(60,ToolUi.width()-96));
            ToolUi.helpButton(row,Core.bundle.get("st.routes.title"),"st.routes.help");
        }).row();
        int index=0;for(int i=0;i<types.length;i++)if(types[i]==overlay.costType())index=i;
        ToolUi.action(root,Core.bundle.get(labels[index]),"st.help.route-type",()->{
            BaseDialog picker=new BaseDialog(Core.bundle.get("st.routes.title"));picker.name="st-modal";picker.addCloseButton();
            for(int i=0;i<types.length;i++){int type=types[i];picker.cont.button(Core.bundle.get(labels[i]),()->{overlay.costType(type);picker.hide();rebuild();}).width(240).height(44).row();}picker.show();
        });
        ToolUi.action(root,Core.bundle.get("st.routes.refresh"),"st.help.route-refresh",()->{overlay.enabled(true);overlay.refresh();rebuild();});
        root.label(()->{var result=overlay.result();if(result==null)return overlay.status();long reached=result.routes().stream().filter(org.schemetools.shared.GridRoutes.Route::reached).count();return Core.bundle.format("st.short.routes-ready",reached,result.routes().size());}).wrap().row();
    }
}
