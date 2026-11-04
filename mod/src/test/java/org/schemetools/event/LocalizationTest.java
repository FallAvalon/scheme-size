package org.schemetools.event;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks that strings referenced by the mod exist in both bundled languages. */
public final class LocalizationTest {
    public static void main(String[] args) throws Exception {
        Properties english = read("bundle.properties");
        Properties russian = read("bundle_ru.properties");
        Pattern reference = Pattern.compile("\"(st\\.[a-z0-9_.-]+)\"");
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher match = reference.matcher(Files.readString(path));
                while (match.find()) {
                    String key = match.group(1);
                    if (key.endsWith(".")) continue; // Enum suffixes are checked below.
                    require(english, key);
                    require(russian, key);
                }
            }
        }
        for (BossRound.Phase value : BossRound.Phase.values()) checkBoth(english, russian, "st.boss.phase." + value.name().toLowerCase(java.util.Locale.ROOT));
        for (BossRound.Outcome value : BossRound.Outcome.values()) checkBoth(english, russian, "st.boss.result." + value.name().toLowerCase(java.util.Locale.ROOT));
        for (BossRound.BossDeath value : BossRound.BossDeath.values()) checkBoth(english, russian, "st.boss.death." + value.name().toLowerCase(java.util.Locale.ROOT));
        for (BossRound.Disconnect value : BossRound.Disconnect.values()) checkBoth(english, russian, "st.boss.disconnect." + value.name().toLowerCase(java.util.Locale.ROOT));
        checkBoth(english, russian, "keybind.schemetools-panel.name");
        Matcher keyNames = Pattern.compile("\\(\"(schemesize-[a-z-]+)\", ").matcher(Files.readString(Path.of("src/main/java/org/schemetools/client/ModKeys.java")));
        while (keyNames.find()) checkBoth(english, russian, "keybind." + keyNames.group(1) + ".name");
        checkBoth(english, russian, "category.scheme-tools.name");
        checkBoth(english, russian, "setting.schemetools-hud.name");
        for (org.schemetools.Edition edition : org.schemetools.Edition.values()) checkBoth(english, russian, edition.titleKey);
        for (String name : new String[]{"grid", "ruler", "unit-bars", "unit-range", "turret-range", "reactor-range", "boost-range", "power-bars", "floor-xray", "hide-units"})
            checkBoth(english, russian, "st.visual." + name);
        for (String name : new String[]{"off", "mine", "follow", "cursor", "circle", "assist", "repair", "demolish"})
            checkBoth(english, russian, "st.ai.mode." + name);
        for (String key : english.stringPropertyNames()) {
            if (!key.startsWith("st.")) continue;
            require(russian, key);
            if (!arguments(english.getProperty(key)).equals(arguments(russian.getProperty(key))))
                throw new AssertionError("format arguments differ: " + key);
            if (english.getProperty(key).contains("@") || russian.getProperty(key).contains("@"))
                throw new AssertionError("use indexed bundle arguments: " + key);
        }
        System.out.println("LocalizationTest passed");
    }

    private static Properties read(String name) throws Exception {
        Properties result = new Properties() {
            @Override public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) throw new AssertionError("duplicate translation in " + name + ": " + key);
                return super.put(key, value);
            }
        };
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources/bundles", name), StandardCharsets.UTF_8)) {
            result.load(reader);
        }
        return result;
    }

    private static void require(Properties values, String key) {
        if (!values.containsKey(key) || values.getProperty(key).isBlank()) throw new AssertionError("missing translation: " + key);
    }

    private static void checkBoth(Properties a, Properties b, String key) { require(a, key); require(b, key); }

    private static Set<String> arguments(String value) {
        Set<String> result = new HashSet<>();
        Matcher matcher = Pattern.compile("\\{\\d+}").matcher(value);
        while (matcher.find()) result.add(matcher.group());
        return result;
    }
}
