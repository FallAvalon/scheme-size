package org.schemetools;

import mindustry.mod.Mod;

/** Client entry point. No UI objects or handlers are constructed on a dedicated server. */
public final class SchemeToolsMod extends Mod {
    @Override
    public void init() {
        EditionBootstrap.init(this, Edition.GOD);
    }
}
