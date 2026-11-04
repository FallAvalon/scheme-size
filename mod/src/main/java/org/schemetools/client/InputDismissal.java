package org.schemetools.client;

import arc.Core;
import arc.input.KeyCode;
import arc.scene.event.Touchable;
import arc.scene.ui.Dialog;
import arc.util.Time;
import mindustry.input.Binding;
import mindustry.ui.dialogs.BaseDialog;

/** Control.update polls Escape independently of Arc's consumed scene events in 160.7. */
final class InputDismissal {
    private InputDismissal(){}
    static void run(Runnable operation){
        if(!Core.input.keyTap(KeyCode.escape) && !Core.input.keyTap(Binding.menu)){
            operation.run();return;
        }
        BaseDialog gate=new BaseDialog("");
        Dialog.DialogStyle style=new Dialog.DialogStyle();
        style.titleFont=gate.getStyle().titleFont;style.titleFontColor=gate.getStyle().titleFontColor;
        gate.setStyle(style);gate.clearChildren();gate.touchable=Touchable.disabled;
        gate.show(Core.scene,null);gate.setSize(0,0);
        operation.run();
        // Keep hasDialog true during the tap frame, without altering any vanilla binding.
        Time.runTask(2,()->gate.hide(null));
    }
}
