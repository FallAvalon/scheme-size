package org.schemetools.shared;

import mindustry.Vars;
import mindustry.gen.Player;

/** Remote PlayerComp.uuid() is [LOCAL] when no NetConnection is present on a client. */
public final class PlayerKey {
    private PlayerKey() { }
    public static String of(Player player) { return Vars.net.client() ? "session:" + player.id : player.uuid(); }
}
