package org.schemetools.client;

import arc.Core;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.type.Item;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import org.schemetools.host.HostActions;
import java.util.*;

/** God organizer resource sets; opening/loading a preset never edits the world. */
public final class ResourceLoadoutDialog extends ToolDialog {
    private final HostActions actions;
    private final Team team;
    private final CoreBuild core;
    private final Object world=Vars.world.tiles;
    private final Map<Item,String> amounts=new LinkedHashMap<>();
    private String name="";
    public ResourceLoadoutDialog(HostActions actions,Team team,CoreBuild core){
        super(text("st.loadout.title"));this.actions=actions;this.team=team;this.core=core;addCloseButton();shown(this::rebuild);
    }
    private static String text(String key){return Core.bundle.get(key);}
    private ResourceLoadouts.Preset current(){
        var values=new LinkedHashMap<Item,Integer>();amounts.forEach((item,value)->values.put(item,Integer.parseInt(value.strip())));
        return new ResourceLoadouts.Preset(name,values);
    }
    @Override public boolean compactContent(){return true;}
    @Override public void refreshWidth(){rebuild();}
    private void rebuild(){
        var root=ToolUi.content(this);
        root.add(Core.bundle.format("st.loadout.target",team.localized(),core.tileX(),core.tileY())).wrap().row();
        ToolUi.valueField(root,text("st.loadout.name"),name,value->name=value.length()>48?value.substring(0,48):value);
        for(var entry:List.copyOf(amounts.entrySet())){
            root.table(row->{
                row.image(entry.getKey().uiIcon).size(28).padRight(4);
                row.add(entry.getKey().localizedName).wrap().width(ToolUi.width()-76).left();
                row.button("×",()->{amounts.remove(entry.getKey());rebuild();}).size(40);
            }).row();
            ToolUi.field(root,text("st.amount"),entry.getValue(),value->amounts.put(entry.getKey(),value));
        }
        ToolUi.action(root,text("st.loadout.add"),"st.help.item-pick",()->{
            var items=new ArrayList<Item>();for(Item item:Vars.content.items())items.add(item);
            ContentPicker.icons(text("st.item"),items,null,item->{if(amounts.size()<64||amounts.containsKey(item))amounts.putIfAbsent(item,"0");rebuild();});
        });
        ToolUi.action(root,text("st.apply"),"st.loadout.help",()->run(()->{
            var values=new LinkedHashMap<Item,Integer>();amounts.forEach((item,value)->values.put(item,Integer.parseInt(value.strip())));
            Vars.ui.showConfirm(text("st.loadout.title"),Core.bundle.format("st.loadout.confirm-apply",name,team.localized(),core.tileX(),core.tileY(),values.size()),()->run(()->{
                if(world!=Vars.world.tiles)throw new IllegalStateException("st.arena.error.world");actions.setCoreItems(team,core,values);Vars.ui.showInfoFade(text("st.success"));
            }));
        })).setDisabled(!actions.isAvailable());
        ToolUi.action(root,text("st.loadout.save"),"st.loadout.help",()->run(()->{
            var preset=current();Vars.ui.showConfirm(text("st.loadout.title"),Core.bundle.format("st.loadout.confirm-save",preset.name()),()->run(()->{ResourceLoadouts.save(preset);rebuild();}));
        }));
        try{for(var preset:ResourceLoadouts.list())root.table(row->{
            var button=row.button(preset.name(),()->{name=preset.name();amounts.clear();preset.items().forEach((item,amount)->amounts.put(item,""+amount));rebuild();}).width(ToolUi.width()-44).minHeight(44).get();button.getLabel().setWrap(true);
            row.button("×",()->Vars.ui.showConfirm(text("st.loadout.title"),Core.bundle.format("st.loadout.confirm-delete",preset.name()),()->run(()->{ResourceLoadouts.remove(preset.name());rebuild();}))).size(40).padLeft(4);
        }).row();}catch(RuntimeException failure){root.add(text("st.loadout.invalid")).wrap().row();}
    }
    private static void run(Runnable action){try{action.run();}catch(RuntimeException failure){Vars.ui.showErrorMessage(Core.bundle.get(failure.getMessage()==null?"st.error.input":failure.getMessage(),text("st.error.input")));}}
}
