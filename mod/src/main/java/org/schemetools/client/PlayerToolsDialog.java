package org.schemetools.client;

import arc.Core;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.ctype.ContentType;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Packets.AdminAction;
import mindustry.type.StatusEffect;
import mindustry.type.UnitType;
import mindustry.ui.dialogs.BaseDialog;
import org.schemetools.host.HostActions;

import java.util.ArrayList;
import java.util.List;

/** Stable-identity player tools: local viewing is separated from host mutations. */
public final class PlayerToolsDialog extends ToolDialog {
    private final HostActions actions;
    private final String uuid;
    private String x, y, seconds = "60";
    private UnitType type;
    private StatusEffect effect;
    private int tab;
    @Override public boolean compactContent(){return true;}
    @Override public void refreshWidth(){rebuild();}
    public PlayerToolsDialog(HostActions actions, Player target) {
        super(Core.bundle.get("st.player-tools"));
        this.actions = actions;
        uuid = org.schemetools.shared.PlayerKey.of(target);
        x = "" + target.tileX(); y = "" + target.tileY();
        type = target.dead() ? null : target.unit().type;
        addCloseButton();
        shown(this::rebuild);
    }
    private static String text(String key) { return Core.bundle.get(key); }
    private Player target() {
        for (Player player : Groups.player.copy()) if (java.util.Objects.equals(uuid, org.schemetools.shared.PlayerKey.of(player))) return player;
        throw new IllegalArgumentException("st.no-player");
    }
    private void rebuild() {
        var root=ToolUi.content(this);
        root.table(t->{int columns=ToolUi.width()<300?2:3;for(int i=0;i<3;i++){final int index=i;var b=t.button(text(new String[]{"st.player-tab-0","st.player-tab-1","st.player-tab-2"}[i]),()->{tab=index;rebuild();}).width((ToolUi.width()-4*(columns-1))/columns).minHeight(44).get();b.getLabel().setWrap(true);b.setChecked(tab==i);if(i%columns==columns-1)t.row();}}).row();
        if(tab==0){
            root.label(() -> { try { return Strings.stripColors(target().name); } catch (RuntimeException ignored) { return text("st.no-player"); } }).get().setWrap(true);root.row();

            button(root,text("st.player-copy-name"), () -> run(() -> Core.app.setClipboardText(Strings.stripColors(target().name)))).row();
            button(root,text("st.player-spectate"), () -> run(() -> {
                Player player = target();
                if (player.dead() || (!actions.isAvailable() && player.unit().inFogTo(Vars.player.team())))
                    throw new IllegalArgumentException("st.no-player");
                Vars.control.input.spectate(player.unit()); hide();
            })).row();
            button(root,text("st.player-stop-spectate"), () -> { Vars.control.input.spectating = null; hide(); }).row();
            root.add(text("st.host-only-tools")).row();
            var cycle=button(root,text("st.player-cycle-team"), () -> confirm("st.player-cycle-warning", () -> actions.cycleTeam(target(), !actions.isCycling(target()))))
                .disabled(!actions.isAvailable()).get();
            cycle.update(()->{try{cycle.setText(text("st.player-cycle-team")+": "+text(actions.isCycling(target())?"st.on":"st.off"));}catch(RuntimeException e){cycle.setDisabled(true);}});

            ToolUi.valueField(root,text("st.player-tile-x"),x,v->x=v);
            ToolUi.valueField(root,text("st.player-tile-y"),y,v->y=v);
            button(root,text("st.unit-tools-control-building"), () -> run(() -> UnitToolsDialog.controlBuilding(Vars.world.build(Integer.parseInt(x), Integer.parseInt(y)))))
                .disabled(b -> {try{return !target().isLocal();}catch(RuntimeException e){return true;}}).row();
            button(root,text("st.player-teleport"), () -> confirm("st.player-teleport", () -> actions.teleport(target(),
                coordinate(x), coordinate(y)))).disabled(!actions.isAvailable()).row();
            button(root,text("st.player-to-me"), () -> confirm("st.player-to-me", () -> actions.teleport(target(), Vars.player.x, Vars.player.y)))
                .disabled(!actions.isAvailable()).row();
            button(root,text("st.player-core-create"), () -> confirm("st.player-core-create-warning", () -> actions.coreShard(target().team(),
                    Integer.parseInt(x), Integer.parseInt(y)))).disabled(!actions.isAvailable()).row();
            button(root,text("st.player-core-remove"), () -> confirm("st.player-core-remove-warning", () -> actions.removeCore(target().team(),
                    Integer.parseInt(x), Integer.parseInt(y)))).disabled(!actions.isAvailable()).row();
        }
        if(tab==1){
            button(root,text("st.player-unit") + ": " + (type == null ? text("st.none") : type.localizedName), () -> {
                List<UnitType> list = new ArrayList<>();
                for (UnitType entry : Vars.content.units()) if (!entry.isHidden() && !entry.isBanned() && entry.playerControllable
                        && entry.segmentUnits <= 1 && entry.supportsEnv(Vars.state.rules.env)) list.add(entry);
                ContentPicker.show(text("st.player-unit"), list, false, selected -> { type = selected; rebuild(); });
            }).row();
            button(root,text("st.player-replace-unit"), () -> confirm("st.player-replace-unit", () -> {
                Player player = target(); actions.controlledUnit(player, type, player.x, player.y);
            })).disabled(!actions.isAvailable() || type == null).row();
            button(root,text("st.player-remove-unit"), () -> confirm("st.player-remove-unit-warning", () -> actions.removeUnit(target())))
                .disabled(!actions.isAvailable()).row();
            button(root,text("st.player-effect") + ": " + (effect == null ? text("st.none") : effect.localizedName), () -> {
                List<StatusEffect> effects = new ArrayList<>();
                for (StatusEffect entry : Vars.content.<StatusEffect>getBy(ContentType.status)) if (!entry.dynamic && !entry.reactive) effects.add(entry);
                ContentPicker.show(text("st.player-effect"), effects, false, selected -> { effect = selected; rebuild(); });
            }).row();

            ToolUi.valueField(root,text("st.player-effect-seconds"),seconds,v->seconds=v);
            button(root,text("st.apply"), () -> run(() -> actions.status(target(), effect, Float.parseFloat(seconds), false))).disabled(!actions.isAvailable() || effect == null).row();
            button(root,text("st.player-effect-remove"), () -> run(() -> actions.status(target(), effect, 1, true))).disabled(!actions.isAvailable() || effect == null).row();
        }
        if(tab==2){
            button(root,text("st.player-kick"), () -> confirm("st.player-kick", () -> actions.moderate(target(), AdminAction.kick))).disabled(!actions.isAvailable()).row();
            button(root,text("st.player-ban"), () -> confirm("st.player-ban-warning", () -> actions.moderate(target(), AdminAction.ban))).disabled(!actions.isAvailable()).row();
            button(root,text("st.player-trace"), () -> run(() -> actions.moderate(target(), AdminAction.trace))).disabled(!actions.isAvailable()).row();
            button(root,text("st.player-admin-toggle"), () -> confirm("st.player-admin-warning", () -> {
                Player player = target(); actions.admin(player, !player.admin);
            })).disabled(!actions.isAvailable()).row();
            button(root,text("st.player-votekick"), () -> run(() -> {
                Player player = target();
                if (!Vars.net.client() || player.isLocal() || player.admin || player.team() != Vars.player.team() || Groups.player.size() < 3)
                    throw new IllegalStateException("st.player-votekick-unavailable");
                Object world = Vars.world.tiles;
                int maxReason = Math.min(120, Vars.maxTextLength - ("/votekick #" + player.id + " ").length());
                Vars.ui.showTextInput(text("st.player-votekick"), text("st.player-votekick-reason"), maxReason, "", reason -> run(() -> {
                    Player current = target();
                    if (world != Vars.world.tiles || !Vars.net.client() || current != player || current.isLocal() || current.admin
                            || current.team() != Vars.player.team() || Groups.player.size() < 3)
                        throw new IllegalStateException("st.player-votekick-unavailable");
                    if (reason == null || reason.isBlank() || reason.length() > maxReason || reason.chars().anyMatch(Character::isISOControl))
                        throw new IllegalArgumentException("st.error.input");
                    // The user explicitly submits the game's built-in votekick command, never executable code.
                    CallVote.send(current, reason);
                }));
            })).disabled(!Vars.net.client()).row();
        }

    }
    private static arc.scene.ui.layout.Cell<arc.scene.ui.TextButton> button(arc.scene.ui.layout.Table root,String label,Runnable task){
        var row=new arc.scene.ui.layout.Table();root.add(row).row();
        var cell=row.button(label,task).width(ToolUi.width()-44).minHeight(44);cell.get().getLabel().setWrap(true);
        String key="st.player-tools-help";
        for(String[] pair:new String[][]{{"st.player-cycle-team","st.player-cycle-warning"},{"st.player-core-create","st.player-core-create-warning"},{"st.player-core-remove","st.player-core-remove-warning"},{"st.player-remove-unit","st.player-remove-unit-warning"},{"st.player-ban","st.player-ban-warning"},{"st.player-admin-toggle","st.player-admin-warning"},{"st.apply","st.player-effect-help"},{"st.player-effect-remove","st.player-effect-help"}})
            if(label.equals(text(pair[0])))key=pair[1];
        for(String[] pair:new String[][]{{"st.player-copy-name","st.help.player-copy"},{"st.player-spectate","st.help.player-watch"},{"st.player-stop-spectate","st.help.player-unwatch"},{"st.unit-tools-control-building","st.help.player-building"},{"st.player-teleport","st.help.player-teleport"},{"st.player-to-me","st.help.player-to-me"},{"st.player-unit","st.help.player-type"},{"st.player-replace-unit","st.help.player-replace"},{"st.player-effect","st.player-effect-help"},{"st.player-kick","st.help.player-kick"},{"st.player-trace","st.help.player-trace"},{"st.player-votekick","st.player-votekick-unavailable"}})
            if(label.equals(text(pair[0]))||label.startsWith(text(pair[0])+": "))key=pair[1];
        ToolUi.helpButton(row,label,key);return cell;
    }
    private static float coordinate(String value) {
        int tile = Integer.parseInt(value.trim());
        if (tile < 0 || tile > 32767) throw new IllegalArgumentException("st.error.input");
        return tile * (float)Vars.tilesize;
    }
    private void confirm(String key, Runnable action) {
        run(() -> {
            String name = Strings.stripColors(target().name);
            Runnable guarded=WorldGuard.capture(action);
            Vars.ui.showConfirm(text("st.player-tools"), name + "\n" + text(key), () -> run(guarded));
        });
    }
    private static void run(Runnable action) {
        try { action.run(); }
        catch (RuntimeException failure) {
            String key = failure.getMessage();
            Vars.ui.showErrorMessage(key != null && Core.bundle.has(key) ? text(key) : text("st.error.input"));
        }
    }
    private static final class CallVote {
        static void send(Player target, String reason) { mindustry.gen.Call.sendChatMessage("/votekick #" + target.id + " " + reason); }
    }
}
