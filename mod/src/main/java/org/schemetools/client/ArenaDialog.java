package org.schemetools.client;

import arc.Core;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.world.Block;
import mindustry.ui.dialogs.BaseDialog;
import org.schemetools.host.ArenaTools;

import java.util.ArrayList;
import java.util.List;

/** Explicit host map edit with a separate preview/commit step and conflict-safe undo. */
public final class ArenaDialog extends ToolDialog {
    private final ArenaTools tools;
    private ArenaTools.Shape shape = ArenaTools.Shape.RECTANGLE;
    private ArenaTools.Layer layer = ArenaTools.Layer.FLOOR;
    private Block floor = Blocks.stone, overlay = Blocks.air, block = Blocks.air;
    private final String[] coords = {"0", "0", "4", "4"};
    private ArenaTools.Edit preview;
    private boolean advanced;
    private org.schemetools.host.BuildingStamp stamp;
    public ArenaDialog(ArenaTools tools) {
        super(text("st.arena.title"));
        this.tools = tools;
        addCloseButton();
        shown(this::rebuild);
    }
    public ArenaDialog(ArenaTools tools,org.schemetools.host.BuildingStamp stamp){this(tools);this.stamp=stamp;}
    private static String text(String key) { return Core.bundle.get(key); }
    @Override public boolean compactContent(){return true;}
    @Override public void refreshWidth(){rebuild();}
    private void calculate(){
        preview=tools.preview(new ArenaTools.Area(Integer.parseInt(coords[0]),Integer.parseInt(coords[1]),Integer.parseInt(coords[2]),Integer.parseInt(coords[3]),shape),layer,floor,overlay,block);
    }
    private void apply(){run(()->{
        calculate();ArenaTools.Edit pending=preview;
        Vars.ui.showConfirm(text("st.arena.title"),Core.bundle.format("st.arena.confirm",pending.count()),()->run(()->{int count=tools.apply(pending);preview=null;rebuild();Vars.ui.showInfoFade(Core.bundle.format("st.arena.applied",count));}));
    });}
    private void rebuild(){
        var root=ToolUi.content(this);
        ToolUi.action(root,text("st.selection.title"),"st.selection.title.help",()->new AreaSelectionDialog(512,area->{preview=null;coords[0]=""+area.x1();coords[1]=""+area.y1();coords[2]=""+area.x2();coords[3]=""+area.y2();rebuild();}).show());
        if(layer==ArenaTools.Layer.FLOOR||layer==ArenaTools.Layer.TERRAIN)ToolUi.action(root,text("st.arena.layer.floor")+": "+floor.localizedName,"st.arena.help",()->pick(0));
        if(layer==ArenaTools.Layer.OVERLAY||layer==ArenaTools.Layer.TERRAIN)ToolUi.action(root,text("st.arena.layer.overlay")+": "+overlay.localizedName,"st.arena.help",()->pick(1));
        if(layer==ArenaTools.Layer.BLOCK||layer==ArenaTools.Layer.TERRAIN)ToolUi.action(root,text("st.arena.layer.block")+": "+block.localizedName,"st.arena.help",()->pick(2));
        ToolUi.action(root,text("st.arena.apply"),"st.arena.help",this::apply);
        ToolUi.action(root,text("st.arena.undo"),"st.help.god-undo",()->run(()->{int count=tools.undo();preview=null;rebuild();Vars.ui.showInfoFade(Core.bundle.format("st.arena.undone",count));}));
        ToolUi.action(root,text("st.workspace.advanced")+(advanced?" ▴":" ▾"),"st.arena.help",()->{advanced=!advanced;rebuild();});
        if(stamp!=null)ToolUi.action(root,text("st.stamp.title"),"st.stamp.help",()->new BuildingStampDialog(stamp).show());
        if(!advanced)return;
        ToolUi.action(root,text("st.arena.shape."+shape.name().toLowerCase(java.util.Locale.ROOT)),"st.arena.help",()->{preview=null;shape=ArenaTools.Shape.values()[(shape.ordinal()+1)%ArenaTools.Shape.values().length];rebuild();});
        ToolUi.action(root,text("st.arena.layer."+layer.name().toLowerCase(java.util.Locale.ROOT)),"st.arena.help",()->{preview=null;layer=ArenaTools.Layer.values()[(layer.ordinal()+1)%ArenaTools.Layer.values().length];rebuild();});
        String[] labels={"X1","Y1","X2","Y2"};for(int i=0;i<4;i++){int index=i;ToolUi.valueField(root,labels[i],coords[i],value->{coords[index]=value;preview=null;});}
        ToolUi.action(root,text("st.arena.around-player"),"st.arena.help",()->{int x=Vars.player.tileX(),y=Vars.player.tileY();coords[0]=""+Math.max(0,x-2);coords[1]=""+Math.max(0,y-2);coords[2]=""+Math.min(Vars.world.width()-1,x+2);coords[3]=""+Math.min(Vars.world.height()-1,y+2);preview=null;rebuild();});
        ToolUi.action(root,text("st.arena.preview"),"st.arena.help",()->run(()->{calculate();rebuild();}));
        root.label(()->preview==null?"":Core.bundle.format("st.arena.count",preview.count())).wrap().row();
    }
    private void pick(int slot) {
        List<Block> content = new ArrayList<>();
        for (Block b : Vars.content.blocks()) {
            if (slot == 0 && b.isFloor() && !b.isOverlay() && b != Blocks.air
                    || slot == 1 && (b.isOverlay() || b == Blocks.air)
                    || slot == 2 && ArenaTools.environmentBlock(b)) content.add(b);
        }
        ContentPicker.icons(text("st.arena.title"), content, slot==0?floor:slot==1?overlay:block, selected -> {
            preview=null;if (slot == 0) floor = selected; else if (slot == 1) overlay = selected; else block = selected;
            rebuild();
        });
    }
    private static void run(Runnable action) {
        try { action.run(); }
        catch (NumberFormatException exception) { Vars.ui.showErrorMessage(text("st.arena.error.area")); }
        catch (RuntimeException exception) {
            String key = exception.getMessage();
            Vars.ui.showErrorMessage(Core.bundle.has(key == null ? "" : key) ? text(key) : text("st.error.input"));
        }
    }
}
