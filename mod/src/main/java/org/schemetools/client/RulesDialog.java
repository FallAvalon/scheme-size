package org.schemetools.client;

import arc.Core;
import mindustry.Vars;
import mindustry.ui.dialogs.BaseDialog;
import org.schemetools.host.HostActions;
import org.schemetools.Edition;

import java.util.Locale;

/** Every submit invokes the authoritative API again after confirmation. */
public final class RulesDialog extends ToolDialog {
    private final HostActions actions;
    private final boolean extended;
    public RulesDialog(HostActions actions, Edition edition) {
        super(Core.bundle.get("st.rules")); this.actions = actions; extended = edition.events;
        addCloseButton(); shown(this::rebuild);
    }
    private static String text(String key) { return Core.bundle.get(key); }
    private void rebuild() {
        cont.clear();
        if (!actions.hasGame()) { cont.add(text("st.no-game")); return; }
        cont.pane(root -> {
            root.defaults().left().pad(4);
            root.add(text("st.rules-live-help")).wrap().width(UiSizing.width(580)).row();
            for (HostActions.BooleanRule key : HostActions.BooleanRule.values()) {
                if (!extended && key.ordinal() > HostActions.BooleanRule.WAVE_TIMER.ordinal()) continue;
                String label = text("st.rule." + key.name().toLowerCase(Locale.ROOT));
                root.button(label + ": " + text(key.get(Vars.state.rules) ? "st.on" : "st.off"), () -> {
                    boolean next = !key.get(Vars.state.rules);
                    confirm(label, () -> actions.rule(key, next));
                }).width(UiSizing.width(560)).disabled(!actions.isAvailable()).row();
            }
            if (extended) for (HostActions.NumericRule key : HostActions.NumericRule.values()) {
                String label = text("st.rule." + key.name().toLowerCase(Locale.ROOT));
                String[] value = {Float.toString(key.get(Vars.state.rules))};
                root.table(row -> {
                    row.add(label).width(UiSizing.width(280)).left();
                    row.field(value[0], input -> value[0] = input).width(UiSizing.width(100)).maxTextLength(10);
                    row.button(text("st.apply"), () -> confirm(label + " = " + value[0], () -> actions.rule(key, Float.parseFloat(value[0]))))
                        .disabled(!actions.isAvailable());
                }).row();
                root.add(Core.bundle.format("st.rule.range", key.min, key.max)).row();
            }
        }).width(UiSizing.width(620)).maxHeight(UiSizing.toolHeight(0.7f));
    }
    private void confirm(String detail, Runnable action) {
        Runnable guarded=WorldGuard.capture(action);
        Vars.ui.showConfirm(text("st.rules"), text("st.rules-live-warning") + "\n" + detail, () -> {
            try { guarded.run(); rebuild(); Vars.ui.showInfoFade(text("st.rules-applied")); }
            catch (RuntimeException failure) { Vars.ui.showErrorMessage(Core.bundle.get(failure.getMessage() == null ? "" : failure.getMessage(), text("st.error.input"))); }
        });
    }
}
