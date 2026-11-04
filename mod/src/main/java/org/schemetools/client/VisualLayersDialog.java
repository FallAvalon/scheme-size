package org.schemetools.client;

import arc.Core;

public final class VisualLayersDialog extends ToolDialog {
    public VisualLayersDialog(){super(Core.bundle.get("st.visual.title"));addCloseButton();shown(this::rebuild);rebuild();}
    @Override public boolean compactContent(){return true;}
    @Override public void refreshWidth(){rebuild();}
    private void rebuild(){
        var root=ToolUi.content(this);
        for(var option:VisualLayers.Option.values()){
            String id=option.name().toLowerCase(java.util.Locale.ROOT).replace('_','-');
            String label=Core.bundle.get("st.short.visual."+id);
            root.table(row->{
                var check=row.check(label,VisualLayers.enabled(option),value->VisualLayers.enabled(option,value)).width(ToolUi.width()-44).minHeight(40).left().get();
                check.getLabel().setWrap(true);check.getLabelCell().width(Math.max(60,ToolUi.width()-96));
                ToolUi.helpButton(row,label,"st.help.visual."+id);
            }).row();
        }
        root.table(row->{
            var check=row.check(Core.bundle.get("st.short.light"),Core.settings.getBool("drawlight",true),v->Core.settings.put("drawlight",v)).width(ToolUi.width()-44).minHeight(40).left().get();
            check.getLabel().setWrap(true);check.getLabelCell().width(Math.max(60,ToolUi.width()-96));
            ToolUi.helpButton(row,Core.bundle.get("st.short.light"),"st.visual.rules-help");
        }).row();
        root.table(row->{
            row.label(()->Core.bundle.format("st.short.lasers",Core.settings.getInt("lasersopacity",100))).wrap().width(ToolUi.width()-44).left();
            ToolUi.helpButton(row,Core.bundle.get("st.short.light"),"st.help.lasers");
        }).row();
        root.slider(0,100,5,Core.settings.getInt("lasersopacity",100),v->{int opacity=Math.round(v);Core.settings.put("lasersopacity",opacity);Core.settings.put("preferredlaseropacity",opacity);}).get().name="scheme-size-power-laser-opacity";root.row();
    }
}
