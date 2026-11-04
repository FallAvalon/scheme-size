package org.schemetools.client;

import arc.Core;
import arc.files.Fi;
import mindustry.Vars;
import mindustry.ui.FileChooser.FileChooserParams;
import mindustry.ui.dialogs.BaseDialog;
import org.schemetools.Edition;
import org.schemetools.shared.UpdateInstaller;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** No update request is made until the user selects a source and explicitly presses Check. */
public final class UpdateDialog extends ToolDialog {
    private final Edition edition;
    private final AtomicInteger generation = new AtomicInteger();
    private String repository;
    private boolean busy;
    private String message = "";
    private UpdateInstaller.Release release;
    public UpdateDialog(Edition edition) {
        super(text("st.update.title")); this.edition = edition;
        repository = Core.settings.getString("schemesize-update-source", "");
        addCloseButton(); shown(this::rebuild);
        hidden(() -> { generation.incrementAndGet(); busy = false; });
    }
    private static String text(String key) { return Core.bundle.get(key); }
    private void rebuild() {
        cont.clear();
        cont.pane(root -> {
            root.defaults().left().pad(5);
            root.add(text("st.update.help")).wrap().width(UiSizing.width(580)).row();
            root.add(edition.modId).row();
            root.add(text("st.update.source")).row();
            root.field(repository, value -> { repository = value; release = null; }).width(UiSizing.width(560)).maxTextLength(160).disabled(busy).row();
            root.button(text("st.update.check"), () -> {
                try {
                    String source = UpdateInstaller.repository(repository.trim());
                    Core.settings.put("schemesize-update-source", source);
                    work(() -> UpdateInstaller.latest(source, edition), result -> { release = result; rebuild(); });
                } catch (IOException failure) { error(failure); }
            }).disabled(busy).row();
            root.button(text("st.update.local"), () -> new FileChooserParams().open(true).extensions("jar").submit(file -> {
                Path source = file.file().toPath();
                work(() -> UpdateInstaller.inspect(source, edition), candidate -> confirmInstall(source, candidate));
            })).disabled(busy).row();
            if (busy) root.add(text("st.update.busy")).row();
            if (!message.isEmpty()) root.add(message).wrap().width(UiSizing.width(580)).row();
            if (release != null) {
                root.add(release.repository() + " / " + release.tag() + "\n" + release.size() + " bytes\nSHA-256: " + release.sha256()).wrap().width(UiSizing.width(580)).row();
                root.button(text("st.update.download"), () -> {
                    var selected = release;
                    Vars.ui.showConfirm(text("st.update.title"), text("st.update.download-warning") + "\n" + selected.url(), () -> {
                        var owner = Vars.mods.getMod(edition.modId);
                        if (owner == null || owner.main == null) { error(new IOException("st.update.error.edition")); return; }
                        Path directory = owner.main.getConfigFolder().child("updates").file().toPath();
                        work(() -> UpdateInstaller.stage(selected, edition, directory), file -> {
                            try { confirmInstall(file, UpdateInstaller.inspect(file, edition)); }
                            catch (IOException failure) { error(failure); }
                        });
                    });
                }).disabled(busy).row();
            }
        }).width(UiSizing.width(620)).maxHeight(UiSizing.toolHeight(0.72f));
    }
    private void confirmInstall(Path file, UpdateInstaller.Candidate candidate) {
        Vars.ui.showConfirm(text("st.update.title"), text("st.update.install-warning") + "\n" + candidate.id() + " " + candidate.version()
            + "\nSHA-256: " + candidate.sha256(), () -> {
                try {
                    var fresh = UpdateInstaller.inspect(file, edition);
                    if (!fresh.equals(candidate)) throw new IOException("st.update.error.digest");
                    Vars.mods.importMod(new Fi(file.toFile()));
                    message = text("st.update.restart"); release = null; rebuild();
                } catch (IOException | RuntimeException failure) { error(failure); }
            });
    }
    @FunctionalInterface private interface Task<T> { T run() throws Exception; }
    private <T> void work(Task<T> task, Consumer<T> complete) {
        int ticket = generation.incrementAndGet(); busy = true; message = ""; rebuild();
        Thread worker = new Thread(() -> {
            try {
                T result = task.run();
                Core.app.post(() -> { if (ticket == generation.get() && isShown()) { busy = false; rebuild(); complete.accept(result); } });
            } catch (Exception failure) {
                Core.app.post(() -> { if (ticket == generation.get() && isShown()) { busy = false; error(failure); } });
            }
        }, "scheme-size-explicit-update"); worker.setDaemon(true); worker.start();
    }
    private void error(Exception failure) {
        String key = failure.getMessage(); message = Core.bundle.get(key == null ? "" : key, text("st.update.error.network")); rebuild();
    }
}
