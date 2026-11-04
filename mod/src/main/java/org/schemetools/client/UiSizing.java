package org.schemetools.client;

import arc.Core;
import arc.scene.ui.layout.Scl;

/** Cell dimensions are logical units: Arc applies the interface scale itself. */
final class UiSizing {
    private UiSizing() { }
    static float scale() { return Math.max(.25f, Scl.scl(1f)); }
    static float height() { return Core.scene.getHeight() / scale(); }
    static float toolHeight(float fraction) { return ToolWorkspace.active == null ? height()*fraction : Math.max(40, Math.min(height()*fraction, ToolWorkspace.contextHeight()-110)); }
    static float width(float preferred) {
        float available = ToolWorkspace.active == null ? Core.scene.getWidth() / scale() - 80f : ToolWorkspace.contentWidth() - 80f;
        return preferred * Math.max(.25f, Math.min(1f, available / 720f));
    }
}
