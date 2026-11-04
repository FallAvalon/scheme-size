package org.schemetools.client;

import mindustry.Vars;

/** A confirmation made for one world must never execute against its replacement. */
public final class WorldGuard {
    private WorldGuard() { }
    public static Runnable capture(Runnable action) {
        Object tiles=Vars.world.tiles;
        return () -> {
            if(tiles!=Vars.world.tiles || !Vars.state.isGame())throw new IllegalStateException("st.arena.error.world");
            action.run();
        };
    }
}
