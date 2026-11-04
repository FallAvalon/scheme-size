package org.schemetools.client;

import arc.scene.ui.Dialog;
import mindustry.Vars;
import mindustry.ui.dialogs.BaseDialog;

/** Native tool content can live in the HUD without blocking the game's map input. */
public class ToolDialog extends BaseDialog {
    public ToolDialog(String title) { super(title); }
    public void refreshWidth(){}
    public boolean compactContent(){return false;}
    @Override public Dialog show() {
        if (ToolWorkspace.active != null && Vars.state.isGame()) {
            ToolWorkspace.active.open(this);
            return this;
        }
        return super.show();
    }
    @Override public void hide() {
        if (ToolWorkspace.active != null && ToolWorkspace.active.current() == this) ToolWorkspace.active.back();
        else super.hide();
    }
    @Override public boolean isShown() {
        return ToolWorkspace.active != null && ToolWorkspace.active.current() == this || super.isShown();
    }
}
