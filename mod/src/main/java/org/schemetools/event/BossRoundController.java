package org.schemetools.event;

import arc.Core;
import arc.Events;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType.PlayerJoin;
import mindustry.game.EventType.PlayerLeave;
import mindustry.game.EventType.ResetEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.UnitDestroyEvent;
import mindustry.game.EventType.UnitChangeEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.game.Rules;
import mindustry.ctype.ContentType;
import mindustry.type.UnitType;
import mindustry.type.StatusEffect;
import mindustry.type.Item;
import org.schemetools.host.HostActions;

import java.util.Objects;

/** Connects the round state machine to the authoritative built-in host game. */
public final class BossRoundController {
    private final HostActions actions;
    private final BossRound round = new BossRound();
    private long sessionId;
    private String bossName = "";
    private Team bossTeam;
    private Team hunterTeam;
    private int lastCountdown = Integer.MIN_VALUE;
    private boolean resultAnnounced;
    private BossCombatSettings combat = BossCombatSettings.defaults();
    private final java.util.Map<String, Team> originalTeams = new java.util.LinkedHashMap<>();
    private final java.util.Map<Unit, java.util.Set<StatusEffect>> appliedEffects = new java.util.IdentityHashMap<>();
    private RuleSnapshot bossRules, hunterRules;
    private boolean originalCanGameOver;
    private boolean combatApplied;
    private Rules appliedRules;
    private UnitType respawnType;
    private float spawnX, spawnY;
    private double respawnIn = -1, effectIn, huntersAbsent;
    private int lastDeathUnit = -1;
    private Unit trackedBossUnit;

    private record RuleSnapshot(float health, float damage) {
        RuleSnapshot(Rules.TeamRule rule) { this(rule.unitHealthMultiplier, rule.unitDamageMultiplier); }
        void restore(Rules.TeamRule rule) { rule.unitHealthMultiplier = health; rule.unitDamageMultiplier = damage; }
    }

    public BossRoundController(HostActions actions) {
        this.actions = Objects.requireNonNull(actions);
        actions.allowTeamCyclesWhen(() -> !round.isActive());
    }

    public void install() {
        Events.on(PlayerLeave.class, event -> playerLeft(event.player));
        Events.on(PlayerJoin.class, event -> playerJoined(event.player));
        Events.on(UnitDestroyEvent.class, event -> unitDestroyed(event.unit));
        Events.on(UnitChangeEvent.class, event -> {
            if (round.isActive() && Objects.equals(round.bossId(), event.player.uuid())) {
                // PlayerComp may clear a falling aircraft before UnitDestroyEvent is emitted.
                // Keep the former body until its death has been processed independently of its controller.
                if (trackedBossUnit != null && (trackedBossUnit.dead || trackedBossUnit.health <= 0))
                    unitDestroyed(trackedBossUnit);
                if (event.unit != null) trackedBossUnit = event.unit;
            }
        });
        Events.on(WorldLoadEvent.class, event -> reset());
        Events.on(ResetEvent.class, event -> reset());
        Events.run(Trigger.update, this::update);
    }

    public void start(Player boss, Team selectedBossTeam, Team selectedHunterTeam,
                      double preparationSeconds, double durationSeconds,
                      BossRound.BossDeath deathPolicy, double deathPenaltySeconds,
                      BossRound.Disconnect disconnectPolicy) {
        start(boss, selectedBossTeam, selectedHunterTeam, preparationSeconds, durationSeconds,
            deathPolicy, deathPenaltySeconds, disconnectPolicy, BossCombatSettings.defaults());
    }

