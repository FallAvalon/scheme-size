package org.schemetools.client;

import arc.Core;
import arc.scene.ui.layout.Table;
import arc.util.Strings;
import mindustry.ctype.UnlockableContent;
import mindustry.ui.dialogs.BaseDialog;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Searchable picker over the running game's content, without assuming numeric IDs. */
public final class ContentPicker {
    private ContentPicker() { }

    /** Same native icon widgets as the game selector, without planet/unlock filtering in God. */
    public static <T extends UnlockableContent> void icons(String title,List<T> content,T current,Consumer<T> selected){
        BaseDialog dialog=new BaseDialog(title);dialog.addCloseButton();
        float width=Math.min(440,Core.scene.getWidth()/UiSizing.scale()-80);
        int columns=Math.max(2,(int)(width/48));Table grid=new Table();String[] query={""};
        Runnable refresh=()->{
            grid.clearChildren();int index=0;
            for(T value:content){
                if(!(value.localizedName+" "+value.name).toLowerCase(Locale.ROOT).contains(query[0].toLowerCase(Locale.ROOT)))continue;
                var button=grid.button(new arc.scene.style.TextureRegionDrawable(value.uiIcon),mindustry.ui.Styles.clearTogglei,32,()->{selected.accept(value);dialog.hide();}).size(48).tooltip(value.localizedName).get();
                button.name="st-content-"+value.name;button.setChecked(value==current);
                if(++index%columns==0)grid.row();
            }
        };
        dialog.cont.field("",v->{query[0]=v;refresh.run();}).width(width).height(40).get().setMessageText(Core.bundle.get("st.search"));dialog.cont.row();
        dialog.cont.pane(grid).width(width).maxHeight(UiSizing.height()*.55f).scrollX(false);refresh.run();dialog.show();
    }

    public static <T extends UnlockableContent> void show(String title, List<T> content, boolean allowNone, Consumer<T> selected) {
        BaseDialog dialog = new BaseDialog(title);
        dialog.addCloseButton();
        Table list = new Table();
        Runnable[] refresh = new Runnable[1];
        String[] query = {""};
        refresh[0] = () -> {
            list.clearChildren();
            if (allowNone) list.button(Core.bundle.get("st.none"), () -> { selected.accept(null); dialog.hide(); })
                .width(UiSizing.width(360f)).height(44f).row();
            for (T value : content) {
                String text = Strings.stripColors(value.localizedName) + " (" + value.name + ")";
                if (!text.toLowerCase(Locale.ROOT).contains(query[0].toLowerCase(Locale.ROOT))) continue;
                list.button(text, () -> { selected.accept(value); dialog.hide(); }).width(UiSizing.width(360f)).height(44f).row();
            }
        };
        dialog.cont.field("", value -> { query[0] = value; refresh[0].run(); }).width(UiSizing.width(360f)).row();
        dialog.cont.pane(list).width(UiSizing.width(390f)).maxHeight(UiSizing.height() * 0.6f).row();
        refresh[0].run();
        dialog.show();
    }
}
