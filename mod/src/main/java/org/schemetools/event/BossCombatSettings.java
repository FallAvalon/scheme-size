package org.schemetools.event;

/** Registry names are resolved and checked again against the running game before application. */
public record BossCombatSettings(String bossUnit, String bossEffect, String hunterEffect,
                                 double bossHealth, double bossDamage, double hunterHealth, double hunterDamage,
                                 String startingItem, int bossItems, int hunterItems,
                                 double respawnDelay, boolean huntersEliminatedWins, boolean restoreTeams) {
    public static BossCombatSettings defaults() {
        return new BossCombatSettings("", "", "", 1, 1, 1, 1, "", 0, 0, 3, false, true);
    }

    public BossCombatSettings {
        registryName(bossUnit); registryName(bossEffect); registryName(hunterEffect); registryName(startingItem);
        multiplier(bossHealth); multiplier(bossDamage); multiplier(hunterHealth); multiplier(hunterDamage);
        if (bossItems < 0 || bossItems > 10000 || hunterItems < 0 || hunterItems > 10000
                || ((bossItems > 0 || hunterItems > 0) && startingItem.isEmpty())
                || !Double.isFinite(respawnDelay) || respawnDelay < 0.5 || respawnDelay > 60)
            throw new IllegalArgumentException("st.boss.error.settings");
    }

    private static void registryName(String value) {
        if (value == null || value.length() > 128 || !value.matches("[a-zA-Z0-9_.-]*"))
            throw new IllegalArgumentException("st.boss.error.settings");
    }

    private static void multiplier(double value) {
        if (!Double.isFinite(value) || value < 0.1 || value > 100)
            throw new IllegalArgumentException("st.boss.error.settings");
    }
}
