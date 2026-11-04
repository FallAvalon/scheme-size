package org.schemetools.event;

import java.util.Objects;

/** Match-independent authoritative round state. Time is supplied by the game update loop. */
public final class BossRound {
    public enum Phase { IDLE, PREPARING, RUNNING, PAUSED, FINISHED }
    public enum Outcome { NONE, HUNTERS, BOSS, DRAW, ABORTED }
    public enum BossDeath { HUNTERS_WIN, RESPAWN }
    public enum Disconnect { PAUSE, HUNTERS_WIN, DRAW }

    private Phase phase = Phase.IDLE;
    private Phase pausedPhase = Phase.IDLE;
    private Outcome outcome = Outcome.NONE;
    private BossDeath deathPolicy;
    private Disconnect disconnectPolicy;
    private String bossId;
    private double remaining;
    private double duration;
    private double deathPenalty;
    private boolean bossDisconnected;
    private int bossScore;
    private int hunterScore;

    public void prepare(String bossId, double preparationSeconds, double durationSeconds,
                        BossDeath deathPolicy, double deathPenaltySeconds, Disconnect disconnectPolicy) {
        if (phase != Phase.IDLE && phase != Phase.FINISHED) throw new IllegalStateException("round active");
        if (bossId == null || bossId.isBlank() || !Double.isFinite(preparationSeconds)
                || !Double.isFinite(durationSeconds) || !Double.isFinite(deathPenaltySeconds)
                || preparationSeconds < 0 || preparationSeconds > 3600
                || durationSeconds <= 0 || durationSeconds > 86400
                || deathPenaltySeconds < 0 || deathPenaltySeconds > durationSeconds)
            throw new IllegalArgumentException("invalid round settings");
        this.bossId = bossId;
        this.deathPolicy = Objects.requireNonNull(deathPolicy);
        this.disconnectPolicy = Objects.requireNonNull(disconnectPolicy);
        this.deathPenalty = deathPenaltySeconds;
        duration = durationSeconds;
        remaining = preparationSeconds;
        outcome = Outcome.NONE;
        bossDisconnected = false;
        phase = preparationSeconds == 0 ? Phase.RUNNING : Phase.PREPARING;
        if (phase == Phase.RUNNING) remaining = duration;
    }

    public void advance(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0) throw new IllegalArgumentException("invalid time");
        if (phase != Phase.PREPARING && phase != Phase.RUNNING) return;
        while (seconds > 0 && (phase == Phase.PREPARING || phase == Phase.RUNNING)) {
            double elapsed = Math.min(seconds, remaining);
            remaining -= elapsed;
            seconds -= elapsed;
            if (remaining > 0) return;
            if (phase == Phase.PREPARING) { phase = Phase.RUNNING; remaining = duration; }
            else finish(Outcome.BOSS);
        }
    }

    public void bossDied() {
        requireRunning();
        if (deathPolicy == BossDeath.HUNTERS_WIN) finish(Outcome.HUNTERS);
        else {
            // The boss has to survive longer after a death; reducing the timer would reward it.
            remaining = Math.min(86400, remaining + deathPenalty);
        }
    }

    public void bossDisconnected(String id) {
        if (!Objects.equals(bossId, id) || (phase != Phase.PREPARING && phase != Phase.RUNNING
                && phase != Phase.PAUSED)) return;
        bossDisconnected = true;
        switch (disconnectPolicy) {
            case PAUSE -> { if (phase != Phase.PAUSED) pause(); }
            case HUNTERS_WIN -> finish(Outcome.HUNTERS);
            case DRAW -> finish(Outcome.DRAW);
        }
    }

    public void bossReconnected(String id) {
        // A late join must not undo a result produced by the disconnect policy.
        // Only the paused round that is waiting for this boss can be unblocked.
        if (disconnectPolicy == Disconnect.PAUSE && phase == Phase.PAUSED && bossDisconnected
                && Objects.equals(bossId, id)) bossDisconnected = false;
    }

    /** Changes the boss without restarting or otherwise changing the active round. */
    public void replaceBoss(String newBossId) {
        if (!isActive()) throw new IllegalStateException("round not active");
        if (newBossId == null || newBossId.isBlank() || Objects.equals(bossId, newBossId))
            throw new IllegalArgumentException("invalid boss");
        bossId = newBossId;
        bossDisconnected = false;
    }

    public void pause() {
        if (phase != Phase.PREPARING && phase != Phase.RUNNING) throw new IllegalStateException("round not active");
        pausedPhase = phase;
        phase = Phase.PAUSED;
    }

    public void resume() {
        if (phase != Phase.PAUSED || bossDisconnected) throw new IllegalStateException("round cannot resume");
        phase = pausedPhase;
    }

    public void finish(Outcome result) {
        if (phase != Phase.PREPARING && phase != Phase.RUNNING && phase != Phase.PAUSED)
            throw new IllegalStateException("round not active");
        if (result == null || result == Outcome.NONE) throw new IllegalArgumentException("invalid result");
        outcome = result;
        phase = Phase.FINISHED;
        if (result == Outcome.BOSS) bossScore++;
        if (result == Outcome.HUNTERS) hunterScore++;
    }

    private void requireRunning() { if (phase != Phase.RUNNING) throw new IllegalStateException("round not running"); }
    public void reset() {
        phase = Phase.IDLE;
        pausedPhase = Phase.IDLE;
        outcome = Outcome.NONE;
        deathPolicy = null;
        disconnectPolicy = null;
        bossId = null;
        remaining = 0;
        duration = 0;
        deathPenalty = 0;
        bossDisconnected = false;
        bossScore = 0;
        hunterScore = 0;
    }
    public Phase phase() { return phase; }
    public Outcome outcome() { return outcome; }
    public String bossId() { return bossId; }
    public boolean bossDisconnected() { return bossDisconnected; }
    public Disconnect disconnectPolicy() { return disconnectPolicy; }
    public boolean isAwaitingBossReconnect(String id) {
        return disconnectPolicy == Disconnect.PAUSE && phase == Phase.PAUSED && bossDisconnected
            && Objects.equals(bossId, id);
    }
    public boolean isActive() { return phase == Phase.PREPARING || phase == Phase.RUNNING || phase == Phase.PAUSED; }
    public double remaining() { return remaining; }
    public int bossScore() { return bossScore; }
    public int hunterScore() { return hunterScore; }
}
