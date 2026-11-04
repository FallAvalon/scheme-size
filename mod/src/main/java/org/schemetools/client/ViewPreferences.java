package org.schemetools.client;

import arc.Core;
import arc.Events;
import mindustry.Vars;
import mindustry.game.EventType.*;
import mindustry.input.DesktopInput;

/** Opt-in public camera fields. Defaults leave the game's current values untouched. */
public final class ViewPreferences {
    private float originalPan, originalBoost;
    private DesktopInput input;
    private boolean applied;
    public void install() {
        if (Vars.headless) return;
        Events.run(Trigger.update, this::update);
        // Renderer.update() reloads the native zoom settings every frame. Reapply the
        // temporary multipliers after that update without changing saved game settings.
        Events.run(Trigger.preDraw, this::update);
        Events.on(ResetEvent.class, event -> restore());
        Events.on(WaveEvent.class, event -> {
            if (Core.settings.getBool("schemesize-wave-notice", true) && Core.settings.getBool("schemesize-module-info", true))
                Core.app.post(() -> { if (Vars.state.isGame()) Vars.ui.showInfoFade(Core.bundle.format("st.wave.notice", Vars.state.wave)); });
        });
        Events.on(ClientLoadEvent.class, event -> {
            if (Core.settings.getBool("schemesize-welcome", true)) Vars.ui.showInfoFade(Core.bundle.get("st.welcome"));
        });
    }
    public void update() {
        if (!Vars.state.isGame() || !Core.settings.getBool("schemesize-view-custom", false)
                || !(Vars.control.input instanceof DesktopInput current)) { restore(); return; }
        if (applied && input != current) restore();
        if (!applied) {
            input = current;
            originalPan = input.panSpeed; originalBoost = input.panBoostSpeed; applied = true;
        }
        float zoomOut = Math.max(1, Math.min(5, Core.settings.getInt("schemesize-zoom-out", 1)));
        float zoomIn = Math.max(1, Math.min(5, Core.settings.getInt("schemesize-zoom-in", 1)));
        float pan = Math.max(1, Math.min(5, Core.settings.getInt("schemesize-pan-speed", 1)));
        Vars.renderer.minZoomInGame = Math.max(0.1f, nativeMin() / zoomOut);
        Vars.renderer.maxZoomInGame = Math.min(30, nativeMax() * zoomIn);
        input.panSpeed = originalPan * pan; input.panBoostSpeed = originalBoost * pan;
    }
    public void restore() {
        if (!applied) return;
        Vars.renderer.minZoomInGame = nativeMin(); Vars.renderer.maxZoomInGame = nativeMax();
        input.panSpeed = originalPan; input.panBoostSpeed = originalBoost; input = null; applied = false;
    }
    private static float nativeMin() {
        float multiplier=Core.settings.getFloat("minzoomingamemultiplier",1f);
        return Vars.renderer.minZoom / (Float.isFinite(multiplier)&&multiplier>0 ? multiplier : 1f);
    }
    private static float nativeMax() {
        float multiplier=Core.settings.getFloat("maxzoomingamemultiplier",1f);
        return Vars.renderer.maxZoom * (Float.isFinite(multiplier)&&multiplier>0 ? multiplier : 1f);
    }
}
