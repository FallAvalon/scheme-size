package org.schemetools.event;

public final class BossRoundTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    private static void mustFail(Runnable r) {
        try { r.run(); throw new AssertionError("expected exception"); }
        catch (IllegalArgumentException | IllegalStateException expected) { }
    }
    public static void main(String[] args) throws Exception {
        BossRound round = new BossRound();
        mustFail(() -> round.prepare("", 1, 10, BossRound.BossDeath.HUNTERS_WIN, 0, BossRound.Disconnect.PAUSE));
        round.prepare("boss", 5, 10, BossRound.BossDeath.HUNTERS_WIN, 0, BossRound.Disconnect.PAUSE);
        round.advance(7);
        check(round.phase() == BossRound.Phase.RUNNING && round.remaining() == 8);
        round.bossDisconnected("other");
        check(round.phase() == BossRound.Phase.RUNNING);
        round.bossDisconnected("boss");
        mustFail(round::resume);
        round.advance(100);
        check(round.remaining() == 8);
        round.bossReconnected("boss");
        round.resume();
        round.bossDied();
        check(round.outcome() == BossRound.Outcome.HUNTERS && round.hunterScore() == 1);
        mustFail(() -> round.finish(BossRound.Outcome.BOSS));
        round.prepare("boss", 0, 10, BossRound.BossDeath.RESPAWN, 3, BossRound.Disconnect.DRAW);
        round.bossDied();
        check(round.remaining() == 13);
        round.advance(13);
        check(round.outcome() == BossRound.Outcome.BOSS && round.bossScore() == 1);
        round.prepare("boss", 0, 20, BossRound.BossDeath.RESPAWN, 0, BossRound.Disconnect.PAUSE);
        round.pause();
        round.bossDisconnected("boss");
        check(round.phase() == BossRound.Phase.PAUSED && round.bossDisconnected());
        mustFail(round::resume);
        round.bossReconnected("boss");
        round.resume();
        check(round.phase() == BossRound.Phase.RUNNING && !round.bossDisconnected());

        // Replacement preserves the active round state and score while changing identity.
        round.advance(4);
        double beforeReplacement = round.remaining();
        int bossScoreBeforeReplacement = round.bossScore();
        int hunterScoreBeforeReplacement = round.hunterScore();
        round.pause();
        round.replaceBoss("replacement");
        check(round.bossId().equals("replacement"));
        check(round.phase() == BossRound.Phase.PAUSED && round.remaining() == beforeReplacement);
        check(!round.bossDisconnected() && round.outcome() == BossRound.Outcome.NONE);
        check(round.bossScore() == bossScoreBeforeReplacement && round.hunterScore() == hunterScoreBeforeReplacement);
        mustFail(() -> round.replaceBoss("replacement"));
        mustFail(() -> round.replaceBoss(" "));
        mustFail(() -> round.replaceBoss(null));
        round.resume();
        round.finish(BossRound.Outcome.HUNTERS);
        check(round.hunterScore() == hunterScoreBeforeReplacement + 1);

        // The completed round's score carries into the next round on the same map.
        round.prepare("next", 0, 10, BossRound.BossDeath.RESPAWN, 0, BossRound.Disconnect.PAUSE);
        check(round.bossScore() == bossScoreBeforeReplacement);
        check(round.hunterScore() == hunterScoreBeforeReplacement + 1);
        round.finish(BossRound.Outcome.BOSS);
        mustFail(() -> round.finish(BossRound.Outcome.BOSS));
        check(round.bossScore() == bossScoreBeforeReplacement + 1);

        // Only PAUSE disconnects can be unblocked by the original boss reconnecting.
        BossRound reconnect = new BossRound();
        reconnect.prepare("original", 0, 10, BossRound.BossDeath.RESPAWN, 0, BossRound.Disconnect.PAUSE);
        reconnect.bossDisconnected("original");
        check(reconnect.phase() == BossRound.Phase.PAUSED && reconnect.isAwaitingBossReconnect("original"));
        reconnect.bossReconnected("original");
        check(!reconnect.bossDisconnected() && !reconnect.isAwaitingBossReconnect("original"));
        check(reconnect.phase() == BossRound.Phase.PAUSED); // reconnect does not auto-resume
        reconnect.resume();
        check(reconnect.phase() == BossRound.Phase.RUNNING);

        for (BossRound.Disconnect policy : new BossRound.Disconnect[]{
                BossRound.Disconnect.HUNTERS_WIN, BossRound.Disconnect.DRAW}) {
            BossRound disconnected = new BossRound();
            disconnected.prepare("original", 0, 10, BossRound.BossDeath.RESPAWN, 0, policy);
            disconnected.bossDisconnected("original");
            BossRound.Outcome expected = policy == BossRound.Disconnect.HUNTERS_WIN
                ? BossRound.Outcome.HUNTERS : BossRound.Outcome.DRAW;
            check(disconnected.phase() == BossRound.Phase.FINISHED && disconnected.outcome() == expected);
            int scoreAfterDisconnect = disconnected.hunterScore();
            disconnected.bossReconnected("original");
            check(disconnected.phase() == BossRound.Phase.FINISHED && disconnected.outcome() == expected);
            check(disconnected.bossDisconnected() && disconnected.hunterScore() == scoreAfterDisconnect);
        }

        round.prepare("cleanup", 0, 1, BossRound.BossDeath.RESPAWN, 0, BossRound.Disconnect.PAUSE);
        round.finish(BossRound.Outcome.ABORTED);
        round.reset();
        check(round.phase() == BossRound.Phase.IDLE && round.bossScore() == 0 && round.hunterScore() == 0);
        check(round.bossId() == null && !round.bossDisconnected() && round.outcome() == BossRound.Outcome.NONE);
        BossPresetStoreTest.main(args);
        LocalizationTest.main(args);
        GridRoutesTest.main(args);
        System.out.println("BossRoundTest passed");
    }
}
