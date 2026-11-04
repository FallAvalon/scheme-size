package org.schemetools.event;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Executable smoke test; run with the test classes and main classes on the classpath. */
public final class BossPresetStoreTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void mustFail(IoAction action) throws Exception {
        try {
            action.run();
            throw new AssertionError("expected IOException");
        } catch (IOException expected) {
            // Expected: malformed/unsupported preset data must not be treated as a valid preset.
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("boss-preset-test-");
        try {
            BossPresetStore store = new BossPresetStore(root.resolve("nested").resolve("presets"));
            BossRoundPreset russian = new BossRoundPreset("Тест Босс", "Пресет \"для босса\"\\особый", 7.5, 900,
                BossRound.BossDeath.RESPAWN, 15, BossRound.Disconnect.PAUSE, 2, 3);
            BossRoundPreset alpha = new BossRoundPreset("Alpha", "", 0, 60,
                BossRound.BossDeath.HUNTERS_WIN, 0, BossRound.Disconnect.DRAW, 4, 5);

            store.save(russian);
            store.save(alpha);
            BossCombatSettings combat = new BossCombatSettings("flare", "overclock", "", 4, 2, 0.5, 1,
                "copper", 50, 100, 1.5, true, false);
            BossRoundPreset battle = new BossRoundPreset("Battle", "", 5, 300, BossRound.BossDeath.RESPAWN,
                10, BossRound.Disconnect.PAUSE, 2, 1, combat);
            check(BossPresetStore.decode(BossPresetStore.encode(battle)).combat().equals(combat), "combat round trip");
            String legacy = "{\"formatVersion\":1,\"name\":\"Legacy\",\"description\":\"\",\"preparationSeconds\":0,"
                + "\"durationSeconds\":30,\"deathPolicy\":\"RESPAWN\",\"deathPenaltySeconds\":2,"
                + "\"disconnectPolicy\":\"PAUSE\",\"bossTeamId\":2,\"hunterTeamId\":1}";
            check(BossPresetStore.decode(legacy).combat().equals(BossCombatSettings.defaults()), "v1 migration defaults");
            mustFail(() -> BossPresetStore.decode(BossPresetStore.encode(battle).replace("\"bossHealth\":4.0", "\"bossHealth\":0")));
            mustFail(() -> BossPresetStore.decode(BossPresetStore.encode(battle).replace("\"restoreTeams\":false", "\"restoreTeams\":12")));
            mustFail(() -> BossPresetStore.decode(" ".repeat(BossPresetStore.MAX_JSON_BYTES + 1)));
            check(store.load("тест босс").orElseThrow().name().equals(russian.name()), "case-insensitive load");
            check(store.list().stream().map(BossRoundPreset::name).toList().equals(List.of("Alpha", russian.name())), "sorted list");
            check(store.load(russian.name()).orElseThrow().description().equals(russian.description()), "UTF-8 and escaped JSON round trip");

            BossRoundPreset changed = new BossRoundPreset(russian.name(), "обновлено", 1, 30,
                BossRound.BossDeath.HUNTERS_WIN, 0, BossRound.Disconnect.DRAW, 6, 7);
            store.save(changed);
            check(store.load(russian.name()).orElseThrow().description().equals("обновлено"), "valid overwrite");

            Path jsonFile;
            try (var files = Files.list(root.resolve("nested/presets"))) {
                jsonFile = files.filter(path -> {
                    try { return Files.readString(path, StandardCharsets.UTF_8).contains("обновлено"); }
                    catch (IOException exception) { return false; }
                }).findFirst().orElseThrow();
            }
            byte[] malformed = "{\"formatVersion\":1,\"name\":\"broken\"".getBytes(StandardCharsets.UTF_8);
            Files.write(jsonFile, malformed);
            mustFail(() -> store.load(changed.name()));
            mustFail(store::list);
            mustFail(() -> store.save(changed));
            mustFail(() -> store.delete(changed.name()));
            check(java.util.Arrays.equals(Files.readAllBytes(jsonFile), malformed), "corrupt file remains byte-for-byte unchanged");

            Files.writeString(jsonFile, "{\"formatVersion\":99}", StandardCharsets.UTF_8);
            byte[] unsupported = Files.readAllBytes(jsonFile);
            mustFail(store::list);
            check(java.util.Arrays.equals(Files.readAllBytes(jsonFile), unsupported), "unsupported version remains unchanged");

            check(store.delete("Alpha"), "delete valid preset");
            check(store.load("Alpha").isEmpty(), "deleted preset absent");
            check(!store.delete("Alpha"), "delete missing preset is idempotent");
            mustReject(() -> new BossRoundPreset("../unsafe", "", 0, 60,
                BossRound.BossDeath.HUNTERS_WIN, 0, BossRound.Disconnect.PAUSE, 1, 2));
            mustReject(() -> new BossRoundPreset("bad", "", Double.NaN, 60,
                BossRound.BossDeath.HUNTERS_WIN, 0, BossRound.Disconnect.PAUSE, 1, 2));
            mustReject(() -> new BossRoundPreset("bad", "", 0, 60,
                BossRound.BossDeath.HUNTERS_WIN, 0, BossRound.Disconnect.PAUSE, 1, 1));

            System.out.println("BossPresetStoreTest passed");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void mustReject(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected validation rejection.
        }
    }

    @FunctionalInterface
    private interface IoAction { void run() throws Exception; }
}
