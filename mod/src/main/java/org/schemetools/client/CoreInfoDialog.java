package org.schemetools.client;

import arc.Core;
import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.type.Item;
import mindustry.ui.dialogs.BaseDialog;

import java.util.Locale;

/** Read-only core stock and observed change, calculated from snapshots on this client. */
public final class CoreInfoDialog extends ToolDialog {
    private int teamIndex = 1;
    private long sampleNanos;
    private int[] previous;
    private double[] rates;
    private Table values;

    public CoreInfoDialog() {
        super(Core.bundle.get("st.coreinfo.title"));
        addCloseButton();
        shown(() -> { resetSample(); rebuild(); });
    }

    private Team team() { return Team.baseTeams[Math.floorMod(teamIndex, Team.baseTeams.length)]; }

    private void resetSample() { previous = null; rates = null; sampleNanos = 0; }

    private void rebuild() {
        cont.clear();
        cont.table(row -> {
            row.add(Core.bundle.get("st.team") + ": " + team().localized()).width(UiSizing.width(300f)).left();
            row.button("‹", () -> { teamIndex--; resetSample(); rebuild(); }).size(44f);
            row.button("›", () -> { teamIndex++; resetSample(); rebuild(); }).size(44f);
        }).row();
        cont.add(Core.bundle.get("st.coreinfo.hint")).wrap().width(UiSizing.width(540f)).left().row();
        values = new Table();
        cont.pane(values).width(UiSizing.width(570f)).maxHeight(390f).row();
        values.update(() -> {
            long now = System.nanoTime();
            if (sampleNanos == 0 || now - sampleNanos >= 1_000_000_000L) sample(now);
        });
        sample(System.nanoTime());
    }

    private void sample(long now) {
        if (!Vars.state.isGame() || team().core() == null || team().core().items == null) {
            resetSample();
            values.clearChildren();
            values.add(Core.bundle.get("st.core.none")).left().row();
            sampleNanos = now;
            return;
        }
        var items = Vars.content.items();
        int[] current = new int[items.size];
        double elapsed = (now - sampleNanos) / 1_000_000_000d;
        boolean comparable = previous != null && previous.length == current.length && elapsed > 0;
        rates = new double[current.length];
        for (int i = 0; i < items.size; i++) {
            current[i] = team().core().items.get(items.get(i));
            if (comparable) rates[i] = (current[i] - (long)previous[i]) / elapsed;
        }
        previous = current;
        sampleNanos = now;
        values.clearChildren();
        boolean any = false;
        for (int i = 0; i < items.size; i++) {
            if (current[i] == 0 && rates[i] == 0) continue;
            Item item = items.get(i);
            values.image(item.uiIcon).size(28f).pad(4f);
            values.add(item.localizedName).width(UiSizing.width(210f)).left();
            values.add(Integer.toString(current[i])).width(UiSizing.width(110f)).right();
            values.add(comparable ? String.format(Locale.ROOT, "%+.1f/s", rates[i]) : "—")
                .width(UiSizing.width(110f)).right().row();
            any = true;
        }
        if (!any) values.add(Core.bundle.get("st.coreinfo.empty")).left().row();
        values.invalidateHierarchy();
    }
}