    public void start(Player boss, Team selectedBossTeam, Team selectedHunterTeam,
                      double preparationSeconds, double durationSeconds,
                      BossRound.BossDeath deathPolicy, double deathPenaltySeconds,
                      BossRound.Disconnect disconnectPolicy, BossCombatSettings settings) {
        requireHostGame();
        if (round.isActive()) throw new IllegalStateException("st.boss.error.round-active");
        if (boss == null || !Groups.player.contains(player -> player == boss)
                || selectedBossTeam == null || selectedHunterTeam == null
                || !HostActions.isValidTeam(selectedBossTeam) || !HostActions.isValidTeam(selectedHunterTeam)
                || selectedBossTeam == selectedHunterTeam)
            throw new IllegalArgumentException("st.boss.error.selection");

        var players = Groups.player.copy();
        if (players.size < 2) throw new IllegalStateException("st.boss.error.need-hunters");
        if (boss.uuid() == null || boss.uuid().isBlank())
            throw new IllegalStateException("st.boss.error.player-id");

        // Validate the entire configuration before changing any player's team.
        BossRound validation = new BossRound();
        try {
            validation.prepare(boss.uuid(), preparationSeconds, durationSeconds, deathPolicy,
                deathPenaltySeconds, disconnectPolicy);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException("st.boss.error.settings");
        }

        boolean hasHunter = false;
        for (Player player : players) {
            if (player != boss) hasHunter = true;
        }
        if (!hasHunter) throw new IllegalStateException("st.boss.error.need-hunters");
        validateCombat(settings, boss, selectedBossTeam, selectedHunterTeam);
        for (Player player : players) if (!HostActions.isValidTeam(player.team())) throw new IllegalArgumentException("st.boss.error.selection");
        actions.stopTeamCycles();

        // Validate first and restore prior teams if a later assignment fails.
        java.util.Map<Player, Team> previousTeams = new java.util.LinkedHashMap<>();
        for (Player player : players) {
            if (player.team() == null || !HostActions.isValidTeam(player.team()))
                throw new IllegalArgumentException("st.boss.error.selection");
            previousTeams.put(player, player.team());
        }
        try {
            for (Player player : players) actions.team(player, player == boss ? selectedBossTeam : selectedHunterTeam);
            combat = settings;
            bossTeam = selectedBossTeam;
            hunterTeam = selectedHunterTeam;
            originalTeams.clear();
            previousTeams.forEach((player, team) -> originalTeams.put(player.uuid(), team));
            applyCombatRules();
            spawnX = boss.x;
            spawnY = boss.y;
            respawnType = settings.bossUnit().isEmpty() ? boss.unit() == null ? null : boss.unit().type
                : Vars.content.unit(settings.bossUnit());
            if (!settings.bossUnit().isEmpty()) actions.controlledUnit(boss, respawnType, spawnX, spawnY);
            grantItems(settings.startingItem(), selectedBossTeam, settings.bossItems());
            grantItems(settings.startingItem(), selectedHunterTeam, settings.hunterItems());
            round.prepare(boss.uuid(), preparationSeconds, durationSeconds, deathPolicy,
                deathPenaltySeconds, disconnectPolicy);
        } catch (RuntimeException failure) {
            cleanupCombat();
            previousTeams.forEach(this::restoreTeam);
            throw failure;
        }
        sessionId++;
        bossName = boss.name;
        bossTeam = selectedBossTeam;
        hunterTeam = selectedHunterTeam;
        lastCountdown = Integer.MIN_VALUE;
        resultAnnounced = false;
        respawnIn = -1;
        effectIn = 0;
        huntersAbsent = 0;
        lastDeathUnit = -1;
        trackedBossUnit = boss.unit();
        if (round.phase() == BossRound.Phase.RUNNING) announce("st.boss.started", bossName);
        else announce("st.boss.preparing", bossName, (int)Math.ceil(preparationSeconds));
    }

