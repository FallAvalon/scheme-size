package org.schemetools.client;

import arc.Core;
import mindustry.Vars;
import mindustry.ui.dialogs.BaseDialog;

/** An explicit alternative to modifying native editor dialogs and their private widget tree. */
public final class EditorResizeDialog extends ToolDialog {
    private String width = "850", height = "850", shiftX = "0", shiftY = "0";
    public EditorResizeDialog() {
        super(Core.bundle.get("st.editor.resize")); addCloseButton();
        if (available()) { width = "" + Vars.editor.width(); height = "" + Vars.editor.height(); }
        cont.add(Core.bundle.get("st.editor.resize-help")).wrap().width(UiSizing.width(560)).row();
        String[] values = {width, height, shiftX, shiftY};
        String[] labels = {"W", "H", "X", "Y"};
        for (int i = 0; i < values.length; i++) {
            int index = i;
            cont.table(row -> {
                row.add(labels[index]).width(UiSizing.width(80));
                row.field(values[index], value -> { switch (index) {
                    case 0 -> width = value; case 1 -> height = value; case 2 -> shiftX = value; default -> shiftY = value;
                } }).width(UiSizing.width(140)).maxTextLength(5);
            }).row();
        }
        cont.button(Core.bundle.get("st.apply"), () -> Vars.ui.showConfirm(Core.bundle.get("st.editor.resize"),
            Core.bundle.get("st.editor.resize-warning"), () -> {
                try {
                    int w = Integer.parseInt(width), h = Integer.parseInt(height), x = Integer.parseInt(shiftX), y = Integer.parseInt(shiftY);
                    validate(w, h, x, y);
                    Vars.ui.loadAnd(() -> { apply(w, h, x, y); hide(); });
                } catch (RuntimeException failure) { Vars.ui.showErrorMessage(Core.bundle.get("st.editor.resize-error")); }
            })).disabled(!available()).row();
    }
    public static boolean available() { return !Vars.headless && Vars.ui != null && Vars.ui.editor.isShown() && !Vars.net.active(); }
    private static void validate(int width, int height, int shiftX, int shiftY) {
        Runtime runtime = Runtime.getRuntime();
        long spare = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory());
        // Includes old and new tile grids, renderer work and safety margin. A heuristic,
        // not a promise that the JVM or GPU can allocate every permitted map.
        long cells = Math.min(25_000_000L, Math.max(0, spare / 3 / 256));
        if (!available() || width < 50 || height < 50 || width > 5000 || height > 5000
                || (long)width * height > cells || Math.abs((long)shiftX) > 5000 || Math.abs((long)shiftY) > 5000)
            throw new IllegalArgumentException("st.editor.resize-error");
    }
    public static void apply(int width, int height, int shiftX, int shiftY) {
        validate(width, height, shiftX, shiftY);
        Vars.editor.resize(width, height, shiftX, shiftY); Vars.ui.editor.resetSaved();
    }
}
