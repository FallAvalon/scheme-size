package org.schemetools.client;

import arc.Core;
import arc.scene.ui.layout.Table;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.type.Item;
import mindustry.type.UnitType;
import mindustry.ui.dialogs.BaseDialog;
import org.schemetools.event.BossPresetStore;
import org.schemetools.event.BossCombatSettings;
import org.schemetools.event.BossRound;
import org.schemetools.event.BossRoundController;
import org.schemetools.event.BossRoundPreset;
import org.schemetools.host.HostActions;
import org.schemetools.host.ArenaTools;
import org.schemetools.Edition;
import org.schemetools.schematic.SchematicStore;
import org.schemetools.host.HostActions.ItemAdjustment;
import org.schemetools.host.HostActions.SpawnResult;
import org.schemetools.shared.PlayerKey;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/** Match organizer UI. Every shared-world operation is checked again by HostActions. */
public final class HostPanel extends ToolDialog {
    private final HostActions actions;
    private final BossRoundController bossRounds;
    private final BossPresetStore presetStore;
    private final ArenaTools arenaTools;
    private final BuildTools buildTools = new BuildTools();
    private final ClajService claj = new ClajService();
    private final org.schemetools.host.BuildingStamp stampTools;
    private final Edition edition;
    private final RouteOverlay routes;
    private final SchematicStore schematicStore;
    private final LocalConsole console;
    private final LocalAutopilot autopilot;
    private ModKeys keys;
    private HeldQuickMenu quickMenu;
    HeldQuickMenu quickMenu(){if(quickMenu==null)quickMenu=new HeldQuickMenu(schematicStore);return quickMenu;}
    private final java.util.Map<String, ToolDialog> views = new java.util.HashMap<>();
    private void view(String id, java.util.function.Supplier<ToolDialog> factory) {
        views.computeIfAbsent(id, ignored -> factory.get()).show();
    }
    public void openRoutes() { view("routes", () -> new RoutesDialog(routes)); }


    private String page = "overview";
    private boolean advanced;
    private int displayedPlayerCount=-1;
    @Override public boolean compactContent(){return "overview".equals(page)||"players".equals(page)||"items".equals(page)||"units".equals(page);}
    @Override public void refreshWidth(){rebuild();}
    private BossRound.Phase displayedPhase;
    private boolean displayedDisconnected;
    private int teamIndex;
    private int coreIndex;
    private int itemIndex;
    private int unitIndex;
    private boolean unitsCreate;
    private boolean spawnTeams;
    private String selectedPlayerUuid;
    private String playerSearch = "";
    private String itemDelta = "100";
    private String spawnCount = "1";
    private String announcement = "";

    private int bossTeamIndex;
    private int hunterTeamIndex = 1;
    private String preparationSeconds = "30";
    private String durationSeconds = "300";
    private String deathPenaltySeconds = "30";
    private BossRound.BossDeath deathPolicy = BossRound.BossDeath.HUNTERS_WIN;
    private BossRound.Disconnect disconnectPolicy = BossRound.Disconnect.PAUSE;
    private BossCombatSettings combatSettings = BossCombatSettings.defaults();

    private String presetName = "";
    private String presetDescription = "";
    private String selectedPresetName;
    private List<BossRoundPreset> presets = List.of();
    private String presetLoadError;
    private Table playerListTable;
    private Table playerDetailsTable;
    private Table presetListTable;
    private Table presetDetailsTable;

    public HostPanel(HostActions actions, BossRoundController bossRounds, BossPresetStore presetStore) {
        this(actions, bossRounds, presetStore, Edition.GOD);
    }

    public HostPanel(HostActions actions, BossRoundController bossRounds, BossPresetStore presetStore, Edition edition) {
        this(actions, bossRounds, presetStore, edition, new RouteOverlay());
    }

    public HostPanel(HostActions actions, BossRoundController bossRounds, BossPresetStore presetStore, Edition edition, RouteOverlay routes) {
        this(actions, bossRounds, presetStore, edition, routes, new SchematicStore(Vars.modDirectory.child("scheme-size-shared-data/schematics").file().toPath()));
    }

    public HostPanel(HostActions actions, BossRoundController bossRounds, BossPresetStore presetStore, Edition edition, RouteOverlay routes, SchematicStore schematicStore) {
        this(actions, bossRounds, presetStore, edition, routes, schematicStore, new LocalConsole(actions));
    }

    public HostPanel(HostActions actions, BossRoundController bossRounds, BossPresetStore presetStore, Edition edition, RouteOverlay routes, SchematicStore schematicStore, LocalConsole console) {
        this(actions, bossRounds, presetStore, edition, routes, schematicStore, console, new LocalAutopilot());
    }

    public HostPanel(HostActions actions, BossRoundController bossRounds, BossPresetStore presetStore, Edition edition, RouteOverlay routes, SchematicStore schematicStore, LocalConsole console, LocalAutopilot autopilot) {
        super(text(edition.titleKey));
        title.setEllipsis(true);
        this.edition = Objects.requireNonNull(edition);
        this.routes = Objects.requireNonNull(routes);
        this.schematicStore = Objects.requireNonNull(schematicStore);
        this.console = Objects.requireNonNull(console);
        this.autopilot = Objects.requireNonNull(autopilot);
        this.actions = Objects.requireNonNull(actions);
        this.bossRounds = edition.events ? Objects.requireNonNull(bossRounds) : null;
        this.presetStore = edition.events ? Objects.requireNonNull(presetStore) : null;
        arenaTools = new ArenaTools(actions);
        stampTools = new org.schemetools.host.BuildingStamp(actions);
        arc.Events.on(mindustry.game.EventType.WorldLoadEvent.class,event->stampTools.clear());
        arc.Events.on(mindustry.game.EventType.ResetEvent.class,event->stampTools.clear());
        buildTools.install();
        arc.Events.on(mindustry.game.EventType.WorldLoadEvent.class, event -> views.clear());
        claj.install();
        arc.Events.on(mindustry.game.EventType.WorldLoadEvent.class, event -> arenaTools.clear());
        arc.Events.on(mindustry.game.EventType.ResetEvent.class, event -> arenaTools.clear());
        addCloseButton();
        shown(this::rebuild);
        cont.update(() -> {if("players".equals(page) && displayedPlayerCount!=Groups.player.size()){displayedPlayerCount=Groups.player.size();refreshPlayerWidgets();}if(edition.events && "event".equals(page) && (displayedPhase!=bossRounds.phase() || displayedDisconnected!=bossRounds.bossDisconnected())){displayedPhase=bossRounds.phase();displayedDisconnected=bossRounds.bossDisconnected();rebuild();}});
    }

    private static String text(String key) { return Core.bundle.get(key); }

