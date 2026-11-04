package org.schemetools;

import arc.Core;
import arc.Events;
import arc.util.Log;
import mindustry.Vars;
import mindustry.core.Version;
import mindustry.game.EventType.*;
import mindustry.mod.Mod;
import org.schemetools.client.HostPanel;
import org.schemetools.client.RouteOverlay;
import org.schemetools.client.ModKeys;
import org.schemetools.client.ToolSettingsDialog;
import org.schemetools.client.LocalConsole;
import org.schemetools.client.VisualLayers;
import org.schemetools.client.LocalAutopilot;
import org.schemetools.event.BossPresetStore;
import org.schemetools.event.BossRoundController;
import org.schemetools.host.HostActions;
import org.schemetools.schematic.SchematicStore;

/** One implementation for both edition entry points. Never initializes UI in headless. */
public final class EditionBootstrap {
    private EditionBootstrap() { }
    public static void init(Mod owner, Edition edition) {
        if (Vars.headless) return;
        if (Version.build != 160 || Version.revision != 7) {
            Log.err("Scheme-Size requires tested Mindustry build 160.7; found @. Tools are disabled.", Version.buildString());
            return;
        }
        // God includes Plus. When both are present, only God registers handlers and UI.
        // Metadata is available for all mods before their init methods are invoked.
        var god = Vars.mods.getMod(Edition.GOD.modId);
        if (edition == Edition.PLUS && god != null && god.enabled()) {
            Log.info("Scheme-Size Plus: God Edition is enabled; common UI is provided by God.");
            return;
        }
        HostActions actions = new HostActions(edition.events);
        if (arc.Core.settings.getBool("schemesize-controlled-distributor", false)) org.schemetools.host.HostDistribution.install();
        BossRoundController bossRounds = edition.events ? new BossRoundController(actions) : null;
        BossPresetStore presets = edition.events ? new BossPresetStore(owner.getConfigFolder().child("boss-presets").file().toPath()) : null;
        if (bossRounds != null) bossRounds.install();
        RouteOverlay routes = new RouteOverlay();
        routes.install();
        SchematicStore schematics = new SchematicStore(owner.getConfigFolder().parent().child("scheme-size-shared-data/schematics").file().toPath());
        LocalConsole console = new LocalConsole(actions);
        console.install();
        new VisualLayers().install();
        new org.schemetools.client.ViewPreferences().install();
        org.schemetools.client.PeerInfo.install(edition);
        LocalAutopilot autopilot = new LocalAutopilot();
        autopilot.install();
        Events.on(ClientLoadEvent.class, event -> {
            HostPanel panel = new HostPanel(actions, bossRounds, presets, edition, routes, schematics, console, autopilot);
            ModKeys keys = new ModKeys(edition, panel, routes);
            panel.setKeys(keys);
            var workspace=new org.schemetools.client.ToolWorkspace(panel, edition, routes, keys);
            if(edition.events)new org.schemetools.client.CombatDock(panel,bossRounds,workspace);
            Vars.ui.settings.addCategory(Core.bundle.get(edition.titleKey), table -> {
                table.checkPref("schemetools-hud", true);
                table.button(Core.bundle.get("st.settings.title"), () -> new ToolSettingsDialog(keys, routes, panel).show()).row();
            });
            Log.info("Scheme-Size initialized edition: @", edition.name());
        });
        Events.on(ResetEvent.class, event -> actions.reset());
        Events.on(WorldLoadEvent.class, event -> actions.reset());
    }
}
