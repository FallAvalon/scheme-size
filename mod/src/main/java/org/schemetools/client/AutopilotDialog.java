package org.schemetools.client;

import arc.Core;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.type.Item;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Explicit mode and target selection; AI is never started automatically on load. */
public final class AutopilotDialog extends ToolDialog {
    private final LocalAutopilot ai;
    private final Runnable onStart;
    private LocalAutopilot.Mode mode = LocalAutopilot.Mode.MINE;
    private Item item;
    private Block block;
    private String targetId, x = "0", y = "0", radius;
    public AutopilotDialog(LocalAutopilot ai, Runnable onStart) {
        super(text("st.ai.title")); this.ai = ai; this.onStart = onStart;
        item = Vars.content.item(Core.settings.getString("schemesize-ai-item", ""));
        radius = "" + Core.settings.getFloat("schemesize-ai-radius", 10f);
        if (Vars.state.isGame()) { x = "" + Vars.player.tileX(); y = "" + Vars.player.tileY(); }
        addCloseButton(); shown(this::rebuild);
    }
    private static String text(String key) { return Core.bundle.get(key); }
    private Player target() {
        for (Player player : Groups.player.copy()) if (Objects.equals(org.schemetools.shared.PlayerKey.of(player), targetId)) return player;
        return null;
    }
    private void nextPlayer(int direction) {
        List<Player> players = new ArrayList<>();
        for (Player player : Groups.player.copy()) if (player != Vars.player && player.team() == Vars.player.team()) players.add(player);
        if (players.isEmpty()) { targetId = null; rebuild(); return; }
        int index = -1;
        for (int i = 0; i < players.size(); i++) if (Objects.equals(org.schemetools.shared.PlayerKey.of(players.get(i)), targetId)) index = i;
        targetId = org.schemetools.shared.PlayerKey.of(players.get(Math.floorMod(index + direction, players.size()))); rebuild();
    }
    private void rebuild() {
        cont.clear();
        cont.pane(root -> {
            root.defaults().pad(4f).left();
            root.add(text("st.ai.help")).wrap().width(UiSizing.width(560f)).row();
            root.label(() -> text("st.ai.current") + ": " + text("st.ai.mode." + ai.mode().name().toLowerCase(Locale.ROOT)) + " — " + ai.status())
                .wrap().width(UiSizing.width(560f)).row();
            root.button(text("st.ai.mode." + mode.name().toLowerCase(Locale.ROOT)), () -> {
                int index = (mode.ordinal() + 1) % LocalAutopilot.Mode.values().length;
                mode = LocalAutopilot.Mode.values()[index == 0 ? 1 : index]; rebuild();
            }).width(UiSizing.width(560f)).row();
            if (mode == LocalAutopilot.Mode.MINE) root.button(text("st.ai.item") + ": " + (item == null ? text("st.ai.auto-item") : item.localizedName), () -> {
                List<Item> items = new ArrayList<>(); for (Item value : Vars.content.items()) items.add(value);
                ContentPicker.show(text("st.ai.item"), items, true, value -> { item = value; rebuild(); });
            }).width(UiSizing.width(560f)).row();
            if (mode == LocalAutopilot.Mode.FOLLOW || mode == LocalAutopilot.Mode.ASSIST) root.table(row -> {
                Player player = target();
                row.add(text("st.player") + ": " + (player == null ? text("st.no-player") : Strings.stripColors(player.name))).width(UiSizing.width(380f)).left();
                row.button("‹", () -> nextPlayer(-1)).size(44f); row.button("›", () -> nextPlayer(1)).size(44f);
            }).row();
            if (mode == LocalAutopilot.Mode.CIRCLE) {
                root.table(row -> {
                    row.add("X"); row.field(x, value -> x = value).width(UiSizing.width(100f)).maxTextLength(6);
                    row.add("Y"); row.field(y, value -> y = value).width(UiSizing.width(100f)).maxTextLength(6);
                    row.add(text("st.ai.radius")); row.field(radius, value -> radius = value).width(UiSizing.width(100f)).maxTextLength(8);
                }).row();
            }
            if (mode == LocalAutopilot.Mode.DEMOLISH) root.button(text("st.ai.block") + ": " + (block == null ? text("st.none") : block.localizedName), () -> {
                List<Block> blocks = new ArrayList<>(); for (Block value : Vars.content.blocks()) if (!value.privileged) blocks.add(value);
                ContentPicker.show(text("st.ai.block"), blocks, false, value -> { block = value; rebuild(); });
            }).width(UiSizing.width(560f)).row();
            root.button(text("st.ai.start"), () -> {
                String confirm = mode == LocalAutopilot.Mode.DEMOLISH ? text("st.ai.confirm-demolish") : text("st.ai.confirm-start");
                Vars.ui.showConfirm(text("st.ai.title"), confirm, () -> {
                    try {
                        int tx = Integer.parseInt(x.trim()), ty = Integer.parseInt(y.trim());
                        if (Vars.world.tile(tx, ty) == null) throw new IllegalArgumentException("st.ai.error.settings");
                        ai.start(mode, item, target(), tx * Vars.tilesize, ty * Vars.tilesize,
                            Float.parseFloat(radius.trim()) * Vars.tilesize, block);
                        hide(); onStart.run();
                    } catch (RuntimeException failure) {
                        String key = failure.getMessage();
                        Vars.ui.showErrorMessage(key != null && Core.bundle.has(key) ? text(key) : text("st.ai.error.settings"));
                    }
                });
            }).disabled(!Vars.state.isGame()).row();
            root.button(text("st.ai.stop"), () -> { ai.stop(); rebuild(); }).row();
        }).width(UiSizing.width(600f)).maxHeight(UiSizing.toolHeight(0.7f));
    }
}
