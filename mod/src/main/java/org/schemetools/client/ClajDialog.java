package org.schemetools.client;

import arc.Core;
import mindustry.Vars;
import mindustry.ui.dialogs.BaseDialog;
import org.schemetools.shared.ClajWire;

/** A conditional legacy-protocol integration. No service is contacted on opening this window. */
public final class ClajDialog extends ToolDialog {
    private final ClajService service;
    private String host = "", port = "6567", localPort = "6567", link = "", status = "";
    public ClajDialog(ClajService service) {
        super(Core.bundle.get("st.claj.title")); this.service = service;
        localPort = "" + Core.settings.getInt("port", 6567);
        addCloseButton(); shown(this::rebuild);
    }
    private static String text(String key) { return Core.bundle.get(key); }
    private void rebuild() {
        cont.clear();
        cont.pane(root -> {
            root.defaults().left().pad(4);
            root.add(text("st.claj.help")).wrap().width(UiSizing.width(580)).row();
            root.table(row -> {
                row.add(text("st.claj.relay")); row.field(host, value -> host = value).width(UiSizing.width(230)).maxTextLength(253);
                row.field(port, value -> port = value).width(UiSizing.width(85)).maxTextLength(5);
            }).row();
            root.table(row -> {
                row.add(text("st.claj.local-port")); row.field(localPort, value -> localPort = value).width(UiSizing.width(100)).maxTextLength(5);
            }).row();
            root.button(text("st.claj.create"), () -> Vars.ui.showConfirm(text("st.claj.title"), text("st.claj.create-warning"), () -> run(() -> {
                service.create(host.trim(), Integer.parseInt(port), Integer.parseInt(localPort), value -> {
                    status = Core.bundle.has(value) ? text(value) : value; rebuild();
                }); status = text("st.claj.connecting"); rebuild();
            }))).disabled(!Vars.net.server()).row();
            for (var room : service.rooms()) root.table(row -> {
                row.label(() -> room.closed() ? text("st.claj.closed") : room.link().isEmpty() ? text("st.claj.connecting") : room.link()).wrap().width(UiSizing.width(360));
                row.button(text("st.claj.copy"), () -> Core.app.setClipboardText(room.link())).disabled(room.link().isEmpty());
                row.button(text("st.claj.close"), () -> { room.close(); rebuild(); });
            }).row();
            root.button(text("st.claj.close-all"), () -> { service.closeAll(); rebuild(); }).row();
            root.add(text("st.claj.link")).row();
            root.field(link, value -> link = value).width(UiSizing.width(560)).maxTextLength(400).row();
            root.button(text("st.claj.parse"), () -> run(() -> {
                var parsed = ClajWire.parse(link); status = parsed.host() + ":" + parsed.port(); rebuild();
            })).row();
            root.button(text("st.claj.join"), () -> run(() -> {
                var parsed = ClajWire.parse(link);
                Vars.ui.showConfirm(text("st.claj.title"), text("st.claj.join-warning") + "\n" + parsed.host() + ":" + parsed.port(),
                    () -> run(() -> { ClajService.join(parsed); hide(); }));
            })).disabled(Vars.net.server()).row();
            if (!status.isEmpty()) root.add(status).wrap().width(UiSizing.width(580)).row();
        }).width(UiSizing.width(620)).maxHeight(UiSizing.toolHeight(0.72f));
    }
    private void run(Runnable action) {
        try { action.run(); }
        catch (RuntimeException failure) { status = Core.bundle.get(failure.getMessage() == null ? "" : failure.getMessage(), text("st.claj.error.network")); rebuild(); }
    }
}
