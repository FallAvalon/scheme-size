package org.schemetools.client;

import arc.Core;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.blocks.storage.CoreBlock;
import org.schemetools.host.BuildingStamp;

public final class BuildingStampDialog extends ToolDialog {
    private final BuildingStamp tools;
    private Block block=Blocks.copperWall;
    private int teamIndex,rotation;
    private final String[] area={"0","0","5","5"};
    private BuildingStamp.Preview preview;
    private boolean advanced;
    public BuildingStampDialog(BuildingStamp tools){
        super(text("st.stamp.title"));this.tools=tools;addCloseButton();
        if(Vars.state.isGame()){area[0]=""+Vars.player.tileX();area[1]=""+Vars.player.tileY();}
        shown(this::rebuild);
    }
    private static String text(String key){return Core.bundle.get(key);}
    private Team team(){return Team.baseTeams[1+Math.floorMod(teamIndex,Team.baseTeams.length-1)];}
    @Override public boolean compactContent(){return true;}
    @Override public void refreshWidth(){rebuild();}
    private void rebuild(){
        var root=ToolUi.content(this);
        ToolUi.action(root,text("st.selection.title"),"st.selection.title.help",()->new AreaSelectionDialog(512,selected->{area[0]=""+selected.x1();area[1]=""+selected.y1();area[2]=""+(selected.x2()-selected.x1()+1);area[3]=""+(selected.y2()-selected.y1()+1);preview=null;rebuild();}).show());
        ToolUi.action(root,block.localizedName,"st.stamp.help",()->{
            var blocks=new java.util.ArrayList<Block>();for(Block value:Vars.content.blocks())if(value.hasBuilding()&&!value.isFloor()&&(tools.godMode()||(!(value instanceof CoreBlock)&&!value.privileged&&!value.isBanned()&&value.isVisible()&&value.supportsEnv(Vars.state.rules.env))))blocks.add(value);
            ContentPicker.icons(text("st.build.block"),blocks,block,selected->{block=selected;preview=null;rebuild();});
        });
        ToolUi.action(root,team().localized(),"st.stamp.help",()->{teamIndex++;preview=null;rebuild();});
        ToolUi.action(root,text("st.arena.preview"),"st.stamp.help",()->run(()->{preview=tools.preview(Integer.parseInt(area[0]),Integer.parseInt(area[1]),Integer.parseInt(area[2]),Integer.parseInt(area[3]),block,team(),rotation);rebuild();}));
        root.label(()->preview==null?"":Core.bundle.format("st.arena.count",preview.count())).wrap().row();
        var apply=ToolUi.action(root,text("st.apply"),"st.stamp.help",()->{
            var pending=preview;if(pending==null)return;
            Vars.ui.showConfirm(text("st.stamp.title"),Core.bundle.format("st.stamp.confirm",pending.count(),pending.replacements()),()->run(()->{tools.apply(pending);preview=null;rebuild();Vars.ui.showInfoFade(text("st.success"));}));
        });apply.update(()->apply.setDisabled(preview==null||Vars.net.client()||!Vars.state.isGame()));
        ToolUi.action(root,text("st.arena.undo"),"st.help.god-undo",()->run(()->{tools.undo();preview=null;rebuild();Vars.ui.showInfoFade(text("st.success"));}));
        ToolUi.action(root,text("st.workspace.advanced")+(advanced?" ▴":" ▾"),"st.stamp.help",()->{advanced=!advanced;rebuild();});
        if(advanced){
            ToolUi.action(root,Core.bundle.format("st.build.rotation",rotation),"st.help.build-rotation",()->{rotation=(rotation+1)%4;preview=null;rebuild();});
            String[] labels={"X","Y","W","H"};for(int i=0;i<4;i++){int index=i;ToolUi.valueField(root,labels[i],area[i],value->{area[index]=value;preview=null;});}
        }
    }
    private void run(Runnable action){try{action.run();}catch(RuntimeException failure){Vars.ui.showErrorMessage(Core.bundle.get(failure.getMessage()==null?"":failure.getMessage(),text("st.stamp.error")));}}
}
