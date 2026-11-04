package org.schemetools.client;

import arc.Core;
import arc.scene.ui.TextArea;
import arc.scene.ui.TextField;
import mindustry.Vars;
import mindustry.ui.dialogs.BaseDialog;

/** Explicit local host console; executing arbitrary code is always a user-initiated action. */
public final class LocalConsoleDialog extends ToolDialog {
    private final LocalConsole console;
    private boolean multiline;
    private String draft = "", interval = "5";
    private int historyIndex = -1;
    private TextField editor;
    public LocalConsoleDialog(LocalConsole console) {
        super(text("st.console.title"));
        this.console = console;
        addCloseButton(); shown(this::rebuild);
    }
    private static String text(String key) { return Core.bundle.get(key); }
    private void rebuild() {
        cont.clear();
        cont.pane(root -> {
            root.defaults().pad(4f).left();
            root.add(text("st.console.warning")).wrap().width(UiSizing.width(600f)).row();
            root.button(text(multiline ? "st.console.multiline" : "st.console.classic"), () -> {
                draft = editor.getText(); multiline = !multiline; rebuild();
            }).row();
            editor = multiline ? new TextArea(draft) : new TextField(draft);
            editor.setMaxLength(8192);
            root.add(editor).width(UiSizing.width(600f)).height(multiline ? 180f : 45f).row();
            root.table(row -> {
                row.button(text("st.console.previous"), () -> history(1));
                row.button(text("st.console.next"), () -> history(-1));
                row.button(text("st.console.run"), () -> confirm(false));
            }).row();
            root.table(row -> {
                row.add(text("st.console.interval")); row.field(interval, value -> interval = value).width(UiSizing.width(120f)).maxTextLength(8);
                row.button(text("st.console.repeat"), () -> confirm(true));
                row.button(text("st.console.stop-all"), () -> { console.stopAll(); rebuild(); });
            }).row();
            for (LocalConsole.Job job : console.jobs()) root.table(row -> {
                String snippet = job.script().replace('\n', ' ');
                row.add("#" + job.id() + " · " + job.intervalSeconds() + " s · "
                    + (snippet.length() > 40 ? snippet.substring(0, 40) + "…" : snippet).replace("[", "[[")).width(UiSizing.width(440f)).left();
                row.button(text("st.console.stop"), () -> { console.stop(job.id()); rebuild(); });
            }).row();
            root.add(text("st.console.output")).left().row();
            root.label(() -> console.output().replace("[", "[[")).wrap().width(UiSizing.width(600f)).left().row();
        }).width(UiSizing.width(640f)).maxHeight(UiSizing.toolHeight(0.7f));
    }
    private void history(int direction) {
        var history = console.history();
        if (history.isEmpty()) return;
        historyIndex = Math.max(-1, Math.min(history.size() - 1, historyIndex + direction));
        editor.setText(historyIndex < 0 ? "" : history.get(historyIndex));
    }
    private void confirm(boolean repeating) {
        String script = editor.getText();
        double seconds;
        try { seconds = repeating ? Double.parseDouble(interval) : 0; }
        catch (NumberFormatException failure) { Vars.ui.showErrorMessage(text("st.console.error.interval")); return; }
        String key = repeating ? "st.console.confirm-repeat" : "st.console.confirm-run";
        Runnable guarded=WorldGuard.capture(() -> { if(repeating)console.repeat(script,seconds);else console.run(script); });
        Vars.ui.showConfirm(text("st.console.title"), text(key), () -> {
            try {
                guarded.run();
                draft = script; historyIndex = -1; rebuild();
            } catch (RuntimeException failure) {
                String message = failure.getMessage();
                Vars.ui.showErrorMessage(message != null && Core.bundle.has(message) ? text(message) : text("st.console.error.script"));
            }
        });
    }
}
