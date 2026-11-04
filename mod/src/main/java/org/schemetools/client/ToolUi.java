package org.schemetools.client;

import arc.Core;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.scene.ui.Dialog;
import mindustry.ui.Styles;

/** Bounded single-column controls; explanations never compete with the map. */
final class ToolUi {
    private ToolUi(){}
    private static boolean hosted(){return ToolWorkspace.active!=null&&mindustry.Vars.state!=null&&mindustry.Vars.state.isGame();}
    static float width(){return !hosted()?Math.min(460,Core.scene.getWidth()/UiSizing.scale()-80):ToolWorkspace.contentWidth()-56;}
    static Table content(ToolDialog dialog){
        dialog.cont.clear();dialog.cont.margin(0);
        Table root=new Table();root.top().left();root.defaults().width(width()).padBottom(6).left();
        if(!hosted())dialog.cont.pane(root).width(width()+32*UiSizing.scale()).maxHeight(UiSizing.height()*.7f).scrollX(false);
        else dialog.cont.add(root).width(width()).top();
        return root;
    }
    static void help(String title,String key){
        helpText(title,Core.bundle.get(key));
    }
    static void helpText(String title,String text){
        Dialog dialog=new Dialog(title,Styles.defaultDialog);
        dialog.closeOnBack();
        float width=Math.min(440,Core.scene.getWidth()/UiSizing.scale()-90);
        dialog.cont.pane(body->body.add(text).wrap().width(width)).width(width+32*UiSizing.scale()).maxHeight(UiSizing.height()*.6f).scrollX(false).pad(12);
        dialog.buttons.button(Core.bundle.get("st.help.close"),dialog::hide).width(140).height(44);
        dialog.show();
    }
    static void helpButton(Table row,String title,String key){
        row.button("?",()->help(title,key)).size(40,40).padLeft(4).tooltip(Core.bundle.get("st.help.read"));
    }
    static TextButton action(Table root,String label,String helpKey,Runnable run){
        TextButton[] result={null};
        root.table(row->{
            result[0]=row.button(label,run).width(width()-44).minHeight(44).get();
            result[0].getLabel().setWrap(true);
            helpButton(row,label,helpKey);
        }).row();
        return result[0];
    }
    static void wrapButtons(Table root){
        for(var cell:root.getCells())if(cell.get() instanceof TextButton button){cell.width(width()).minHeight(44);button.getLabel().setWrap(true);}
    }
    static void field(Table root,String label,String value,arc.func.Cons<String> changed){
        root.field(value,changed).width(width()).height(40).get().setMessageText(label);root.row();
    }
    static void valueField(Table root,String label,String value,arc.func.Cons<String> changed){
        root.add(label).wrap().left().row();field(root,label,value,changed);
    }
}
