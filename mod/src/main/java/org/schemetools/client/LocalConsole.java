package org.schemetools.client;

import arc.Events;
import mindustry.Vars;
import mindustry.game.EventType.ResetEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import org.schemetools.host.HostActions;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Host-only access to the game's existing local Rhino console. No script is transmitted by this class. */
public final class LocalConsole {
    public record Job(int id, String script, double intervalSeconds, long nextRunNanos) { }
    private final HostActions actions;
    private final ArrayDeque<String> history = new ArrayDeque<>();
    private final List<Job> jobs = new ArrayList<>();
    private int nextId;
    private String output = "";
    public LocalConsole(HostActions actions) { this.actions = actions; }
    public void install() {
        Events.on(ResetEvent.class, event -> jobs.clear());
        Events.on(WorldLoadEvent.class, event -> jobs.clear());
        Events.run(Trigger.update, this::update);
    }
    public String run(String script) {
        requireHost(); validate(script);
        remember(script);
        output = limit(Vars.mods.getScripts().runConsole(script), 8192);
        return output;
    }
    public int repeat(String script, double seconds) {
        requireHost(); validate(script);
        if (!Double.isFinite(seconds) || seconds < 1 || seconds > 3600 || jobs.size() >= 8)
            throw new IllegalArgumentException("st.console.error.interval");
        remember(script);
        int id = ++nextId;
        jobs.add(new Job(id, script, seconds, System.nanoTime() + (long)(seconds * 1_000_000_000L)));
        return id;
    }
    public boolean stop(int id) { return jobs.removeIf(job -> job.id == id); }
    public void stopAll() { jobs.clear(); }
    public List<Job> jobs() { return List.copyOf(jobs); }
    public List<String> history() { return List.copyOf(history); }
    public String output() { return output; }
    private void update() {
        if (!actions.isAvailable()||!arc.Core.settings.getBool("schemesize-module-console",true)) { jobs.clear(); return; }
        if (Vars.state.isPaused()) return;
        long now = System.nanoTime();
        for (Job job : List.copyOf(jobs)) {
            int index = jobs.indexOf(job);
            if (index < 0 || now - job.nextRunNanos < 0) continue;
            // No catch-up loop after pauses or slow frames; one execution per job per interval.
            jobs.set(index, new Job(job.id, job.script, job.intervalSeconds, now + (long)(job.intervalSeconds * 1_000_000_000L)));
            try {
                requireHost();
                output = limit(Vars.mods.getScripts().runConsole(job.script), 8192);
            } catch (RuntimeException failure) {
                jobs.removeIf(current -> current.id == job.id);
                output = failure.toString();
            }
        }
    }
    private void requireHost() { if (!actions.isAvailable()) throw new IllegalStateException("st.error.authority"); }
    private static void validate(String script) {
        if (script == null || script.isBlank() || script.length() > 8192) throw new IllegalArgumentException("st.console.error.script");
    }
    private void remember(String script) {
        history.remove(script); history.addFirst(script);
        while (history.size() > 100) history.removeLast();
    }
    private static String limit(String text, int size) { return text == null ? "" : text.length() <= size ? text : text.substring(0, size) + "…"; }
}
