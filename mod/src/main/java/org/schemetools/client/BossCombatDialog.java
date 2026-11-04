package org.schemetools.client;

import arc.Core;
import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.ctype.ContentType;
import mindustry.type.StatusEffect;
import mindustry.ui.dialogs.BaseDialog;
import org.schemetools.event.BossCombatSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Edits a detached configuration; only Apply replaces the panel's current settings. */
public final class BossCombatDialog extends ToolDialog {
    private final Consumer<BossCombatSettings> save;
    private String unit, bossEffect, hunterEffect, item;
    private final String[] numbers;
    private boolean elimination, restore;

    public BossCombatDialog(BossCombatSettings value, Consumer<BossCombatSettings> save) {
        super(text("st.boss.combat"));
        this.save = save;
        unit = value.bossUnit(); bossEffect = value.bossEffect(); hunterEffect = value.hunterEffect(); item = value.startingItem();
        numbers = new String[]{"" + value.bossHealth(), "" + value.bossDamage(), "" + value.hunterHealth(),
            "" + value.hunterDamage(), "" + value.bossItems(), "" + value.hunterItems(), "" + value.respawnDelay()};
        elimination = value.huntersEliminatedWins(); restore = value.restoreTeams();
        addCloseButton();
        buttons.button(text("st.apply"), this::apply);
        rebuild();
    }

    private static String text(String key) { return Core.bundle.get(key); }

    private void rebuild() {
        cont.clear();
        cont.pane(root -> {
            root.defaults().pad(4f).left();
            root.add(text("st.boss.combat-help")).wrap().width(UiSizing.width(560f)).row();
            root.button(text("st.boss.unit") + ": " + unitLabel(), () -> {
                var units = new ArrayList<mindustry.type.UnitType>();
                for (var type : Vars.content.units()) if (!type.isHidden() && !type.isBanned() && type.playerControllable
                        && type.segmentUnits <= 1 && type.supportsEnv(Vars.state.rules.env)) units.add(type);
                ContentPicker.show(text("st.boss.unit"), units, true, selected -> { unit = selected == null ? "" : selected.name; rebuild(); });
            }).width(UiSizing.width(560f)).row();
            effectButton(root, true); effectButton(root, false);
            String[] keys = {"st.boss.health", "st.boss.damage", "st.boss.hunter-health", "st.boss.hunter-damage",
                "st.boss.items", "st.boss.hunter-items", "st.boss.respawn-delay"};
            for (int i = 0; i < numbers.length; i++) {
                final int index = i;
                root.table(row -> {
                    row.add(text(keys[index])).width(UiSizing.width(370f)).left();
                    row.field(numbers[index], v -> numbers[index] = v.length() > 12 ? v.substring(0, 12) : v).width(UiSizing.width(150f));
                }).row();
            }
            root.button(text("st.boss.starting-item") + ": " + item, () -> {
                List<mindustry.type.Item> items = new ArrayList<>();
                for (var value : Vars.content.items()) items.add(value);
                ContentPicker.show(text("st.boss.starting-item"), items, true, selected -> { item = selected == null ? "" : selected.name; rebuild(); });
            }).width(UiSizing.width(560f)).row();
            root.check(text("st.boss.elimination"), elimination, v -> elimination = v).row();
            root.check(text("st.boss.restore-teams"), restore, v -> restore = v).row();
            root.add(text("st.boss.restore-help")).wrap().width(UiSizing.width(560f)).row();
        }).maxHeight(UiSizing.toolHeight(0.65f)).width(UiSizing.width(600f));
    }

    private String unitLabel() {
        var type = Vars.content.unit(unit);
        return type == null ? text("st.boss.keep-unit") : type.localizedName;
    }

    private void effectButton(Table root, boolean boss) {
        String key = boss ? "st.boss.effect" : "st.boss.hunter-effect";
        String name = boss ? bossEffect : hunterEffect;
        StatusEffect current = Vars.content.getByName(ContentType.status, name);
        root.button(text(key) + ": " + (current == null ? text("st.none") : current.localizedName), () -> {
            List<StatusEffect> effects = new ArrayList<>();
            for (var entry : Vars.content.<StatusEffect>getBy(ContentType.status))
                if (!entry.isHidden() && !entry.dynamic && !entry.reactive && !entry.permanent) effects.add(entry);
            ContentPicker.show(text(key), effects, true, selected -> {
                if (boss) bossEffect = selected == null ? "" : selected.name;
                else hunterEffect = selected == null ? "" : selected.name;
                rebuild();
            });
        }).width(UiSizing.width(560f)).row();
    }

    private void apply() {
        try {
            BossCombatSettings result = new BossCombatSettings(unit, bossEffect, hunterEffect,
                Double.parseDouble(numbers[0]), Double.parseDouble(numbers[1]), Double.parseDouble(numbers[2]),
                Double.parseDouble(numbers[3]), item, Integer.parseInt(numbers[4]), Integer.parseInt(numbers[5]),
                Double.parseDouble(numbers[6]), elimination, restore);
            save.accept(result);
            hide();
        } catch (IllegalArgumentException exception) { Vars.ui.showErrorMessage(text("st.boss.error.settings")); }
    }
}
