package org.schemetools.client;

import arc.Core;
import mindustry.Vars;
import mindustry.game.Schematic;
import mindustry.ui.dialogs.BaseDialog;
import org.schemetools.schematic.SchematicStore;
import org.schemetools.schematic.SchematicTools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A tag filter shared by the native library and this mod's safely stored copies. */
public final class QuickSchematicsDialog extends ToolDialog {
    private final SchematicStore store;
    private final List<Schematic> library = new ArrayList<>();
    private String tag = Core.settings.getString("schemesize-quick-tag", "");
    private String search = "";
    private arc.scene.ui.layout.Table list;
    private boolean advanced;
    @Override public boolean compactContent(){return true;}
    @Override public void refreshWidth(){rebuild();}
    public QuickSchematicsDialog(SchematicStore store) {
        super(Core.bundle.get("st.quick-schematics")); this.store = store;
        addCloseButton(); shown(this::rebuild);
    }
    public static boolean matches(Schematic schematic, String tag, String search) {
        String query = arc.util.Strings.stripColors(search).toLowerCase(Locale.ROOT);
        return arc.util.Strings.stripColors(schematic.tags.get("name", "")).toLowerCase(Locale.ROOT).contains(query)
            && (tag.isBlank() || schematic.labels.contains(label -> label.equalsIgnoreCase(tag.trim())));
    }
    private void rebuild() {
        library.clear();
        for (var schematic : Vars.schematics.all()) library.add(schematic);
        try { for (var entry : store.list().entries()) library.add(entry.schematic()); }
        catch (IOException failure) { Vars.ui.showInfoFade(Core.bundle.get("st.schematic.error.file")); }
        var root=ToolUi.content(this);
        root.add(Core.bundle.get("st.quick.hold-hint")).wrap().row();
        root.table(t->{for(int i=0;i<6;i++){final int slot=i;String value=HeldQuickMenu.tag(i);var b=t.button((i+1)+": "+(value.isBlank()?"+":value),()->HeldQuickMenu.configure(slot,store)).width((ToolUi.width()-4)/2).minHeight(40).pad(2).get();b.getLabel().setEllipsis(true);if(i%2==1)t.row();}}).row();
        ToolUi.valueField(root,Core.bundle.get("st.quick.size"),""+HeldQuickMenu.sizePercent(),v->{try{int size=Integer.parseInt(v);if(size>=70&&size<=130)Core.settings.put("schemesize-quick-size",size);}catch(NumberFormatException ignored){}});
        ToolUi.action(root,Core.bundle.get("st.quick.library"),"st.quick-schematics-help",()->{advanced=!advanced;rebuild();});
        if(!advanced)return;
        ToolUi.field(root,Core.bundle.get("st.schematic.tag"),tag,value->{tag=value;Core.settings.put("schemesize-quick-tag",value);refill();});
        ToolUi.field(root,Core.bundle.get("st.search"),search,value->{search=value;refill();});
        list = new arc.scene.ui.layout.Table(); root.pane(list).width(ToolUi.width()).maxHeight(UiSizing.toolHeight(0.5f)).scrollX(false).row(); refill();
    }
    private void refill() {
        if (list == null) return;
        list.clearChildren(); int count = 0;
        for (var schematic : library) if (matches(schematic, tag, search) && !SchematicTools.terrain(schematic)) {
            count++;
            list.button(schematic.tags.get("name", Core.bundle.get("st.schematic.unnamed")) + "  " + schematic.width + "×" + schematic.height, () -> {
                try { SchematicTools.use(schematic); hide(); }
                catch (IOException failure) { Vars.ui.showErrorMessage(Core.bundle.get(failure.getMessage(), Core.bundle.get("st.schematic.error.data"))); }
            }).width(ToolUi.width()-24).minHeight(46).get().name = "scheme-size-quick-entry";
            list.row();
        }
        if (count == 0) list.add(Core.bundle.get("st.schematic.no-matches")).row();
    }
}
