package org.schemetools.client;

import arc.Core;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.entities.units.BuildPlan;
import mindustry.type.Item;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import org.schemetools.host.ArenaTools;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Ordinary construction helpers remain separate from direct host terrain editing. */
public final class BuildToolsDialog extends ToolDialog {
    private final BuildTools tools;
    private final org.schemetools.host.HostActions actions;
    private final org.schemetools.host.BuildingStamp instant;
    private boolean instantMode=true;
    private final Object world=Vars.world.tiles;
    private Block block = Blocks.copperWall;
    private Item item = Items.copper;
    private int rotation;
    private Object config;
    private String x = "0", y = "0", width = "5", height = "5", powerRadius = "5";
    private ArenaTools.Shape shape = ArenaTools.Shape.RECTANGLE;
    private boolean outline, advanced;
    private ArenaDialog terrainDialog;

    public BuildToolsDialog(BuildTools tools) {
        this(tools,new org.schemetools.host.HostActions());
    }
    public BuildToolsDialog(BuildTools tools,org.schemetools.host.HostActions actions){
        super(text("st.build.title"));
        this.actions=actions;this.instant=new org.schemetools.host.BuildingStamp(actions);
        this.tools = tools;
        if (Vars.state.isGame()) { x = "" + Vars.player.tileX(); y = "" + Vars.player.tileY(); }
        addCloseButton();
        shown(this::rebuild);
    }
    private static String text(String key) { return Core.bundle.get(key); }
    @Override public boolean compactContent(){return true;}
    @Override public void refreshWidth(){rebuild();}
    private void action(arc.scene.ui.layout.Table root,String key,Runnable run){ToolUi.action(root,text(key),key+".help",run);}
    private void rebuild() {
        var root=ToolUi.content(this);
        if(actions.godMode())ToolUi.action(root,text("st.arena.edit-map"),"st.arena.help",()->{if(terrainDialog==null)terrainDialog=new ArenaDialog(new ArenaTools(actions));terrainDialog.show();});
        action(root,"st.selection.title",()->new AreaSelectionDialog(512,area->{x=""+area.x1();y=""+area.y1();width=""+(area.x2()-area.x1()+1);height=""+(area.y2()-area.y1()+1);rebuild();}).show());
        ToolUi.action(root,text("st.build.block")+": "+block.localizedName,"st.help.build-block",()->{
            List<Block> blocks=new ArrayList<>();
            for(Block value:Vars.content.blocks())if(value.hasBuilding()&&!value.isFloor()&&(actions.godMode()||(value.isVisible()&&!value.privileged&&!value.isBanned()&&value.unlockedNow()&&value.supportsEnv(Vars.state.rules.env))))blocks.add(value);
            ContentPicker.icons(text("st.build.block"),blocks,block,selected->{block=selected;config=null;rebuild();});
        }).name="st-build-pick-block";
        action(root,"st.build.fill",()->{
            if(actions.godMode()&&instantMode)prepareInstant();
            else prepare(()->{int sx=integer(x),sy=integer(y),w=integer(width),h=integer(height);
                List<BuildPlan> plans=tools.fill(new ArenaTools.Area(sx,sy,Math.addExact(sx,w-1),Math.addExact(sy,h-1),shape),block,rotation,outline);
                for(BuildPlan plan:plans)plan.config=config;return plans;});
        });
        action(root,"st.build.restore",()->execute(tools::restoreDeleted,false));
        if(actions.godMode())action(root,"st.short.instant-undo",()->execute(instant::undo,false));
        ToolUi.action(root,text("st.workspace.advanced")+(advanced?" ▴":" ▾"),"st.help.build-advanced",()->{advanced=!advanced;rebuild();});
        if(!advanced)return;
        if(actions.godMode())root.check(text("st.short.instant"),instantMode,v->instantMode=v).row();
        ToolUi.valueField(root,"X",x,v->x=v);ToolUi.valueField(root,"Y",y,v->y=v);
        action(root,"st.build.at-player",()->{x=""+Vars.player.tileX();y=""+Vars.player.tileY();rebuild();});
        action(root,"st.build.copy",()->execute(()->{var build=Vars.world.build(integer(x),integer(y));if(build==null||(!actions.godMode()&&build.team!=Vars.player.team()))throw new IllegalArgumentException("st.build.error.target");block=build.block;rotation=build.rotation;config=build.config();rebuild();return 1;},false));
        ToolUi.action(root,Core.bundle.format("st.build.rotation",rotation),"st.help.build-rotation",()->{rotation=(rotation+1)%4;rebuild();});
        ToolUi.valueField(root,text("st.build.width"),width,v->width=v);ToolUi.valueField(root,text("st.build.height"),height,v->height=v);
        ToolUi.action(root,text("st.arena.shape."+shape.name().toLowerCase(java.util.Locale.ROOT)),"st.help.build-shape",()->{shape=shape==ArenaTools.Shape.CIRCLE?ArenaTools.Shape.RECTANGLE:ArenaTools.Shape.CIRCLE;rebuild();});
        root.check(text("st.build.outline"),outline,v->outline=v).row();
        action(root,"st.build.replace",()->prepare(()->tools.cluster(integer(x),integer(y),block,false)));
        action(root,"st.build.replace-rebuild",()->ToolUi.confirmAction(text("st.build.title"),text("st.build.replace-rebuild-warning"),()->execute(()->tools.replaceCluster(integer(x),integer(y),block),false)));
        root.label(()->Core.bundle.format("st.build.replacement-pending",tools.pendingReplacements())).wrap().row();
        action(root,"st.build.replacement-cancel",tools::cancelReplacement);
        action(root,"st.build.remove",()->prepare(()->tools.cluster(integer(x),integer(y),null,true)));
        action(root,"st.build.remove-plans",()->ToolUi.confirmAction(text("st.build.title"),text("st.build.confirm-remove-plans"),()->execute(()->tools.removePlanCluster(integer(x),integer(y)),false)));
        action(root,"st.build.connect",()->execute(()->tools.connectPower(integer(x),integer(y)),true));
        ToolUi.valueField(root,text("st.build.power-radius"),powerRadius,v->powerRadius=v);
        action(root,"st.build.power-line",this::preparePowerLine);
        ToolUi.action(root,text("st.item")+": "+item.localizedName,"st.help.item-pick",()->{List<Item> items=new ArrayList<>();for(Item value:Vars.content.items())items.add(value);ContentPicker.icons(text("st.item"),items,item,selected->{item=selected;rebuild();});});
        action(root,"st.build.withdraw",()->execute(()->{tools.withdraw(integer(x),integer(y),item);return 1;},true));
        action(root,"st.build.deposit",()->execute(()->{tools.deposit(integer(x),integer(y));return 1;},true));
        action(root,"st.build.drop",this::confirmDrop);
        action(root,"st.build.priority-drop",this::confirmPreferredDrop);
        root.label(()->text(tools.preferredDropStatus())).wrap().row();
        action(root,"st.build.priority-drop-cancel",tools::cancelPreferredDrop);
    }
    private void prepareInstant(){
        try{
            if(world!=Vars.world.tiles)throw new IllegalStateException("st.arena.error.world");
            var preview=instant.preview(integer(x),integer(y),integer(width),integer(height),block,Vars.player.team(),rotation,shape,outline).configured(config);
            ToolUi.confirmAction(text("st.build.title"),Core.bundle.format("st.build.instant-confirm",preview.count(),preview.replacements()),()->execute(()->instant.apply(preview),false));
        }catch(RuntimeException failure){error(failure);}
    }
    private void prepare(Supplier<List<BuildPlan>> prepare) {
        try {
            if(world!=Vars.world.tiles)throw new IllegalStateException("st.arena.error.world");
            List<BuildPlan> plans = prepare.get();
            ToolUi.confirmAction(text("st.build.title"), Core.bundle.format("st.build.confirm", plans.size()),
                () -> execute(() -> tools.enqueue(plans), false));
        } catch (RuntimeException failure) { error(failure); }
    }
    private void preparePowerLine(){
        try{
            if(world!=Vars.world.tiles)throw new IllegalStateException("st.arena.error.world");
            BuildTools.PowerLine line=tools.powerLine(integer(x),integer(y),integer(powerRadius),block);
            ToolUi.confirmAction(text("st.build.power-line"),Core.bundle.format("st.build.power-line-confirm",line.targetName(),line.targetX(),line.targetY(),line.count()),
                ()->execute(()->tools.enqueuePowerLine(line),false));
        }catch(RuntimeException failure){error(failure);}
    }
    private void confirmDrop(){
        try{
            if(world!=Vars.world.tiles||!Vars.state.isGame()||Vars.player.dead())throw new IllegalStateException("st.arena.error.world");
            var unit=Vars.player.unit();var item=unit.stack.item;int amount=unit.stack.amount;
            if(item==null||amount<=0)throw new IllegalArgumentException("st.build.error.items");
            ToolUi.confirmAction(text("st.build.drop"),Core.bundle.format("st.build.drop-confirm",item.localizedName,amount),()->execute(()->{
                if(Vars.player.dead()||Vars.player.unit()!=unit||unit.stack.item!=item||unit.stack.amount!=amount)
                    throw new IllegalStateException("st.build.priority-drop-changed");
                tools.drop();return 1;
            },true));
        }catch(RuntimeException failure){error(failure);}
    }
    private void confirmPreferredDrop(){
        try{
            if(world!=Vars.world.tiles)throw new IllegalStateException("st.arena.error.world");
            var preview=tools.previewPreferredDrop(integer(x),integer(y));
            ToolUi.confirmAction(text("st.build.priority-drop"),Core.bundle.format("st.build.priority-drop-confirm",preview.itemName(),preview.amount(),preview.x(),preview.y()),
                ()->execute(()->{tools.beginPreferredDrop(preview);return 1;},true));
        }catch(RuntimeException failure){error(failure);}
    }
    private void execute(Supplier<Integer> action, boolean request) {
        try {
            if(world!=Vars.world.tiles)throw new IllegalStateException("st.arena.error.world");
            Vars.ui.showInfoFade(Core.bundle.format(request ? "st.build.requested" : "st.build.result", action.get()));
        }
        catch (RuntimeException failure) { error(failure); }
    }
    private static int integer(String value) { return Integer.parseInt(value.trim()); }
    private static void error(RuntimeException failure) {
        String key = failure.getMessage();
        Vars.ui.showErrorMessage(key != null && Core.bundle.has(key) ? text(key) : text("st.error.input"));
    }
}
