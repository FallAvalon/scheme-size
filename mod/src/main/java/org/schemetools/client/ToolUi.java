package org.schemetools.client;

import arc.Core;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.scene.ui.Dialog;
import mindustry.ui.Styles;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.scene.style.BaseDrawable;
import arc.scene.style.Drawable;

/** Bounded single-column controls; explanations never compete with the map. */
final class ToolUi {
    private ToolUi(){}
    static void confirmAction(String title,String prompt,Runnable action){
        if(Core.settings.getBool("schemesize-confirm-actions",false))mindustry.Vars.ui.showConfirm(title,prompt,action);
        else action.run();
    }
    /** Native backgrounds with a small outline; no generated UI textures. */
    private static final class Frame extends BaseDrawable {
        final Drawable background; final Color color;
        Frame(Drawable background,Color color){this.background=background;this.color=color;}
        @Override public void draw(float x,float y,float width,float height){
            float alpha=Draw.getColor().a;background.draw(x,y,width,height);
            Draw.color(color,alpha);Lines.stroke(1.5f);Lines.rect(x+1,y+1,width-2,height-2);Draw.reset();
        }
    }
    static TextButton.TextButtonStyle toggleStyle(){
        var style=new TextButton.TextButtonStyle(Styles.togglet);
        style.checked=tinted(Tex.buttonSelect,Pal.accent);style.checkedFontColor=Pal.accent;
        return style;
    }
    private static Drawable tinted(Drawable background,Color color){
        if(background instanceof arc.scene.style.NinePatchDrawable patch)return patch.tint(color);
        if(background instanceof arc.scene.style.TextureRegionDrawable region)return region.tint(color);
        return new Frame(background,color);
    }
    static void emphasize(TextButton button,boolean dangerous){
        var style=new TextButton.TextButtonStyle(button.getStyle());
        Color color=dangerous?Color.scarlet:Pal.accent;
        style.up=tinted(Tex.button,color);style.over=tinted(Tex.buttonOver,color);
        style.fontColor=color;button.setStyle(style);
    }
    static void heading(Table root,String title,String help){
        root.table(row->{
            row.add(title).color(Pal.accent).left().growX().minWidth(0);
            if(help!=null)row.button("?",()->help(title,help)).size(28).padLeft(4).tooltip(Core.bundle.get("st.help.read"));
        }).minHeight(34).row();
    }
    static void tabs(Table root,String first,String second,boolean right,Runnable leftAction,Runnable rightAction){
        boolean narrow=width()<240;float w=narrow?width():(width()-4)/2;
        root.table(row->{
            var a=row.button(first,toggleStyle(),leftAction).width(w).minHeight(42).get();a.setChecked(!right);a.getLabel().setEllipsis(true);
            if(narrow)row.row();else row.add().width(4);
            var b=row.button(second,toggleStyle(),rightAction).width(w).minHeight(42).get();b.setChecked(right);b.getLabel().setEllipsis(true);
        }).row();
    }
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
        dialog.name="st-modal";
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