    /** Reassigns the active boss while preserving the round clock, phase, outcome, and score. */
    public void replaceBoss(Player newBoss) {
        requireHostGame();
        if (!round.isActive()) throw new IllegalStateException("st.boss.error.round-active");

        String oldBossId = round.bossId();
        if (newBoss == null || !Groups.player.contains(player -> player == newBoss)
                || newBoss.uuid() == null || newBoss.uuid().isBlank()
                || Objects.equals(oldBossId, newBoss.uuid())
                || newBoss.team() == null || newBoss.team() == Team.derelict
                || bossTeam == null || hunterTeam == null
                || bossTeam == Team.derelict || hunterTeam == Team.derelict || bossTeam == hunterTeam)
            throw new IllegalArgumentException("st.boss.error.selection");

        Player oldBoss = connectedPlayer(oldBossId);
        if (oldBoss == newBoss)
            throw new IllegalArgumentException("st.boss.error.selection");

        // Capture the affected teams and validate identity/state before mutating anything.
        Team newBossOriginalTeam = newBoss.team();
        Team oldBossOriginalTeam = oldBoss == null ? null : oldBoss.team();
        String newBossId = newBoss.uuid();
        String newBossName = newBoss.name;

        try {
            actions.team(newBoss, bossTeam);
            if (oldBoss != null) actions.team(oldBoss, hunterTeam);
            round.replaceBoss(newBossId);
            bossName = newBossName;
            respawnIn = -1;
            lastDeathUnit = -1;
            trackedBossUnit = newBoss.unit();
            spawnX = newBoss.x;
            spawnY = newBoss.y;
            if (!combat.bossUnit().isEmpty()) {
                respawnType = Vars.content.unit(combat.bossUnit());
                respawnIn = 0;
            } else if (newBoss.unit() != null) respawnType = newBoss.unit().type;
        } catch (RuntimeException failure) {
            // Best-effort rollback keeps the prior boss and candidate in their original teams.
            if (oldBoss != null) restoreTeam(oldBoss, oldBossOriginalTeam);
            restoreTeam(newBoss, newBossOriginalTeam);
            throw failure;
        }
    }

    private static Player connectedPlayer(String uuid) {
        if (uuid == null) return null;
        for (Player player : Groups.player.copy()) {
            if (Objects.equals(uuid, player.uuid())) return player;
        }
        return null;
    }

    private void restoreTeam(Player player, Team team) {
        if (team == null || !Groups.player.contains(candidate -> candidate == player)) return;
        try {
            actions.team(player, team);
        } catch (RuntimeException ignored) {
            // The original operation's error remains the actionable failure.
        }
    }

    public void pause() {
        requireHostGame();
        round.pause();
        announce("st.boss.paused");
    }

    public void resume() {
        requireHostGame();
        round.resume();
        announce(round.phase() == BossRound.Phase.PREPARING ? "st.boss.resumed-preparing" : "st.boss.started", bossName);
    }

    public void finish(BossRound.Outcome outcome) {
        requireHostGame();
        round.finish(outcome);
        announceResultOnce();
    }

    public void reset() {
        cleanupCombat();
        round.reset();
        bossName = "";
        bossTeam = null;
        hunterTeam = null;
        lastCountdown = Integer.MIN_VALUE;
        resultAnnounced = false;
        originalTeams.clear();
        respawnIn = -1;
        trackedBossUnit = null;
    }

    private void update() {
        if (!actions.isAvailable() || !Vars.state.isGame() || Vars.state.isPaused()) return;
        if (trackedBossUnit != null && (trackedBossUnit.dead || trackedBossUnit.health <= 0 || !trackedBossUnit.isAdded()))
            unitDestroyed(trackedBossUnit);
        BossRound.Phase before = round.phase();
        round.advance(Time.delta / 60d);
        BossRound.Phase after = round.phase();
        if (round.isActive()) updateCombat(Time.delta / 60d);
        after = round.phase();
        if (before == BossRound.Phase.PREPARING && after == BossRound.Phase.RUNNING) {
            announce("st.boss.started", bossName);
            lastCountdown = Integer.MIN_VALUE;
        } else if (after == BossRound.Phase.FINISHED && before != BossRound.Phase.FINISHED) {
            announceResultOnce();
        }

        if (after == BossRound.Phase.PREPARING) {
            int seconds = (int)Math.ceil(round.remaining());
            if (seconds > 0 && seconds <= 10 && seconds != lastCountdown) {
                announce("st.boss.countdown", seconds);
                lastCountdown = seconds;
            }
        }
    }

    private void playerLeft(Player player) {
        if (!actions.isAvailable() || player == null || !round.isActive()) return;
        if (!Objects.equals(round.bossId(), player.uuid())) return;
        round.bossDisconnected(player.uuid());
        if (round.phase() == BossRound.Phase.PAUSED) announce("st.boss.disconnect-paused", bossName);
        else if (round.phase() == BossRound.Phase.FINISHED) announceResultOnce();
    }

