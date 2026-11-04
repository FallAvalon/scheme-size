package org.schemetools.event;

import java.text.Normalizer;
import java.util.Objects;

/** Immutable, versioned local configuration for a Boss Player round. */
public final class BossRoundPreset {
    public static final int FORMAT_VERSION = 2;
    public static final int MAX_NAME_CODE_POINTS = 40;
    public static final int MAX_DESCRIPTION_CODE_POINTS = 240;
    public static final int MIN_TEAM_ID = 1;
    public static final int MAX_TEAM_ID = 255;
    public static final double MAX_PREPARATION_SECONDS = 3600d;
    public static final double MAX_DURATION_SECONDS = 86400d;

    private final String name;
    private final String description;
    private final double preparationSeconds;
    private final double durationSeconds;
    private final BossRound.BossDeath deathPolicy;
    private final double deathPenaltySeconds;
    private final BossRound.Disconnect disconnectPolicy;
    private final int bossTeamId;
    private final int hunterTeamId;
    private final BossCombatSettings combat;

    public BossRoundPreset(String name, String description, double preparationSeconds,
                           double durationSeconds, BossRound.BossDeath deathPolicy,
                           double deathPenaltySeconds, BossRound.Disconnect disconnectPolicy,
                           int bossTeamId, int hunterTeamId) {
        this(name, description, preparationSeconds, durationSeconds, deathPolicy, deathPenaltySeconds,
            disconnectPolicy, bossTeamId, hunterTeamId, BossCombatSettings.defaults());
    }

    public BossRoundPreset(String name, String description, double preparationSeconds,
                           double durationSeconds, BossRound.BossDeath deathPolicy,
                           double deathPenaltySeconds, BossRound.Disconnect disconnectPolicy,
                           int bossTeamId, int hunterTeamId, BossCombatSettings combat) {
        this.name = validateText(name, "name", MAX_NAME_CODE_POINTS, false);
        this.description = validateText(description, "description", MAX_DESCRIPTION_CODE_POINTS, true);
        if (!Double.isFinite(preparationSeconds) || preparationSeconds < 0
                || preparationSeconds > MAX_PREPARATION_SECONDS)
            throw new IllegalArgumentException("preparationSeconds out of range");
        if (!Double.isFinite(durationSeconds) || durationSeconds <= 0
                || durationSeconds > MAX_DURATION_SECONDS)
            throw new IllegalArgumentException("durationSeconds out of range");
        if (!Double.isFinite(deathPenaltySeconds) || deathPenaltySeconds < 0
                || deathPenaltySeconds > durationSeconds)
            throw new IllegalArgumentException("deathPenaltySeconds out of range");
        this.deathPolicy = Objects.requireNonNull(deathPolicy, "deathPolicy");
        this.disconnectPolicy = Objects.requireNonNull(disconnectPolicy, "disconnectPolicy");
        if (bossTeamId < MIN_TEAM_ID || bossTeamId > MAX_TEAM_ID
                || hunterTeamId < MIN_TEAM_ID || hunterTeamId > MAX_TEAM_ID
                || bossTeamId == hunterTeamId)
            throw new IllegalArgumentException("invalid team ids");

        this.preparationSeconds = preparationSeconds;
        this.durationSeconds = durationSeconds;
        this.deathPenaltySeconds = deathPenaltySeconds;
        this.bossTeamId = bossTeamId;
        this.hunterTeamId = hunterTeamId;
        this.combat = Objects.requireNonNull(combat);
    }

    private static String validateText(String value, String field, int maxCodePoints, boolean allowEmpty) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        if (!normalized.equals(normalized.strip()) || (!allowEmpty && normalized.isEmpty()))
            throw new IllegalArgumentException("invalid " + field);
        if (normalized.codePointCount(0, normalized.length()) > maxCodePoints)
            throw new IllegalArgumentException(field + " is too long");
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            if (Character.isISOControl(codePoint) || Character.getType(codePoint) == Character.FORMAT
                    || (!allowEmpty && (codePoint == '/' || codePoint == '\\' || codePoint == ':'
                    || codePoint == '*' || codePoint == '?' || codePoint == '"'
                    || codePoint == '<' || codePoint == '>' || codePoint == '|')))
                throw new IllegalArgumentException("invalid character in " + field);
            offset += Character.charCount(codePoint);
        }
        // Reject unpaired UTF-16 surrogates so persisted UTF-8 stays round-trippable.
        for (int i = 0; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            if (Character.isHighSurrogate(current)) {
                if (i + 1 >= normalized.length() || !Character.isLowSurrogate(normalized.charAt(i + 1)))
                    throw new IllegalArgumentException("invalid Unicode in " + field);
                i++;
            } else if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException("invalid Unicode in " + field);
            }
        }
        return normalized;
    }

    public int formatVersion() { return FORMAT_VERSION; }
    public String name() { return name; }
    public String description() { return description; }
    public double preparationSeconds() { return preparationSeconds; }
    public double durationSeconds() { return durationSeconds; }
    public BossRound.BossDeath deathPolicy() { return deathPolicy; }
    public double deathPenaltySeconds() { return deathPenaltySeconds; }
    public BossRound.Disconnect disconnectPolicy() { return disconnectPolicy; }
    public int bossTeamId() { return bossTeamId; }
    public int hunterTeamId() { return hunterTeamId; }
    public BossCombatSettings combat() { return combat; }

    /** Case-insensitive, Unicode-normalized identity used only for local store lookup. */
    String key() { return name.toLowerCase(java.util.Locale.ROOT); }
}
