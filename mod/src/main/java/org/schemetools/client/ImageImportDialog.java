package org.schemetools.client;

import arc.Core;
import arc.files.Fi;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.ui.FileChooser.FileChooserParams;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.logic.LogicDisplay;
import org.schemetools.schematic.ImageSchematic;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Conversion runs outside the render thread; closing the dialog cancels publication and work. */
public final class ImageImportDialog extends ToolDialog {
    private final Consumer<mindustry.game.Schematic> imported;
    private final AtomicInteger generation = new AtomicInteger();
    private Fi file;
    private LogicDisplay display = (LogicDisplay)Blocks.logicDisplay;
    private String columns = "1", rows = "1", step = "1";
    private boolean smooth = true, busy;
    private ImageSchematic.Result result;
    private String message = "";
    public ImageImportDialog(Consumer<mindustry.game.Schematic> imported) {
        super(text("st.image.title"));
        this.imported = imported;
        addCloseButton();
        shown(this::rebuild);
        hidden(() -> { generation.incrementAndGet(); busy = false; });
    }
    private static String text(String key) { return Core.bundle.get(key); }
    private void changed() { generation.incrementAndGet(); result = null; message = ""; }
    private void rebuild() {
        cont.clear();
        cont.pane(root -> {
            root.defaults().left().pad(5);
            root.add(text("st.image.help")).wrap().width(UiSizing.width(560f)).row();
            root.button(file == null ? text("st.image.file") : file.name(), () -> new FileChooserParams().open(true).extensions("png", "jpg", "jpeg", "bmp")
                .submit(value -> { file = value; changed(); rebuild(); })).disabled(busy).row();
            root.button(text("st.image.display") + ": " + display.localizedName, () -> {
                var displays = new ArrayList<LogicDisplay>();
                for (var block : Vars.content.blocks()) if (block instanceof LogicDisplay entry && !entry.privileged
                        && entry.displaySize <= 512 && !entry.isBanned() && entry.unlockedNow()) displays.add(entry);
                ContentPicker.show(text("st.image.display"), displays, false, selected -> { display = selected; changed(); rebuild(); });
            }).disabled(busy).row();
            root.table(row -> {
                row.add(text("st.image.columns")); row.field(columns, v -> { columns = v; changed(); }).width(UiSizing.width(70)).maxTextLength(2).disabled(busy);
                row.add(text("st.image.rows")); row.field(rows, v -> { rows = v; changed(); }).width(UiSizing.width(70)).maxTextLength(2).disabled(busy);
                row.add(text("st.image.step")); row.field(step, v -> { step = v; changed(); }).width(UiSizing.width(70)).maxTextLength(2).disabled(busy);
            }).row();
            root.check(text("st.image.smooth"), smooth, value -> { smooth = value; changed(); }).disabled(busy).row();
            root.button(busy ? text("st.image.busy") : text("st.image.convert"), this::convert).disabled(busy || file == null).row();
            if (!message.isEmpty()) root.add(message).wrap().width(UiSizing.width(560f)).row();
            if (result != null) {
                root.add(Core.bundle.format("st.image.result", result.schematic().width, result.schematic().height,
                    result.programs().size(), result.rectangles())).wrap().width(UiSizing.width(560f)).row();
                root.button(text("st.image.import"), () -> { imported.accept(result.schematic()); hide(); }).row();
                root.button(text("st.image.code"), () -> {
                    var output = new StringBuilder(); int i = 0;
                    for (String program : result.programs()) output.append("# processor ").append(++i).append('\n').append(program).append('\n');
                    Core.app.setClipboardText(output.toString());
                }).row();
            }
        }).width(UiSizing.width(600f)).maxHeight(UiSizing.toolHeight(0.7f));
    }
    private void convert() {
        try {
            var options = new ImageSchematic.Options(display, (LogicBlock)Blocks.microProcessor,
                Integer.parseInt(columns), Integer.parseInt(rows), smooth, Integer.parseInt(step));
            var source = file.file().toPath();
            int ticket = generation.incrementAndGet(); busy = true; result = null; message = ""; rebuild();
            Thread worker = new Thread(() -> {
                try {
                    var value = ImageSchematic.read(source, options, () -> generation.get() != ticket);
                    Core.app.post(() -> { if (generation.get() == ticket && isShown()) { result = value; busy = false; rebuild(); } });
                } catch (Exception failure) {
                    String key = failure.getMessage();
                    Core.app.post(() -> { if (generation.get() == ticket && isShown()) {
                        busy = false; message = Core.bundle.has(key == null ? "" : key) ? text(key) : text("st.image.error.file"); rebuild();
                    } });
                }
            }, "scheme-size-image-import");
            worker.setDaemon(true); worker.start();
        } catch (RuntimeException failure) { message = text("st.image.error.options"); rebuild(); }
    }
}