    private void playerJoined(Player player) {
        if (!actions.isAvailable() || player == null || !round.isActive()) return;
        boolean returningBoss = round.isAwaitingBossReconnect(player.uuid());
        try {
            originalTeams.putIfAbsent(player.uuid(), player.team());
            // Every late joiner receives the active event's role. A reconnecting boss
            // must be restored before the paused round can resume.
            actions.team(player, Objects.equals(round.bossId(), player.uuid()) ? bossTeam : hunterTeam);
        } catch (RuntimeException ignored) {
            return;
        }
        if (returningBoss) {
            round.bossReconnected(player.uuid());
            announce("st.boss.reconnected", bossName);
            if (player.dead() && respawnType != null) respawnIn = combat.respawnDelay();
        }
    }

    private void unitDestroyed(Unit unit) {
        if (!actions.isAvailable() || unit == null || !round.isActive()) return;
        Player player = unit.getPlayer();
        if (unit != trackedBossUnit && (player == null || !Objects.equals(round.bossId(), player.uuid()))) return;
        if (round.phase() != BossRound.Phase.RUNNING && round.phase() != BossRound.Phase.PREPARING) return;
        if (lastDeathUnit == unit.id) return;
        lastDeathUnit = unit.id;
        respawnType = combat.bossUnit().isEmpty() ? unit.type : Vars.content.unit(combat.bossUnit());
        spawnX = unit.x;
        spawnY = unit.y;
        try {
            if (round.phase() == BossRound.Phase.RUNNING) round.bossDied();
        } catch (IllegalStateException ignored) {
            return;
        }
        if (round.phase() == BossRound.Phase.FINISHED) announceResultOnce();
        else {
            respawnIn = combat.respawnDelay();
            announce("st.boss.death-penalty", bossName, (int)Math.ceil(round.remaining()));
        }
    }

    private void validateCombat(BossCombatSettings settings, Player boss, Team bt, Team ht) {
        if (settings == null) throw new IllegalArgumentException("st.boss.error.settings");
        if (!settings.bossUnit().isEmpty()) {
            UnitType type = Vars.content.unit(settings.bossUnit());
            if (type == null || type.isHidden() || type.isBanned() || !type.playerControllable
                    || type.segmentUnits > 1 || !type.supportsEnv(Vars.state.rules.env)
                    || !Vars.state.rules.possessionAllowed)
                throw new IllegalArgumentException("st.boss.error.unit");
        } else if (boss.dead() || !boss.unit().type.playerControllable) {
            throw new IllegalArgumentException("st.boss.error.unit");
        }
        effect(settings.bossEffect()); effect(settings.hunterEffect());
        checkItems(settings.startingItem(), bt, settings.bossItems());
        checkItems(settings.startingItem(), ht, settings.hunterItems());
    }

    private static StatusEffect effect(String name) {
        if (name.isEmpty()) return null;
        StatusEffect effect = Vars.content.getByName(ContentType.status, name);
        if (effect == null || effect.dynamic || effect.reactive || effect.permanent)
            throw new IllegalArgumentException("st.boss.error.effect");
        return effect;
    }

    private static void checkItems(String name, Team team, int amount) {
        if (amount == 0) return;
        Item item = Vars.content.item(name);
        var core = team.core();
        if (item == null || core == null || core.items.get(item) + (long)amount > core.getMaximumAccepted(item))
            throw new IllegalArgumentException("st.error.capacity");
    }

    private void grantItems(String name, Team team, int amount) {
        if (amount != 0) actions.adjustItem(team, team.core(), Vars.content.item(name), amount);
    }

    private void applyCombatRules() {
        appliedRules = Vars.state.rules;
        bossRules = new RuleSnapshot(bossTeam.rules());
        hunterRules = new RuleSnapshot(hunterTeam.rules());
        originalCanGameOver = Vars.state.rules.canGameOver;
        combatApplied = true;
        bossTeam.rules().unitHealthMultiplier = (float)combat.bossHealth();
        bossTeam.rules().unitDamageMultiplier = (float)combat.bossDamage();
        hunterTeam.rules().unitHealthMultiplier = (float)combat.hunterHealth();
        hunterTeam.rules().unitDamageMultiplier = (float)combat.hunterDamage();
        Vars.state.rules.canGameOver = false;
        Call.setRules(Vars.state.rules);
    }

