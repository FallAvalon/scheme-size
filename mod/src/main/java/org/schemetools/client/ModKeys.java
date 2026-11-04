package org.schemetools.client;

import arc.Core;
import arc.Events;
import arc.input.KeyBind;
import arc.input.KeyCode;
import mindustry.Vars;
import mindustry.game.EventType.Trigger;
import org.schemetools.Edition;

import java.util.ArrayList;
import java.util.List;

/** Explicit opt-in bindings; no vanilla key is captured by default. */
public final class ModKeys {
    public enum Action {
        PANEL("schemetools-panel", "st.keys.panel", "panel"),
        SCHEMATICS("schemesize-schematics", "st.schematic.title", "schematics"),
        QUICK_SCHEMATICS("schemesize-quick-schematics", "st.quick-schematics", "schematics"),
        BUILD("schemesize-build", "st.build.title", "build"),
        CORE("schemesize-core", "st.coreinfo.title", "info"),
        WAVE("schemesize-wave", "st.wave.title", "info"),
        ROUTES("schemesize-routes", "st.routes.title", "routes"),
        CONSOLE("schemesize-console", "st.console.title", "console"),
        VISUAL("schemesize-visual", "st.visual.title", "visual"),
        AI("schemesize-ai", "st.ai.title", "ai"),
        PLAYERS("schemesize-players", "st.players", "players"),
        UNITS("schemesize-unit-tools", "st.unit-tools", "players"),
        SELF("schemesize-self-tools", "st.player-tools", "players"),
        ITEMS("schemesize-items", "st.items", "players"),
        RESTORE("schemesize-restore", "st.build.restore", "build"),
        ARENA("schemesize-arena", "st.arena.title", "arena"),
        EVENT("schemesize-event", "st.event", "event");
        public final String id, label, module;
        Action(String id, String label, String module) { this.id = id; this.label = label; this.module = module; }
    }
    public record Entry(Action action, KeyBind binding, Runnable run) { }
    public record Chord(boolean ctrl, boolean alt, boolean shift) {
        public boolean matches() { return ctrl == Core.input.ctrl() && alt == Core.input.alt() && shift == Core.input.shift(); }
        public String prefix() { return (ctrl ? "Ctrl+" : "") + (alt ? "Alt+" : "") + (shift ? "Shift+" : ""); }
    }
    private final List<Entry> entries = new ArrayList<>();

    public ModKeys(Edition edition, HostPanel panel, RouteOverlay routes) {
        for (Action action : Action.values()) {
            if (action == Action.EVENT && !edition.events) continue;
            KeyBind bind = KeyBind.all.find(value -> value.name.equals(action.id));
            if (bind == null) bind = KeyBind.add(action.id, KeyCode.unset, "scheme-tools");
            bind.load();
            Runnable run = switch (action) {
                case PANEL -> panel::show;
                case SCHEMATICS -> panel::openSchematics;
                case QUICK_SCHEMATICS -> panel::openQuickSchematics;
                case BUILD -> panel::openBuildTools;
                case CORE -> () -> new CoreInfoDialog().show();
                case WAVE -> () -> new WaveForecastDialog().show();
                case ROUTES -> () -> new RoutesDialog(routes).show();
                case CONSOLE -> panel::openConsole;
                case VISUAL -> panel::openVisuals;
                case AI -> panel::toggleAutopilot;
                case PLAYERS -> () -> panel.openPage("players");
                case UNITS -> panel::openUnitTools;
                case SELF -> panel::openSelfTools;
                case ITEMS -> () -> panel.openPage("items");
                case RESTORE -> panel::restoreBuild;
                case ARENA -> panel::openArenaTools;
                case EVENT -> () -> panel.openPage("event");
            };
            entries.add(new Entry(action, bind, run));
        }
        Events.run(Trigger.update, this::dispatch);
    }
    public void dispatch() {
        if (!Vars.state.isGame() || Core.scene.hasKeyboard() || Core.scene.hasDialog()) return;
        for (Entry entry : entries) if (Core.settings.getBool("schemesize-module-" + entry.action.module, true)
                && chord(entry).matches() && Core.input.keyTap(entry.binding)) { entry.run.run(); break; }
    }
    public List<Entry> entries() { return List.copyOf(entries); }
    public void assignOriginalPrimaryKeys() {
        for (Entry entry : entries) {
            KeyCode key = switch (entry.action) {
                case PANEL -> KeyCode.f12; case VISUAL -> KeyCode.y; case QUICK_SCHEMATICS -> KeyCode.g;
                case CORE -> KeyCode.f7; case AI -> KeyCode.i; case UNITS -> KeyCode.f2;
                case SELF -> KeyCode.f3; case ITEMS -> KeyCode.f4; default -> null;
            };
            if (key != null) assign(entry, key, new Chord(false, false, false));
        }
    }
    public static Chord chord(Entry entry) {
        String id = entry.action.id;
        return new Chord(Core.settings.getBool(id + "-ctrl", false), Core.settings.getBool(id + "-alt", false), Core.settings.getBool(id + "-shift", false));
    }
    public static void assign(Entry entry, KeyCode key, Chord chord) {
        if (key == null || key == KeyCode.escape) throw new IllegalArgumentException("st.error.input");
        entry.binding.value = new KeyBind.Axis(key);
        entry.binding.save();
        Core.settings.put(entry.action.id + "-ctrl", chord.ctrl);
        Core.settings.put(entry.action.id + "-alt", chord.alt);
        Core.settings.put(entry.action.id + "-shift", chord.shift);
    }
    public static void reset(Entry entry) {
        entry.binding.resetToDefault();
        Core.settings.remove(entry.action.id + "-ctrl"); Core.settings.remove(entry.action.id + "-alt"); Core.settings.remove(entry.action.id + "-shift");
    }
    public static void clear(Entry entry) {
        entry.binding.unset();
        entry.binding.save();
        Core.settings.remove(entry.action.id + "-ctrl"); Core.settings.remove(entry.action.id + "-alt"); Core.settings.remove(entry.action.id + "-shift");
    }
    public static List<String> conflicts(Entry entry, KeyCode key) {
        List<String> found = new ArrayList<>();
        for (KeyBind other : KeyBind.all) if (other != entry.binding && !other.isUnset() && other.value != null
                && (other.value.key == key || other.value.min == key || other.value.max == key)) found.add(other.name);
        return found;
    }
    public static String display(Entry entry) {
        if (entry.binding.isUnset()) return Core.bundle.get("st.keys.unset");
        KeyBind.Axis value = entry.binding.value;
        return chord(entry).prefix() + (value.key == null ? value.min + " / " + value.max : value.key.toString());
    }
}