    public void setKeys(ModKeys keys) { this.keys = keys; }
    public boolean canOrganize(){return actions.isAvailable();}
    private void sameCombat(long session){if(bossRounds.sessionId()!=session||!bossRounds.isActive())throw new IllegalStateException("st.combat.changed");}
    public void combatPause(){long session=bossRounds.sessionId();boolean resume=bossRounds.phase()==BossRound.Phase.PAUSED;quickConfirm(text(resume?"st.boss.confirm-resume":"st.boss.confirm-pause"),()->perform(()->{sameCombat(session);if(resume)bossRounds.resume();else bossRounds.pause();}));}
    public void combatFinish(){long session=bossRounds.sessionId();confirm(text("st.boss.confirm-finish"),()->perform(()->{sameCombat(session);bossRounds.finish(BossRound.Outcome.ABORTED);}));}
    public void combatMessage(){
        String message=Core.settings.getString("schemesize-combat-message","");
        if(message.isBlank())Vars.ui.showTextInput(text("st.announce"),"",200,"",value->{Core.settings.put("schemesize-combat-message",value);perform(()->actions.announce(value));});
        else perform(()->actions.announce(message));
    }
    public String combatSpawnLabel(){UnitType type=selectedUnit();return (type==null?text("st.units"):type.localizedName)+" ×"+spawnCount;}
    public void combatSpawnSettings(){unitsCreate=true;openPage("units");}
    public void combatSpawn(){attempt(()->{
        if(!actions.isAvailable())throw new IllegalStateException("st.error.authority");
        Team team=selectedTeam();UnitType selected=selectedUnit();int count=parseInt(spawnCount,1,actions.maxSpawnBatch(),"st.error.input");
        new AreaSelectionDialog(1,point->quickConfirm(Core.bundle.format("st.workspace.spawn-confirm",selected.localizedName,count,point.x1(),point.y1()),()->perform(()->{
            SpawnResult result=actions.spawn(team,selected,point.x1()*Vars.tilesize,point.y1()*Vars.tilesize,count);return Core.bundle.format("st.spawn-result",result.spawned());
        })),selected,team,count).show();
    });}
    public void refreshLayout() {
        if (("players".equals(page) || "arena".equals(page)) && !Core.settings.getBool("schemesize-module-" + page, true)) page = "overview";
        if (isShown()) rebuild();
    }
    public void openPage(String name) { title.setText(text("st."+name)); selectPage(name); show(); }
    public void openSchematics() { view("schematics", () -> new SchematicsDialog(schematicStore, arenaTools)); }
    public void openQuickSchematics() { view("openQuickSchematics", () -> new QuickSchematicsDialog(schematicStore)); }
    public void openBuildTools() { view("openBuildTools", () -> new BuildToolsDialog(buildTools,actions)); }
    public void openArenaTools() { view("openArenaTools", () -> new ArenaDialog(arenaTools,stampTools)); }
    public void openConsole() { view("openConsole", () -> new LocalConsoleDialog(console)); }
    public void openVisuals() { view("openVisuals", () -> new VisualLayersDialog()); }
    public void openAutopilot() { view("openAutopilot", () -> new AutopilotDialog(autopilot, this::hide)); }
    public void toggleAutopilot() { if (autopilot.mode() == LocalAutopilot.Mode.OFF) openAutopilot(); else autopilot.stop(); }
    public void stopAutopilot() { autopilot.stop(); }
    public void stopPendingItemDrop() { buildTools.cancelPreferredDrop(); }
    public boolean organizerEdition(){return edition.events;}
    public String authorityDescription(){return actions.isAvailable()?text("st.authority"):actions.hasGame()?text("st.readonly"):text("st.no-game");}
    public void stopBuildingTasks(){buildTools.cancelPreferredDrop();buildTools.cancelReplacement();}
    public void stopConsoleTasks(){console.stopAll();}
    public void stopLocalTasks(){autopilot.stop();buildTools.cancelPreferredDrop();buildTools.cancelReplacement();console.stopAll();TerrainPaste.cancel();}
    public void openUpdates() { view("openUpdates", () -> new UpdateDialog(edition)); }
    public void openClaj() { view("openClaj", () -> new ClajDialog(claj)); }
    public void openUnitTools() { view("openUnitTools", () -> new UnitToolsDialog(actions)); }
    public void openSelfTools() { new PlayerToolsDialog(actions, Vars.player).show(); }
    public void teleportToCursor() {
        try {
            boolean remote=CursorTeleport.move(actions);
            autopilot.stop();
            Vars.ui.showInfoFade(text(remote?"st.teleport.requested":"st.teleport.done"));
        } catch (IllegalArgumentException | IllegalStateException failure) {
            Vars.ui.showInfoFade(text(failure.getMessage() == null ? "st.error.input" : failure.getMessage()));
        }
    }
    public void restoreBuild() {
        try { Vars.ui.showInfoFade(Core.bundle.format("st.build.result", buildTools.restoreDeleted())); }
        catch (RuntimeException failure) { showError(failure.getMessage() == null ? "st.error.input" : failure.getMessage()); }
    }

    private Team selectedTeam() {
        Team[] teams = Team.baseTeams;
        return teams.length <= 1 ? null : teams[1 + Math.floorMod(teamIndex, teams.length - 1)];
    }

    private Team eventTeam(int index) {
        Team[] teams = Team.baseTeams;
        return teams.length <= 1 ? null : teams[1 + Math.floorMod(index, teams.length - 1)];
    }

    private CoreBuildSelection selectedCore() {
        Team team = selectedTeam();
        if (team == null || team.cores().size == 0) return null;
        int index = Math.floorMod(coreIndex, team.cores().size);
        return new CoreBuildSelection(team.cores().get(index), index + 1, team.cores().size);
    }

    private Item selectedItem() {
        var items = Vars.content.items();
        return items.size == 0 ? null : items.get(Math.floorMod(itemIndex, items.size));
    }

    private List<UnitType> availableUnits() {
        List<UnitType> result = new ArrayList<>();
        var units = Vars.content.units();
        for (int i = 0; i < units.size; i++) {
            UnitType type = units.get(i);
            if (actions.godMode() || (!type.isHidden() && !type.isBanned() && type.segmentUnits <= 1)) result.add(type);
        }
        return result;
    }

    private UnitType selectedUnit() {
        List<UnitType> units = availableUnits();
        return units.isEmpty() ? null : units.get(Math.floorMod(unitIndex, units.size()));
    }

    private Player findPlayer(String uuid) {
        if (uuid == null) return null;
        for (Player player : Groups.player.copy()) {
            if (Objects.equals(uuid, PlayerKey.of(player))) return player;
        }
        return null;
    }

    private Player selectedPlayer() { return findPlayer(selectedPlayerUuid); }

