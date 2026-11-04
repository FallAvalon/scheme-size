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
        cont.clear();
        cont.add(Core.bundle.get("st.quick-schematics-help")).wrap().width(UiSizing.width(560)).row();
        cont.table(row -> {
            row.add(Core.bundle.get("st.schematic.tag"));
            row.field(tag, value -> { tag = value; Core.settings.put("schemesize-quick-tag", value); refill(); }).width(UiSizing.width(180)).maxTextLength(64);
            row.add(Core.bundle.get("st.search")); row.field(search, value -> { search = value; refill(); }).width(UiSizing.width(180)).maxTextLength(80);
        }).row();
        list = new arc.scene.ui.layout.Table(); cont.pane(list).width(UiSizing.width(600)).maxHeight(UiSizing.toolHeight(0.6f)).row(); refill();
    }
    private void refill() {
        if (list == null) return;
        list.clearChildren(); int count = 0;
        for (var schematic : library) if (matches(schematic, tag, search) && !SchematicTools.terrain(schematic)) {
            count++;
            list.button(schematic.tags.get("name", Core.bundle.get("st.schematic.unnamed")) + "  " + schematic.width + "×" + schematic.height, () -> {
                try { SchematicTools.use(schematic); hide(); }
                catch (IOException failure) { Vars.ui.showErrorMessage(Core.bundle.get(failure.getMessage(), Core.bundle.get("st.schematic.error.data"))); }
            }).width(UiSizing.width(570)).height(46).get().name = "scheme-size-quick-entry";
            list.row();
        }
        if (count == 0) list.add(Core.bundle.get("st.schematic.no-matches")).row();
    }
}
