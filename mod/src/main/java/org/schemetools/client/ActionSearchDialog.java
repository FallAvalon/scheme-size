package org.schemetools.client;

import arc.Core;
import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.ui.dialogs.BaseDialog;
import java.util.List;
import java.util.Locale;

/** Search uses the same registered actions and edition/module boundaries as hotkeys. */
public final class ActionSearchDialog extends ToolDialog {
    private final ModKeys keys;
    private final Table results = new Table();
    public ActionSearchDialog(ModKeys keys) {
        super(Core.bundle.get("st.actions.title")); this.keys = keys; addCloseButton();
        cont.add(Core.bundle.get("st.search")).left().row();
        cont.field("", this::refresh).width(UiSizing.width(560f)).maxTextLength(80).row();
        cont.pane(results).width(UiSizing.width(600f)).maxHeight(UiSizing.toolHeight(.55f)).row();
        refresh("");
    }
    public List<ModKeys.Entry> matching(String query) {
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return keys.entries().stream().filter(entry -> entry.action() != ModKeys.Action.TELEPORT)
            .filter(entry -> Core.settings.getBool("schemesize-module-" + entry.action().module, true))
            .filter(entry -> Core.bundle.get(entry.action().label).toLowerCase(Locale.ROOT).contains(needle)).toList();
    }
    private void refresh(String query) {
        results.clearChildren();
        var entries = matching(query);
        if (entries.isEmpty()) results.add(Core.bundle.get("st.actions.empty")).wrap().width(UiSizing.width(560f)).row();
        for (var entry : entries) results.button(Core.bundle.get(entry.action().label) + "  ·  " + ModKeys.display(entry), () -> {
            if (!Vars.state.isGame() || !Core.settings.getBool("schemesize-module-" + entry.action().module, true)) return;
            hide();
            try { entry.run().run(); }
            catch (RuntimeException failure) { Vars.ui.showErrorMessage(Core.bundle.get(failure.getMessage() == null ? "st.error.input" : failure.getMessage(), Core.bundle.get("st.error.input"))); }
        }).width(UiSizing.width(560f)).height(48f).disabled(!Vars.state.isGame()).row();
    }
}