    private List<Player> matchingPlayers() {
        String query = Strings.stripColors(playerSearch == null ? "" : playerSearch).trim().toLowerCase(Locale.ROOT);
        List<Player> result = new ArrayList<>();
        for (Player player : Groups.player.copy()) {
            String name = Strings.stripColors(player.name);
            if (query.isEmpty() || name.toLowerCase(Locale.ROOT).contains(query)) result.add(player);
        }
        result.sort(Comparator.comparing(p -> Strings.stripColors(p.name), String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private void selectPage(String name) {
        if (!edition.events && ("event".equals(name) || "presets".equals(name) || "teams".equals(name))) name = "overview";
        page = name;
        if ("presets".equals(name)) refreshPresets();
        rebuild();
    }

    private void rebuild() {
        if(compactContent()){Table root=ToolUi.content(this);switch(page){case "overview"->overviewCompact(root);case "players"->players(root);case "items"->items(root);case "units"->units(root);}return;}
        playerListTable = null;
        playerDetailsTable = null;
        presetListTable = null;
        presetDetailsTable = null;
        cont.clear();
        boolean hosted = ToolWorkspace.active != null;
        float width = hosted ? ToolWorkspace.contentWidth() - 48 : Math.min(760f, Math.max(280f, Core.scene.getWidth() / UiSizing.scale() * .9f));
        float height = Math.min(620f, Math.max(180f, UiSizing.height() * .7f));
        cont.pane(root -> {
            root.defaults().pad(4f);
            if (!hosted) { root.table(menu -> {
                int columns = width < 450f ? 2 : width < 650f ? 3 : 4;
                menu.defaults().size((Math.min(UiSizing.width(UiSizing.width(650f)), width - 64f) - 24f) / columns, 42f).pad(2f);
                String[] pages = {"overview", "players", "teams", "items", "units", "rules", "arena", "event", "presets"};
                for (int i = 0; i < pages.length; i++) {
                    String name = pages[i];
                    if (!edition.events && ("event".equals(name) || "presets".equals(name) || "teams".equals(name))) continue;
                    if (("players".equals(name) || "arena".equals(name)) && !Core.settings.getBool("schemesize-module-" + name, true)) continue;
                    menu.button(text("st." + name), () -> selectPage(name));
                    if ((i + 1) % columns == 0) menu.row();
                }
            }).row();
            root.button(text("st.settings.title"), () -> new ToolSettingsDialog(keys, routes, this).show()).height(42f).row();
            if (keys != null) root.button(text("st.actions.title"), () -> { hide(); new ActionSearchDialog(keys).show(); }).height(42f).row();
            }
            if(!hosted)root.label(() -> actions.isAvailable() ? text("st.authority")
                    : actions.hasGame() ? text("st.readonly") : text("st.no-game"))
                .wrap().width(width - 32f).pad(8f).row();
            switch (page) {
                case "players" -> players(root);
                case "teams" -> teams(root);
                case "items" -> items(root);
                case "units" -> units(root);
                case "rules" -> rules(root);
                case "event" -> event(root);
                case "presets" -> presetPage(root);
                case "arena" -> {
                    root.add(text("st.arena.help")).wrap().width(UiSizing.width(650f)).row();
                    root.button(text("st.arena.title"), this::openArenaTools).disabled(!actions.isAvailable()).row();
                }
                default -> {
                    if(hosted) {
                        for(String target : new String[]{"players","items","units","rules","teams","presets"}) {
                            if(!edition.events && (target.equals("teams") || target.equals("presets"))) continue;
                            root.button(text("st."+target), () -> openPage(target)).growX().row();
                        }
                    }
                    overview(root);
                }
            }
        }).width(width).maxHeight(height);
    }

    private void teamControl(Table root, String labelKey, Consumer<Team> selected) {
        Team team = selectedTeam();
        root.table(row -> {
            row.add(text(labelKey) + ": " + (team == null ? "—" : team.localized())).wrap().width(UiSizing.width(300f)).left();
            row.button("‹", () -> { teamIndex--; coreIndex = 0; rebuild(); }).size(44f);
            row.button("›", () -> { teamIndex++; coreIndex = 0; rebuild(); }).size(44f);
        }).row();
        if (selected != null) selected.accept(team);
    }

    private void playerPicker(Table root, boolean compact) {
        Player player = selectedPlayer();
        root.table(row -> {
            row.add(text("st.player") + ": " + (player == null ? text("st.no-player")
                : Strings.stripColors(player.name))).wrap().width(UiSizing.width(compact ? 300f : 440f)).left();
            row.button("‹", () -> stepPlayer(-1)).size(44f);
            row.button("›", () -> stepPlayer(1)).size(44f);
        }).row();
    }

    private void stepPlayer(int offset) {
        List<Player> players = new ArrayList<>();
        for (Player player : Groups.player.copy()) players.add(player);
        if (players.isEmpty()) {
            selectedPlayerUuid = null;
        } else {
            int current = -1;
            for (int i = 0; i < players.size(); i++)
                if (Objects.equals(PlayerKey.of(players.get(i)), selectedPlayerUuid)) current = i;
            selectedPlayerUuid = PlayerKey.of(players.get(Math.floorMod(current + offset, players.size())));
        }
        if (playerListTable != null) refreshPlayerWidgets();
        else rebuild();
    }

    private void numberField(Table root, String labelKey, String value, Consumer<String> setter, float width) {
        root.table(row -> {
            row.add(text(labelKey)).wrap().width(UiSizing.width(300f)).left();
            row.field(value, setter::accept).width(UiSizing.width(width));
        }).row();
    }

    private int parseInt(String value, int min, int max, String errorKey) {
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < min || parsed > max) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(errorKey);
        }
    }

    private double parseDouble(String value, double min, double max, String errorKey) {
        try {
            double parsed = Double.parseDouble(value.trim());
            if (!Double.isFinite(parsed) || parsed < min || parsed > max) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(errorKey);
        }
    }

    private void overviewCompact(Table root){
        for(String target:new String[]{"players","items","units","rules","teams","presets"}){
            if(!edition.events&&(target.equals("teams")||target.equals("presets")))continue;
            if((target.equals("players")||target.equals("items")||target.equals("units"))&&!Core.settings.getBool("schemesize-module-players",true))continue;
            ToolUi.action(root,text("st."+target),"st.help.overview",()->openPage(target));
        }
        String[] labels={"st.wave.open","st.coreinfo.open","st.build.title","st.routes.title","st.schematic.title","st.quick-schematics","st.console.title","st.visual.title","st.power.title","st.ai.title"};
        Runnable[] runs={()->new WaveForecastDialog().show(),()->new CoreInfoDialog().show(),this::openBuildTools,this::openRoutes,this::openSchematics,this::openQuickSchematics,this::openConsole,this::openVisuals,()->new PowerInfoDialog().show(),this::openAutopilot};
        String[] modules={"info","info","build","routes","schematics","schematics","console","visual","info","ai"};
        for(int i=0;i<labels.length;i++)if(Core.settings.getBool("schemesize-module-"+modules[i],true))ToolUi.action(root,text(labels[i]),"st.help.overview",runs[i]).setDisabled(i==6?!actions.isAvailable():i!=4&&i!=7&&!actions.hasGame());
        ToolUi.action(root,text("st.toggle-waves"),"st.rules-help",()->quickConfirm(text("st.confirm-rules"),()->perform(()->{actions.waves(!Vars.state.rules.waves);return text("st.rules-applied");})));
        ToolUi.action(root,text("st.players-refresh"),"st.help.overview",()->{refreshPlayerWidgets();Vars.ui.showInfoFade(text("st.players-refreshed"));});
        ToolUi.field(root,text("st.announce"),announcement,value->announcement=limit(value,200));
        ToolUi.action(root,text("st.announce"),"st.help.overview",()->perform(()->{actions.announce(announcement);return Core.bundle.format("st.announce-sent",announcement.strip());}));
        ToolUi.action(root,text("st.settings.title"),"st.settings.help",()->new ToolSettingsDialog(keys,routes,this).show());
        ToolUi.action(root,text("st.match-overview"),"st.help.overview",()->{
            String details=text("st.map")+": "+mapName()+"\n"+text("st.mode")+": "+matchMode()+"\n"+text("st.match-state")+": "+matchState()+"\n"+Core.bundle.format("st.player-count",Groups.player.size(),teamCountText());
            if(edition.events)details+="\n"+text("st.boss.phase")+": "+text("st.boss.phase."+bossRounds.phase().name().toLowerCase(Locale.ROOT))+"\n"+Core.bundle.format("st.boss.score",bossRounds.bossScore(),bossRounds.hunterScore());
            ToolUi.helpText(text("st.match-overview"),details);
        });
    }
    private void overview(Table root) {
        root.add(text("st.match-overview")).left().row();
        root.table(info -> {
            info.defaults().left().pad(3f);
            info.label(() -> text("st.map") + ": " + mapName()).wrap().width(UiSizing.width(650f)).row();
            info.label(() -> text("st.mode") + ": " + matchMode()).row();
            info.label(() -> text("st.match-state") + ": " + matchState()).row();
            info.label(() -> Core.bundle.format("st.player-count", Groups.player.size(), teamCountText()))
                .wrap().width(UiSizing.width(650f)).row();
            if (edition.events) {
                info.label(() -> text("st.boss.phase") + ": "
                + text("st.boss.phase." + bossRounds.phase().name().toLowerCase(Locale.ROOT))).row();
            info.label(() -> Core.bundle.format("st.boss.score", bossRounds.bossScore(), bossRounds.hunterScore()))
                .row();
            }
        }).left().row();
        root.add(text("st.quick-actions")).left().padTop(10f).row();
        root.table(row -> {
            row.defaults().size(UiSizing.width(300f), 44f).pad(3f);
            row.button(text("st.toggle-waves"), () -> quickConfirm(text("st.confirm-rules"), () -> perform(() -> {
                actions.waves(!Vars.state.rules.waves);
                return text("st.rules-applied");
            })))
                .disabled(!actions.isAvailable());
            row.button(text("st.players-refresh"), () -> {
                if (playerListTable != null) refreshPlayerWidgets();
                Vars.ui.showInfoFade(text("st.players-refreshed"));
            });
        }).row();
        root.button(text("st.wave.open"), () -> new WaveForecastDialog().show())
            .width(UiSizing.width(240f)).height(44f).disabled(!actions.hasGame()).visible(() -> Core.settings.getBool("schemesize-module-info", true)).row();
        root.button(text("st.coreinfo.open"), () -> new CoreInfoDialog().show())
            .width(UiSizing.width(240f)).height(44f).disabled(!actions.hasGame()).visible(() -> Core.settings.getBool("schemesize-module-info", true)).row();
        root.button(text("st.build.title"), this::openBuildTools)
            .width(UiSizing.width(240f)).height(44f).disabled(!actions.hasGame()).visible(() -> Core.settings.getBool("schemesize-module-build", true)).row();
        root.button(text("st.routes.title"), () -> new RoutesDialog(routes).show())
            .width(UiSizing.width(240f)).height(44f).disabled(!actions.hasGame()).visible(() -> Core.settings.getBool("schemesize-module-routes", true)).row();
        root.button(text("st.schematic.title"), this::openSchematics).width(UiSizing.width(240f)).height(44f)
            .visible(() -> Core.settings.getBool("schemesize-module-schematics", true)).row();
        root.button(text("st.quick-schematics"), this::openQuickSchematics).width(UiSizing.width(240f)).height(44f).disabled(!actions.hasGame())
            .visible(() -> Core.settings.getBool("schemesize-module-schematics", true)).row();
        root.button(text("st.console.title"), this::openConsole).width(UiSizing.width(240f)).height(44f).disabled(!actions.isAvailable())
            .visible(() -> Core.settings.getBool("schemesize-module-console", true)).row();
        root.button(text("st.visual.title"), this::openVisuals).width(UiSizing.width(240f)).height(44f)
            .visible(() -> Core.settings.getBool("schemesize-module-visual", true)).row();
        root.button(text("st.power.title"), () -> new PowerInfoDialog().show()).width(UiSizing.width(240f)).height(44f).disabled(!actions.hasGame())
            .visible(() -> Core.settings.getBool("schemesize-module-info", true)).row();
        root.button(text("st.ai.title"), this::openAutopilot).width(UiSizing.width(240f)).height(44f).disabled(!actions.hasGame())
            .visible(() -> Core.settings.getBool("schemesize-module-ai", true)).row();
        root.field(announcement, value -> announcement = limit(value, 200)).width(UiSizing.width(560f)).maxTextLength(200).row();
        root.button(text("st.announce"), () -> perform(() -> {
            actions.announce(announcement);
            return Core.bundle.format("st.announce-sent", announcement.strip());
        })).width(UiSizing.width(220f)).height(44f).disabled(!actions.isAvailable()).row();
    }

    private String mapName() {
        return Vars.state == null || Vars.state.map == null ? "—" : Vars.state.map.plainName();
    }

    private String matchMode() {
        if (!actions.hasGame()) return text("st.mode.none");
        if (Vars.state.rules.pvp) return text("st.mode.pvp");
        if (Vars.state.rules.waves) return text("st.mode.waves");
        return text("st.mode.sandbox");
    }

    private String matchState() {
        if (!actions.hasGame()) return text("st.match.none");
        if (Vars.state.isPaused()) return text("st.match.paused");
        if (Vars.state.gameOver) return text("st.match.over");
        return Core.bundle.format("st.match.wave", Vars.state.wave);
    }

    private String teamCountText() {
        StringBuilder result = new StringBuilder();
        for (Team team : Team.baseTeams) {
            int count = 0;
            for (Player player : Groups.player.copy()) if (player.team() == team) count++;
            if (count == 0) continue;
            if (!result.isEmpty()) result.append("  ·  ");
            result.append(team.localized()).append(": ").append(count);
        }
        return result.isEmpty() ? text("st.no-players") : result.toString();
    }

    private static String displayName(Player player){String name=Strings.stripColors(player.name);return name.isBlank()?Core.bundle.get("st.player")+" #"+player.id:name;}
    private void players(Table root){
        if(edition.events&&bossRounds.isActive()){combatPlayers(root);return;}
        ToolUi.action(root,text("st.workspace.player-point"),"st.help.player-map",()->new AreaSelectionDialog(1,point->{
            Player nearest=null;float distance=Float.MAX_VALUE;
            for(Player candidate:Groups.player.copy()){float dst=candidate.dst(point.x1()*Vars.tilesize,point.y1()*Vars.tilesize);if(dst<distance){distance=dst;nearest=candidate;}}
            if(nearest==null||distance>Vars.tilesize*5){showError("st.error.input");return;}
            selectedPlayerUuid=PlayerKey.of(nearest);refreshPlayerWidgets();
        }).show());
        ToolUi.field(root,text("st.search"),playerSearch,value->{playerSearch=limit(value,80);refreshPlayerWidgets();});
        playerListTable=new Table();root.pane(playerListTable).width(ToolUi.width()).maxHeight(160).scrollX(false).row();
        playerDetailsTable=new Table();playerDetailsTable.defaults().width(ToolUi.width()).padBottom(6).left();root.add(playerDetailsTable).row();refreshPlayerWidgets();
    }
    private void refreshPlayerWidgets(){
        if("players".equals(page)&&edition.events&&bossRounds.isActive()){rebuild();return;}
        if(playerListTable==null||playerDetailsTable==null)return;
        var players=matchingPlayers();
        if(selectedPlayer()==null||players.stream().noneMatch(p->Objects.equals(PlayerKey.of(p),selectedPlayerUuid)))selectedPlayerUuid=players.isEmpty()?null:PlayerKey.of(players.get(0));
        playerListTable.clearChildren();
        for(Player player:players){
            String uuid=PlayerKey.of(player),name=displayName(player);
            var button=playerListTable.button((Objects.equals(uuid,selectedPlayerUuid)?"▶ ":"")+name,()->{selectedPlayerUuid=uuid;refreshPlayerWidgets();}).width(ToolUi.width()-24).minHeight(40).get();
            button.getLabel().setWrap(true);playerListTable.row();
        }
        if(players.isEmpty())playerListTable.add(text("st.no-matching-players")).wrap().width(ToolUi.width()-24).row();
        playerListTable.invalidateHierarchy();playerDetailsTable.clearChildren();
        Player selected=selectedPlayer();if(selected==null)return;
        playerDetailsTable.table(row->{
            row.add(displayName(selected)).wrap().width(ToolUi.width()-44).left();
            row.button("?",()->ToolUi.helpText(displayName(selected),Core.bundle.format("st.player-detail",displayName(selected),selected.team().localized(),selected.unit()==null?text("st.player-no-unit"):selected.unit().type().localizedName)+"\n"+PeerInfo.describe(selected))).size(40,40).padLeft(4).tooltip(text("st.help.read"));
        }).row();
        String chosenUuid=selectedPlayerUuid;
        playerDetailsTable.table(row->{
            var watch=row.button(text("st.combat.watch"),()->attempt(()->{
                Player current=findPlayer(chosenUuid);if(current==null||current.dead()||!actions.isAvailable()&&current.unit().inFogTo(Vars.player.team()))throw new IllegalArgumentException("st.no-player");
                Vars.control.input.spectate(current.unit());
            })).width((ToolUi.width()-4)/2).height(38).get();watch.name="st-player-watch-inline";
            var bring=row.button(text("st.combat.bring"),()->quickConfirm(text("st.player-to-me"),()->perform(()->actions.teleport(findPlayer(chosenUuid),Vars.player.x,Vars.player.y)))).width((ToolUi.width()-4)/2).height(38).padLeft(4).disabled(!actions.isAvailable()).get();bring.name="st-player-bring-inline";
        }).row();
        ToolUi.action(playerDetailsTable,text("st.combat.more-player"),"st.help.player-actions",()->{Player current=selectedPlayer();if(current!=null)new PlayerToolsDialog(actions,current).show();});
        ToolUi.action(playerDetailsTable,text("st.team")+": "+selected.team().localized(),"st.help.player-team",()->{
            BaseDialog picker=new BaseDialog(text("st.team"));picker.name="st-modal";picker.addCloseButton();
            for(Team destination:Team.baseTeams)if(HostActions.isValidTeam(destination))picker.cont.button(destination.localized(),()->{
                String uuid=selectedPlayerUuid;String name=displayName(selectedPlayer());picker.hide();
                quickConfirm(Core.bundle.format("st.confirm-team-detail",name,destination.localized()),()->perform(()->{actions.team(findPlayer(uuid),destination);refreshPlayerWidgets();return Core.bundle.format("st.team-changed",name,destination.localized());}));
            }).width(240).height(44).row();picker.show();
        }).setDisabled(!actions.isAvailable());

    }

    private boolean destinationUnavailable(Team team) { return team == null || selectedPlayer() == null; }

    /** In a fight the selector stays one row; the selected player's actions stay directly below it. */
    private void combatPlayers(Table root){
        playerListTable=null;playerDetailsTable=null;
        if(selectedPlayer()==null){var found=matchingPlayers();selectedPlayerUuid=found.isEmpty()?null:PlayerKey.of(found.get(0));}
        Player selected=selectedPlayer();if(selected==null){root.add(text("st.no-player")).row();return;}
        String uuid=selectedPlayerUuid;
        root.button(displayName(selected)+" ▾",()->{
            BaseDialog picker=new BaseDialog(text("st.players"));picker.name="st-modal";picker.addCloseButton();
            Table list=new Table();Runnable refill=()->{list.clearChildren();for(Player p:matchingPlayers()){String key=PlayerKey.of(p);list.button(displayName(p),()->{selectedPlayerUuid=key;picker.hide();rebuild();}).width(240).height(38).row();}};
            picker.cont.field(playerSearch,value->{playerSearch=limit(value,80);refill.run();}).width(260).row();picker.cont.pane(list).width(260).maxHeight(UiSizing.height()*.45f).scrollX(false);refill.run();picker.show();
        }).minHeight(38).get().name="st-combat-player-selector";
        root.row();
        root.table(row->{
            row.button(text("st.combat.watch"),()->attempt(()->{Player p=findPlayer(uuid);if(p==null||p.dead())throw new IllegalArgumentException("st.no-player");Vars.control.input.spectate(p.unit());})).width((ToolUi.width()-4)/2).height(36).get().name="st-player-watch-inline";
            row.button(text("st.combat.bring"),()->quickConfirm(text("st.player-to-me"),()->perform(()->actions.teleport(findPlayer(uuid),Vars.player.x,Vars.player.y)))).width((ToolUi.width()-4)/2).height(36).padLeft(4).disabled(!actions.isAvailable()).get().name="st-player-bring-inline";
        }).row();
        root.table(row->{for(Team team:Team.baseTeams)if(HostActions.isValidTeam(team)){
            var style=new arc.scene.ui.ImageButton.ImageButtonStyle(mindustry.ui.Styles.clearTogglei);style.imageUpColor=team.color;style.imageCheckedColor=team.color;style.imageDownColor=team.color;var button=row.button(mindustry.gen.Tex.whiteui,style,()->quickConfirm(text("st.team")+": "+team.localized(),()->perform(()->actions.team(findPlayer(uuid),team)))).size(30).pad(2).tooltip(team.localized()).get();button.getImageCell().size(16);button.setChecked(selected.team()==team);button.setDisabled(!actions.isAvailable());button.name="st-combat-player-team-"+team.id;
        }}).row();
        ToolUi.action(root,text("st.combat.more-player"),"st.help.player-actions",()->{Player current=findPlayer(uuid);if(current!=null)new PlayerToolsDialog(actions,current).show();});
    }

    private void compactTeam(Table root){
        int columns=ToolUi.width()>=320?2:1;float width=(ToolUi.width()-4*(columns-1))/columns;
        root.table(row->{
            for(int i=1;i<Team.baseTeams.length;i++){
                final int index=i-1;Team team=Team.baseTeams[i];
                var button=row.button(team.localized(),ToolUi.toggleStyle(),()->{teamIndex=index;coreIndex=0;spawnTeams=false;rebuild();}).width(width).minHeight(36).padBottom(4).padRight(4).get();
                button.getLabel().setColor(team.color);button.getLabel().setWrap(true);button.setChecked(selectedTeam()==team);button.name="st-create-team-"+team.id;
                if(i%columns==0)row.row();
            }
        }).row();
    }
    private void spawnTeamChoices(Table root){
        Team current=selectedTeam();Team extra=current!=Team.sharded&&current!=Team.crux?current:Team.blue;
        int columns=ToolUi.width()>=320?2:1;float width=(ToolUi.width()-4*(columns-1))/columns;
        root.table(row->{int index=0;
            for(Team team:new Team[]{Team.sharded,Team.crux,extra}){
                var style=ToolUi.toggleStyle();style.fontColor=team.color;style.checkedFontColor=team.color;
                var button=row.button(team.localized(),style,()->{teamIndex=baseTeamIndex(team);coreIndex=0;spawnTeams=false;rebuild();}).width(width).height(36).padRight(4).padBottom(4).get();
                button.margin(4);button.getLabel().setFontScale(.8f);button.getLabel().setEllipsis(true);button.setChecked(current==team);button.name="st-create-team-"+team.id;
                if(++index%columns==0)row.row();
            }
            row.button(text("st.ui.other-teams"),()->{spawnTeams=!spawnTeams;rebuild();}).width(width).height(36).padBottom(4).get().getLabel().setFontScale(.8f);
        }).row();
        if(spawnTeams)compactTeam(root);
    }
    private void changeSpawnCount(int delta){
        long count;try{count=Long.parseLong(spawnCount.trim());}catch(NumberFormatException ignored){count=1;}
        spawnCount=""+Math.max(1,Math.min(actions.maxSpawnBatch(),count+(long)delta));rebuild();
    }
    private void items(Table root) {
        if(!actions.hasGame()){root.add(text("st.no-game")).wrap().row();return;}
        ToolUi.action(root,text("st.workspace.core-point"),"st.help.core-pick",()->new AreaSelectionDialog(1,point->{
            var target=Vars.world.build(point.x1(),point.y1());
            if(!(target instanceof mindustry.world.blocks.storage.CoreBlock.CoreBuild core)||baseTeamIndex(core.team)<0){showError("st.error.input");return;}
            teamIndex=baseTeamIndex(core.team);coreIndex=core.team.cores().indexOf(core,true);rebuild();
        }).show());
        compactTeam(root);
        Team team=selectedTeam();CoreBuildSelection selection=selectedCore();Item item=selectedItem();
        if(selection!=null){
            ToolUi.action(root,text("st.core")+" · "+selection.number+"/"+selection.total,"st.help.core-pick",()->{
                BaseDialog picker=new BaseDialog(text("st.core"));picker.name="st-modal";picker.addCloseButton();
                int i=0;for(var core:team.cores()){int index=i++;picker.cont.button(core.block.localizedName+" · "+core.tileX()+", "+core.tileY(),()->{coreIndex=index;picker.hide();rebuild();}).width(ToolUi.width()).height(44).row();}picker.show();
            });
        }
        var itemButton=ToolUi.action(root,text("st.item")+": "+(item==null?"—":item.localizedName),"st.help.item-pick",()->{
            List<Item> list=new ArrayList<>();for(Item value:Vars.content.items())list.add(value);
            ContentPicker.icons(text("st.item"),list,item,chosen->{itemIndex=Vars.content.items().indexOf(chosen,true);rebuild();});
        });
        // Show the selected native resource icon directly on the action.
        if(item!=null){itemButton.getCell(itemButton.getLabel()).growX().minWidth(0);itemButton.image(new arc.scene.style.TextureRegionDrawable(item.uiIcon)).size(32).padLeft(4);}
        ToolUi.valueField(root,text("st.short.item-delta"),itemDelta,value->itemDelta=limit(value,11));
        root.label(()->selection==null||item==null?text("st.items-current-none"):Core.bundle.format("st.items-current",selection.core.items.get(item))).wrap().row();
        ToolUi.action(root,text("st.apply"),"st.help.item-apply",()->attempt(()->{
            int delta=parseInt(itemDelta,actions.godMode()?Integer.MIN_VALUE:-HostActions.MAX_ITEM_DELTA,actions.godMode()?Integer.MAX_VALUE:HostActions.MAX_ITEM_DELTA,"st.error.input");
            quickConfirm(Core.bundle.format("st.confirm-items-detail",team.localized(),item.localizedName,delta),()->perform(()->{
                ItemAdjustment result=actions.adjustItem(team,selection==null?null:selection.core,item,delta);return Core.bundle.format("st.items-applied",result.before(),result.after());
            }));
        })).setDisabled(!actions.isAvailable()||selection==null||item==null);
        if(edition.events&&selection!=null)ToolUi.action(root,text("st.loadout.title"),"st.loadout.help",()->new ResourceLoadoutDialog(actions,team,selection.core).show());
    }
    private UnitToolsDialog unitTools(){return (UnitToolsDialog)views.computeIfAbsent("openUnitTools",ignored->new UnitToolsDialog(actions));}
    private void units(Table root) {
        ToolUi.tabs(root,text("st.roster.tab-map"),text("st.roster.tab-create"),unitsCreate,()->{unitsCreate=false;rebuild();},()->{unitsCreate=true;rebuild();});
        if(!unitsCreate){unitTools().render(root,this::rebuild);return;}
        if(!actions.hasGame()){root.add(text("st.no-game")).wrap().row();return;}
        ToolUi.heading(root,text("st.ui.step-team"),"st.help.unit-spawn");spawnTeamChoices(root);UnitType type=selectedUnit();
        ToolUi.heading(root,text("st.ui.step-unit"),"st.help.unit-pick");
        var typeButton=ToolUi.action(root,text("st.unit")+": "+(type==null?"—":type.localizedName),"st.help.unit-pick",()->ContentPicker.icons(text("st.unit"),availableUnits(),type,chosen->{unitIndex=availableUnits().indexOf(chosen);rebuild();}));
        if(type!=null)typeButton.image(type.uiIcon).size(32).padLeft(4);
        if(ToolUi.width()<320)root.add(text("st.amount")).left().row();
        root.table(row->{
            if(ToolUi.width()>=320)row.add(text("st.amount")).growX().minWidth(0).left();
            row.button("−",()->changeSpawnCount(-1)).size(36);
            row.field(spawnCount,value->spawnCount=limit(value,10)).width(ToolUi.width()>=320?80:ToolUi.width()-80).height(40).get().setMessageText(text("st.amount"));
            row.button("+",()->changeSpawnCount(1)).size(36);
        }).row();
        root.table(chips->{for(int value:new int[]{1,10,50})chips.button(""+value,ToolUi.toggleStyle(),()->{spawnCount=""+value;rebuild();}).width((ToolUi.width()-8)/3).height(36).padRight(4).get().setChecked(spawnCount.equals(""+value));}).row();
        ToolUi.heading(root,text("st.ui.step-place"),"st.help.unit-spawn");
        var place=ToolUi.action(root,text("st.workspace.point"),"st.help.unit-spawn",()->attempt(()->{
            Team team=selectedTeam();UnitType selected=selectedUnit();int count=parseInt(spawnCount,1,actions.maxSpawnBatch(),"st.error.input");
            new AreaSelectionDialog(1,point->quickConfirm(Core.bundle.format("st.workspace.spawn-confirm",selected.localizedName,count,point.x1(),point.y1()),()->perform(()->{
                SpawnResult result=actions.spawn(team,selected,point.x1()*Vars.tilesize,point.y1()*Vars.tilesize,count);return Core.bundle.format("st.spawn-result",result.spawned());
            })),selected,team,count).show();
        }));place.setDisabled(!actions.isAvailable()||type==null);ToolUi.emphasize(place,false);
        ToolUi.action(root,text("st.workspace.advanced")+(advanced?" ▴":" ▾"),"st.help.unit-near",()->{advanced=!advanced;rebuild();});
        if(!advanced)return;
        Player player=selectedPlayer();
        ToolUi.action(root,text("st.players")+": "+(player==null?"—":Strings.stripColors(player.name)),"st.help.unit-near",()->{
            BaseDialog picker=new BaseDialog(text("st.players"));picker.name="st-modal";picker.addCloseButton();
            for(Player target:Groups.player.copy())picker.cont.button(Strings.stripColors(target.name)+" #"+target.id,()->{selectedPlayerUuid=PlayerKey.of(target);picker.hide();rebuild();}).width(ToolUi.width()).height(44).row();picker.show();
        });
        ToolUi.action(root,text("st.spawn"),"st.help.unit-near",()->attempt(()->{
            String uuid=selectedPlayerUuid;Team team=selectedTeam();UnitType selected=selectedUnit();int count=parseInt(spawnCount,1,actions.maxSpawnBatch(),"st.error.input");
            quickConfirm(Core.bundle.format("st.confirm-spawn-detail",selected.localizedName,count,player.name,team.localized()),()->perform(()->{
                Player current=findPlayer(uuid);if(current==null)throw new IllegalArgumentException("st.error.input");return Core.bundle.format("st.spawn-result",actions.spawn(team,selected,current.x,current.y,count).spawned());
            }));
        })).setDisabled(!actions.isAvailable()||player==null||type==null);
    }

    private boolean teamUnavailable() { return !HostActions.isValidTeam(selectedTeam()); }

    private void teams(Table root) {
        if (!actions.hasGame()) { root.add(text("st.no-game")).row(); return; }
        root.add(text("st.teams-help")).wrap().width(UiSizing.width(650)).row();
        for (Team team : Team.baseTeams) if (HostActions.isValidTeam(team)) {
            int players = Groups.player.count(player -> player.team() == team);
            int units = team.data().units.size;
            root.add(Core.bundle.format("st.team-summary", team.localized(), players, units, team.cores().size,
                team.rules().unitHealthMultiplier, team.rules().unitDamageMultiplier)).wrap().width(UiSizing.width(650)).left().row();
        }
        teamControl(root, "st.team", null);
        root.table(row -> {
            row.button(text("st.players"), () -> selectPage("players"));
            row.button(text("st.items"), () -> selectPage("items"));
            row.button(text("st.units"), () -> selectPage("units"));
        }).row();
    }

    private void rules(Table root) {
        if (!actions.hasGame()) { root.add(text("st.no-game")).wrap().row(); return; }
        root.add(text("st.rules-help")).wrap().width(UiSizing.width(650f)).left().row();
        root.button(text("st.rules-live-title"), () -> new RulesDialog(actions, edition).show()).width(UiSizing.width(300)).disabled(!actions.isAvailable()).row();
        root.add(text("st.waves") + ": " + (Vars.state.rules.waves ? text("st.on") : text("st.off"))).left().row();
        root.button(text("st.toggle-waves"), () -> quickConfirm(text("st.confirm-rules"), () -> perform(() -> {
            actions.waves(!Vars.state.rules.waves);
            return text("st.rules-applied");
        }))).width(UiSizing.width(300f)).height(44f).disabled(!actions.isAvailable()).row();
    }

    private void event(Table root) {
        root.defaults().width(UiSizing.width(650f)).left();
        if (!actions.hasGame()) { root.add(text("st.no-game")).wrap().row(); return; }
        root.add(text("st.boss.header")).wrap().left().row();
        root.label(() -> text("st.boss.phase") + ": "
            + text("st.boss.phase." + bossRounds.phase().name().toLowerCase(Locale.ROOT))).wrap().left().row();
        root.label(() -> Core.bundle.format("st.boss.score", bossRounds.bossScore(), bossRounds.hunterScore()))
            .wrap().left().row();
        if(bossRounds.phase()==BossRound.Phase.FINISHED)root.label(() -> Core.bundle.format("st.boss.result." + bossRounds.outcome().name().toLowerCase(Locale.ROOT),
            bossRounds.bossName(), bossRounds.outcome() == BossRound.Outcome.HUNTERS
                ? bossRounds.hunterScore() : bossRounds.bossScore()))
            .wrap().width(UiSizing.width(650f)).left().visible(() -> bossRounds.phase() == BossRound.Phase.FINISHED).row();
        if(bossRounds.isActive())activeEventControls(root);else eventSetupControls(root);
    }

    private void activeEventControls(Table root) {
        root.label(() -> Core.bundle.format("st.boss.current", bossRounds.bossName())).wrap().left().row();
        root.label(() -> Core.bundle.format("st.boss.remaining", (int)Math.ceil(bossRounds.remaining())))
            .wrap().left().row();
        if(bossRounds.bossDisconnected())root.add(text("st.boss.disconnected")).wrap().width(UiSizing.width(650f)).left().visible(bossRounds::bossDisconnected).row();
        if(bossRounds.phase()!=BossRound.Phase.PAUSED)root.button(text("st.boss.pause"), () -> confirm(text("st.boss.confirm-pause"), () -> perform(() -> {
            bossRounds.pause(); return text("st.success");
        }))).width(UiSizing.width(650f)).height(48f).disabled(!actions.isAvailable())
            .visible(() -> bossRounds.phase() != BossRound.Phase.PAUSED).row();
        if(bossRounds.phase()==BossRound.Phase.PAUSED && !bossRounds.bossDisconnected())root.button(text("st.boss.resume"), () -> confirm(text("st.boss.confirm-resume"), () -> perform(() -> {
            bossRounds.resume(); return text("st.success");
        }))).width(UiSizing.width(650f)).height(48f).disabled(!actions.isAvailable())
            .visible(() -> bossRounds.phase() == BossRound.Phase.PAUSED && !bossRounds.bossDisconnected()).row();
        root.button(text("st.boss.finish"), () -> confirm(text("st.boss.confirm-finish"), () -> perform(() -> {
            bossRounds.finish(BossRound.Outcome.ABORTED); return text("st.success");
        }))).width(UiSizing.width(650f)).height(48f).disabled(!actions.isAvailable()).row();
        root.button(text("st.workspace.advanced") + (advanced ? " ▴" : " ▾"), () -> {advanced=!advanced;rebuild();}).growX().row();
        if(!advanced)return;
        playerPicker(root, true);
        root.add(text("st.boss.replace-hint")).wrap().width(UiSizing.width(650f)).left().row();
        root.button(text("st.boss.replace"), () -> {
            String uuid = selectedPlayerUuid;
            Player candidate = findPlayer(uuid);
            confirm(candidate == null ? text("st.boss.error.selection")
                : Core.bundle.format("st.boss.confirm-replace", Strings.stripColors(candidate.name),
                    bossRounds.bossName()), () -> perform(() -> {
                Player current = findPlayer(uuid);
                bossRounds.replaceBoss(current);
                return Core.bundle.format("st.boss.replaced", bossRounds.bossName());
            }));
        }).width(UiSizing.width(300f)).height(44f).disabled(!actions.isAvailable() || selectedPlayer() == null).row();
        root.add(text("st.boss.declare-result")).wrap().left().padTop(8f).row();
        root.table(row -> {
            row.defaults().width(UiSizing.width(190f)).height(44f).pad(3f);
            resultButton(row, BossRound.Outcome.HUNTERS, "st.boss.award-hunters");
            resultButton(row, BossRound.Outcome.BOSS, "st.boss.award-boss");
            resultButton(row, BossRound.Outcome.DRAW, "st.boss.award-draw");
        }).row();

    }

    private void resultButton(Table row, BossRound.Outcome outcome, String key) {
        row.button(text(key), () -> confirm(Core.bundle.format("st.boss.confirm-result", text(key)),
            () -> perform(() -> { bossRounds.finish(outcome); return text("st.success"); })))
            .disabled(!actions.isAvailable());
    }

    private void eventSetupControls(Table root) {
        playerPicker(root, true);
        eventTeamControl(root, "st.boss.team", true);
        eventTeamControl(root, "st.boss.hunter-team", false);
        root.button(text("st.presets"), () -> openPage("presets")).growX().row();
        root.button(text("st.workspace.advanced") + (advanced ? " ▴" : " ▾"), () -> {advanced=!advanced;rebuild();}).growX().row();
        if(advanced) { root.button(text("st.boss.combat"), () -> new BossCombatDialog(combatSettings, value -> combatSettings = value).show()).row();
        eventNumber(root, "st.boss.preparation", preparationSeconds, value -> preparationSeconds = limit(value, 12));
        eventNumber(root, "st.boss.duration", durationSeconds, value -> durationSeconds = limit(value, 12));
        eventNumber(root, "st.boss.death-penalty-label", deathPenaltySeconds, value -> deathPenaltySeconds = limit(value, 12));
        root.button(Core.bundle.format("st.boss.death-policy", text("st.boss.death." + deathPolicy.name().toLowerCase(Locale.ROOT))),
            () -> { deathPolicy = deathPolicy == BossRound.BossDeath.HUNTERS_WIN
                ? BossRound.BossDeath.RESPAWN : BossRound.BossDeath.HUNTERS_WIN; rebuild(); })
            .width(UiSizing.width(430f)).height(44f).disabled(!actions.isAvailable()).row();
        root.button(Core.bundle.format("st.boss.disconnect-policy", text("st.boss.disconnect." + disconnectPolicy.name().toLowerCase(Locale.ROOT))),
            () -> { disconnectPolicy = BossRound.Disconnect.values()[(disconnectPolicy.ordinal() + 1)
                % BossRound.Disconnect.values().length]; rebuild(); })
            .width(UiSizing.width(430f)).height(44f).disabled(!actions.isAvailable()).row();
        }
        root.add(text("st.boss.team-hint")).wrap().width(UiSizing.width(650f)).left().row();
        root.button(text("st.boss.start"), () -> attempt(() -> {
            Player boss = selectedPlayer();
            String uuid = selectedPlayerUuid;
            String bossLabel = boss == null ? "—" : Strings.stripColors(boss.name);
            Team selectedBossTeam = eventTeam(bossTeamIndex);
            Team selectedHunterTeam = eventTeam(hunterTeamIndex);
            double preparation = parseDouble(preparationSeconds, 0, 3600, "st.boss.error.settings");
            double duration = parseDouble(durationSeconds, 1, 86400, "st.boss.error.settings");
            double penalty = parseDouble(deathPenaltySeconds, 0, duration, "st.boss.error.settings");
            String prompt = Core.bundle.format("st.boss.confirm-start", bossLabel,
                selectedBossTeam.localized(), selectedHunterTeam.localized());
            confirm(prompt, () -> perform(() -> {
                bossRounds.start(findPlayer(uuid), selectedBossTeam, selectedHunterTeam,
                    preparation, duration, deathPolicy, penalty, disconnectPolicy, combatSettings);
                return text("st.success");
            }));
        })).width(UiSizing.width(320f)).height(48f).disabled(!actions.isAvailable() || selectedPlayer() == null).row();
    }

    private void eventTeamControl(Table root, String labelKey, boolean boss) {
        int index = boss ? bossTeamIndex : hunterTeamIndex;
        Team team = eventTeam(index);
        root.table(row -> {
            row.add(text(labelKey) + ": " + (team == null ? "—" : team.localized())).wrap().width(UiSizing.width(300f)).left();
            row.button("‹", () -> { if (boss) bossTeamIndex--; else hunterTeamIndex--; rebuild(); }).size(44f);
            row.button("›", () -> { if (boss) bossTeamIndex++; else hunterTeamIndex++; rebuild(); }).size(44f);
        }).row();
    }

    private void eventNumber(Table root, String labelKey, String value, Consumer<String> setter) {
        root.table(row -> {
            row.add(text(labelKey)).wrap().width(UiSizing.width(300f)).left();
            row.field(value, setter::accept).width(UiSizing.width(160f)).maxTextLength(12);
        }).row();
    }

    private void presetPage(Table root) {
        root.add(text("st.presets-help")).wrap().width(UiSizing.width(650f)).left().row();
        if (bossRounds.isActive()) root.add(text("st.presets-active-block")).wrap().width(UiSizing.width(650f)).left().row();
        root.table(row -> {
            row.add(text("st.preset-name")).width(UiSizing.width(190f)).left();
            row.field(presetName, value -> presetName = limit(value, BossRoundPreset.MAX_NAME_CODE_POINTS))
                .width(UiSizing.width(390f)).maxTextLength(64);
        }).row();
        root.table(row -> {
            row.add(text("st.preset-description")).width(UiSizing.width(190f)).left();
            row.field(presetDescription, value -> presetDescription = limit(value, BossRoundPreset.MAX_DESCRIPTION_CODE_POINTS))
                .width(UiSizing.width(390f)).maxTextLength(300);
        }).row();
        root.table(row -> {
            row.defaults().width(UiSizing.width(210f)).height(44f).pad(3f);
            row.button(text("st.preset-save"), this::savePreset).disabled(!actions.isAvailable() || bossRounds.isActive());
            row.button(text("st.preset-refresh"), () -> { refreshPresets(); refreshPresetWidgets(); });
        }).row();
        root.button(text("st.preset-import-clipboard"), () -> {
            if (!actions.isAvailable() || bossRounds.isActive()) { showError("st.error.authority"); return; }
            try {
                BossRoundPreset imported = BossPresetStore.decode(Core.app.getClipboardText());
                confirm(Core.bundle.format("st.preset-confirm-import", imported.name()), () -> persistPreset(imported));
            } catch (IOException | IllegalArgumentException exception) { showError("st.preset.error.load"); }
        }).disabled(!actions.isAvailable() || bossRounds.isActive()).row();
        if (presetLoadError != null) root.add(text(presetLoadError)).wrap().width(UiSizing.width(650f)).left().row();
        presetListTable = new Table();
        root.pane(presetListTable).width(UiSizing.width(670f)).maxHeight(180f).row();
        presetDetailsTable = new Table();
        root.add(presetDetailsTable).left().row();
        refreshPresetWidgets();
    }

    private void refreshPresets() {
        try {
            presets = presetStore.list();
            presetLoadError = null;
            if (selectedPresetName != null && presets.stream().noneMatch(p -> p.name().equals(selectedPresetName)))
                selectedPresetName = null;
        } catch (IOException exception) {
            presetLoadError = "st.preset.error.read";
        }
    }

    private void refreshPresetWidgets() {
        if (presetListTable == null || presetDetailsTable == null) return;
        presetListTable.clearChildren();
        if (presets.isEmpty()) {
            presetListTable.add(text("st.preset.none")).left().pad(8f).row();
        } else {
            for (BossRoundPreset preset : presets) {
                String marker = Objects.equals(selectedPresetName, preset.name()) ? "▶ " : "  ";
                presetListTable.button(marker + preset.name(), () -> {
                    selectedPresetName = preset.name();
                    refreshPresetWidgets();
                }).left().growX().height(40f).row();
            }
        }
        presetListTable.invalidateHierarchy();
        presetDetailsTable.clearChildren();
        BossRoundPreset selected = selectedPreset();
        if (selected == null) return;
        Team bossTeam = accessibleTeam(selected.bossTeamId());
        Team hunterTeam = accessibleTeam(selected.hunterTeamId());
        presetDetailsTable.add(Core.bundle.format("st.preset-detail", selected.name(), selected.description(),
            bossTeam == null ? "?" : bossTeam.localized(), hunterTeam == null ? "?" : hunterTeam.localized())).wrap().width(UiSizing.width(650f)).row();
        presetDetailsTable.table(row -> {
            row.defaults().width(UiSizing.width(180f)).height(42f).pad(3f);
            row.button(text("st.preset-load"), () -> loadPreset(selected.name()))
                .disabled(!actions.isAvailable() || bossRounds.isActive() || bossTeam == null || hunterTeam == null);
            row.button(text("st.preset-delete"), () -> confirm(Core.bundle.format("st.preset-confirm-delete", selected.name()),
                () -> deletePreset(selected.name()))).disabled(!actions.isAvailable() || bossRounds.isActive());
            row.button(text("st.preset-export-clipboard"), () -> {
                Core.app.setClipboardText(BossPresetStore.encode(selected));
                Vars.ui.showInfoFade(text("st.preset-copied"));
            });
        }).row();
    }

    private BossRoundPreset selectedPreset() {
        for (BossRoundPreset preset : presets) if (Objects.equals(preset.name(), selectedPresetName)) return preset;
        return null;
    }

    private void savePreset() {
        if (!actions.isAvailable()) { showError("st.error.authority"); return; }
        if (bossRounds.isActive()) { showError("st.presets-active-block"); return; }
        try {
            BossRoundPreset value = currentPreset();
            var existing = presetStore.load(value.name());
            if (existing.isPresent()) {
                confirm(Core.bundle.format("st.preset-confirm-replace", value.name()), () -> persistPreset(value));
            } else {
                persistPreset(value);
            }
        } catch (IOException exception) {
            showError("st.preset.error.write");
        } catch (IllegalArgumentException exception) {
            showError("st.preset.error.fields");
        }
    }

    private BossRoundPreset currentPreset() {
        Team bossTeam = eventTeam(bossTeamIndex);
        Team hunterTeam = eventTeam(hunterTeamIndex);
        if (!HostActions.isAccessiblePresetTeam(bossTeam) || !HostActions.isAccessiblePresetTeam(hunterTeam)
                || bossTeam == hunterTeam) throw new IllegalArgumentException("invalid team");
        double preparation = parseDouble(preparationSeconds, 0, BossRoundPreset.MAX_PREPARATION_SECONDS, "st.preset.error.fields");
        double duration = parseDouble(durationSeconds, 1, BossRoundPreset.MAX_DURATION_SECONDS, "st.preset.error.fields");
        double penalty = parseDouble(deathPenaltySeconds, 0, duration, "st.preset.error.fields");
        return new BossRoundPreset(presetName, presetDescription, preparation, duration, deathPolicy,
            penalty, disconnectPolicy, bossTeam.id, hunterTeam.id, combatSettings);
    }

    private void persistPreset(BossRoundPreset preset) {
        try {
            presetStore.save(preset);
            presetName = preset.name();
            selectedPresetName = preset.name();
            refreshPresets();
            refreshPresetWidgets();
            Vars.ui.showInfoFade(Core.bundle.format("st.preset-saved", preset.name()));
        } catch (IOException exception) {
            showError("st.preset.error.write");
        }
    }

    private void loadPreset(String name) {
        if (bossRounds.isActive()) { showError("st.presets-active-block"); return; }
        try {
            BossRoundPreset preset = presetStore.load(name).orElseThrow(() -> new IOException("missing preset"));
            Team bossTeam = accessibleTeam(preset.bossTeamId());
            Team hunterTeam = accessibleTeam(preset.hunterTeamId());
            if (bossTeam == null || hunterTeam == null || bossTeam == hunterTeam)
                throw new IllegalArgumentException("preset teams are unavailable");
            int bossIndex = baseTeamIndex(bossTeam);
            int hunterIndex = baseTeamIndex(hunterTeam);
            if (bossIndex < 0 || hunterIndex < 0) throw new IllegalArgumentException("preset team is not selectable");

            // Validate everything before changing the visible form.
            bossTeamIndex = bossIndex;
            hunterTeamIndex = hunterIndex;
            preparationSeconds = numberText(preset.preparationSeconds());
            durationSeconds = numberText(preset.durationSeconds());
            deathPenaltySeconds = numberText(preset.deathPenaltySeconds());
            deathPolicy = preset.deathPolicy();
            disconnectPolicy = preset.disconnectPolicy();
            combatSettings = preset.combat();
            presetName = preset.name();
            presetDescription = preset.description();
            page = "event";
            rebuild();
            Vars.ui.showInfoFade(Core.bundle.format("st.preset-loaded", preset.name()));
        } catch (IOException | IllegalArgumentException exception) {
            showError("st.preset.error.load");
        }
    }

    private void deletePreset(String name) {
        try {
            if (presetStore.delete(name)) {
                if (Objects.equals(selectedPresetName, name)) selectedPresetName = null;
                refreshPresets();
                refreshPresetWidgets();
                Vars.ui.showInfoFade(Core.bundle.format("st.preset-deleted", name));
            } else showError("st.preset.error.missing");
        } catch (IOException exception) {
            showError("st.preset.error.delete");
        }
    }

    private static Team accessibleTeam(int id) {
        if (id < 0 || id >= Team.all.length) return null;
        Team team = Team.get(id);
        return HostActions.isAccessiblePresetTeam(team) ? team : null;
    }

    private static int baseTeamIndex(Team selected) {
        for (int i = 1; i < Team.baseTeams.length; i++) if (Team.baseTeams[i] == selected) return i - 1;
        return -1;
    }

    private static String numberText(double value) {
        if (value == Math.rint(value)) return Long.toString((long)value);
        return Double.toString(value);
    }

    private void quickConfirm(String prompt,Runnable operation){
        if (!actions.isAvailable()) { showError(actions.hasGame() ? "st.error.authority" : "st.error.no-game"); return; }
        Runnable guarded=WorldGuard.capture(operation);
        ToolUi.confirmAction(text("st.title"),prompt,()->attempt(guarded));
    }
    private void confirm(String prompt, Runnable operation) {
        if (!actions.isAvailable()) { showError(actions.hasGame() ? "st.error.authority" : "st.error.no-game"); return; }
        Runnable guarded=WorldGuard.capture(operation);
        Vars.ui.showConfirm(text("st.title"), prompt, () -> attempt(guarded));
    }

    private void attempt(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showError(exception.getMessage());
        }
    }

    private void perform(java.util.function.Supplier<String> operation) {
        try {
            String result = operation.get();
            Vars.ui.showInfoFade(result == null ? text("st.success") : result);
            if ("players".equals(page)) refreshPlayerWidgets();
            else if ("presets".equals(page)) refreshPresetWidgets();
        } catch (HostActions.PartialSpawnException exception) {
            Vars.ui.showInfo(Core.bundle.format("st.error.spawn-partial", exception.spawned(), exception.requested()));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showError(exception.getMessage());
        }
    }

    private void perform(Runnable operation) {
        perform(() -> { operation.run(); return text("st.success"); });
    }

    private void showError(String key) {
        String message = key == null ? text("st.error.unknown")
            : Core.bundle.has(key) ? text(key) : text("st.error.unknown");
        Vars.ui.showInfo(message);
    }

    private static String limit(String value, int maxCodePoints) {
        if (value == null || value.codePointCount(0, value.length()) <= maxCodePoints) return value == null ? "" : value;
        return value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
    }

    private record CoreBuildSelection(mindustry.world.blocks.storage.CoreBlock.CoreBuild core, int number, int total) { }
}
