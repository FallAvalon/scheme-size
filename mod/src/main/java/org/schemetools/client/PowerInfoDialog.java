package org.schemetools.client;

import arc.Core;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.ui.dialogs.BaseDialog;

import java.util.Locale;

/** Read-only native power graph values for a selected building. */
public final class PowerInfoDialog extends ToolDialog {
    public record Snapshot(float produced, float needed, float stored, float capacity, float satisfaction) { }
    private String x = "0", y = "0";
    private Building selected;
    public PowerInfoDialog() {
        super(Core.bundle.get("st.power.title"));
        if (Vars.state.isGame()) { x = "" + Vars.player.tileX(); y = "" + Vars.player.tileY(); }
        addCloseButton();
        rebuild();
    }
    public static Snapshot inspect(Building building) {
        if (building == null || building.power == null || building.power.graph == null || !building.isValid())
            throw new IllegalArgumentException("st.power.error.node");
        var graph = building.power.graph;
        return new Snapshot(graph.getLastPowerProduced() * 60, graph.getLastPowerNeeded() * 60,
            graph.getLastPowerStored(), graph.getLastCapacity(), graph.getSatisfaction());
    }
    private void rebuild() {
        cont.clear();
        cont.add(Core.bundle.get("st.power.help")).wrap().width(UiSizing.width(540f)).row();
        cont.table(row -> {
            row.add("X"); row.field(x, value -> x = value).width(UiSizing.width(100f)).maxTextLength(6);
            row.add("Y"); row.field(y, value -> y = value).width(UiSizing.width(100f)).maxTextLength(6);
            row.button(Core.bundle.get("st.power.select"), () -> {
                try {
                    Building building = Vars.world.build(Integer.parseInt(x.trim()), Integer.parseInt(y.trim()));
                    if (Vars.net.client() && building != null && (!building.isDiscovered(Vars.player.team()) || Vars.state.rules.fog
                            && building.team != Vars.player.team() && !Vars.fogControl.isVisibleTile(Vars.player.team(), building.tileX(), building.tileY())))
                        throw new IllegalArgumentException();
                    inspect(building); selected = building;
                } catch (RuntimeException failure) { selected = null; Vars.ui.showErrorMessage(Core.bundle.get("st.power.error.node")); }
            });
        }).row();
        cont.label(() -> {
            try {
                Snapshot data = inspect(selected);
                return Core.bundle.format("st.power.values", selected.block.localizedName,
                    number(data.produced), number(data.needed), number(data.stored), number(data.capacity), number(data.satisfaction * 100));
            } catch (RuntimeException failure) { return Core.bundle.get("st.power.none"); }
        }).wrap().width(UiSizing.width(540f)).left().row();
    }
    private static String number(float value) { return Float.isFinite(value) ? String.format(Locale.ROOT, "%.1f", value) : "—"; }
}