    private void updateCombat(double seconds) {
        if (round.phase() == BossRound.Phase.PAUSED) return;
        if (respawnIn >= 0) {
            respawnIn -= seconds;
            Player boss = connectedPlayer(round.bossId());
            if (respawnIn <= 0 && boss != null) {
                respawnIn = -1;
                try {
                    actions.controlledUnit(boss, respawnType, spawnX, spawnY);
                    announce("st.boss.respawned", bossName);
                } catch (RuntimeException failure) {
                    round.pause();
                    announce("st.boss.respawn-failed", bossName);
                    arc.util.Log.err("Scheme-Tools boss respawn failed", failure);
                }
            }
        }
        effectIn -= seconds;
        if (effectIn <= 0) {
            effectIn = 1;
            for (Player player : Groups.player.copy()) if (!player.dead()) {
                StatusEffect effect = effect(Objects.equals(round.bossId(), player.uuid()) ? combat.bossEffect() : combat.hunterEffect());
                if (effect != null) {
                    if (!player.unit().hasEffect(effect))
                        appliedEffects.computeIfAbsent(player.unit(), ignored -> new java.util.HashSet<>()).add(effect);
                    player.unit().apply(effect, 120f);
                }
            }
        }
        if (combat.huntersEliminatedWins() && round.phase() == BossRound.Phase.RUNNING) {
            boolean alive = Groups.player.contains(p -> !Objects.equals(round.bossId(), p.uuid()) && !p.dead());
            huntersAbsent = alive ? 0 : huntersAbsent + seconds;
            if (huntersAbsent >= 2) round.finish(BossRound.Outcome.BOSS);
        }
    }

    private void cleanupCombat() {
        if (!combatApplied) return;
        combatApplied = false;
        if (Vars.state != null && Vars.state.rules == appliedRules && bossTeam != null && hunterTeam != null && !Vars.net.client()) {
            bossRules.restore(bossTeam.rules());
            hunterRules.restore(hunterTeam.rules());
            Vars.state.rules.canGameOver = originalCanGameOver;
            Call.setRules(Vars.state.rules);
            if (combat.restoreTeams()) for (Player player : Groups.player.copy())
                restoreTeam(player, originalTeams.get(player.uuid()));
        }
        appliedEffects.forEach((unit, effects) -> effects.forEach(unit::unapply));
        appliedEffects.clear();
        respawnIn = -1;
    }

    private void requireHostGame() {
        if (!actions.isAvailable()) throw new IllegalStateException("st.error.authority");
        if (!Vars.state.isGame()) throw new IllegalStateException("st.boss.error.no-game");
    }

    private void announceResultOnce() {
        if (resultAnnounced || round.outcome() == BossRound.Outcome.NONE) return;
        resultAnnounced = true;
        cleanupCombat();
        switch (round.outcome()) {
            case HUNTERS -> announce("st.boss.result.hunters", bossName, round.hunterScore());
            case BOSS -> announce("st.boss.result.boss", bossName, round.bossScore());
            case DRAW -> announce("st.boss.result.draw", bossName);
            case ABORTED -> announce("st.boss.result.aborted", bossName);
            default -> { }
        }
    }

    private static void announce(String key, Object... values) {
        if (Vars.net.server() || !Vars.net.active()) Call.sendMessage(Core.bundle.format(key, values));
    }

    public BossRound.Phase phase() { return round.phase(); }
    public long sessionId(){return sessionId;}
    public BossRound.Outcome outcome() { return round.outcome(); }
    public String bossName() { return bossName; }
    public double remaining() { return round.remaining(); }
    public int bossScore() { return round.bossScore(); }
    public int hunterScore() { return round.hunterScore(); }
    public boolean bossDisconnected() { return round.bossDisconnected(); }
    public Team bossTeam() { return bossTeam; }
    public Team hunterTeam() { return hunterTeam; }
    public boolean isActive() { return round.isActive(); }
}
