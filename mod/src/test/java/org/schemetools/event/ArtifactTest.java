package org.schemetools.event;

import org.schemetools.Edition;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipFile;

/** Packaging checks; runtime coexistence is tested separately in isolated game profiles. */
public final class ArtifactTest {
    public static void main(String[] args) throws Exception {
        for (Edition edition : Edition.values()) {
            if (!edition.modId.matches("[a-z][a-z0-9-]{2,63}")) throw new AssertionError("unsafe mod ID");
            Path file = Path.of("build/libs", edition.modId + ".jar");
            try (ZipFile jar = new ZipFile(file.toFile())) {
                String metadata = new String(jar.getInputStream(jar.getEntry("mod.hjson")).readAllBytes(), StandardCharsets.UTF_8);
                String main = edition == Edition.PLUS ? "org.schemetools.SchemeSizePlusMod" : "org.schemetools.SchemeSizeGodMod";
                if (!metadata.contains("name: " + edition.modId + "\n") || !metadata.contains("main: " + main + "\n")
                        || !metadata.contains("hidden: true")) throw new AssertionError("incorrect edition metadata: " + file);
                var iconEntry=jar.getEntry("icon.png");
                if(iconEntry==null)throw new AssertionError("missing mod icon");
                var icon=javax.imageio.ImageIO.read(jar.getInputStream(iconEntry));
                if(icon==null||icon.getWidth()!=256||icon.getHeight()!=256)throw new AssertionError("expected 256px PNG icon");
                if(!metadata.contains("version: 0.3.1")||!metadata.contains("java: true")||!metadata.contains("minGameVersion: 160.7"))throw new AssertionError("publication metadata mismatch");
                if(!java.util.Arrays.equals(java.nio.file.Files.readAllBytes(Path.of("editions", edition == Edition.PLUS ? "plus" : "god", "icon.png")),jar.getInputStream(iconEntry).readAllBytes()))throw new AssertionError("wrong edition icon");
                byte[] entry = jar.getInputStream(jar.getEntry(main.replace('.', '/') + ".class")).readAllBytes();
                if (((entry[6] & 255) << 8 | entry[7] & 255) != 61) throw new AssertionError("expected Java 17 bytecode");
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    if (name.startsWith("mindustry/") || name.startsWith("arc/") || name.startsWith("probe/")
                            || name.endsWith("Test.class")) throw new AssertionError("unexpected packaged dependency/test: " + name);
                }
            }
        }
        try (ZipFile plus = new ZipFile(Path.of("build/libs", Edition.PLUS.modId + ".jar").toFile());
             ZipFile god = new ZipFile(Path.of("build/libs", Edition.GOD.modId + ".jar").toFile())) {
            if(java.util.Arrays.equals(plus.getInputStream(plus.getEntry("icon.png")).readAllBytes(),god.getInputStream(god.getEntry("icon.png")).readAllBytes()))throw new AssertionError("edition icons must be distinct");
            var plusClasses = plus.stream().filter(entry -> entry.getName().startsWith("org/schemetools/") && entry.getName().endsWith(".class"))
                .map(java.util.zip.ZipEntry::getName).collect(java.util.stream.Collectors.toSet());
            var godClasses = god.stream().filter(entry -> entry.getName().startsWith("org/schemetools/") && entry.getName().endsWith(".class"))
                .map(java.util.zip.ZipEntry::getName).collect(java.util.stream.Collectors.toSet());
            if (!plusClasses.equals(godClasses)) throw new AssertionError("edition code sets differ");
            for (String name : plusClasses) {
                byte[] a = plus.getInputStream(plus.getEntry(name)).readAllBytes();
                byte[] b = god.getInputStream(god.getEntry(name)).readAllBytes();
                if (!java.util.Arrays.equals(a,b)) throw new AssertionError("edition class differs: " + name);
            }
        }
        if (!Edition.GOD.events || Edition.PLUS.events) throw new AssertionError("edition composition");
        System.out.println("ArtifactTest passed: edition metadata, identical shared classes, content boundaries, Java 17");
    }
}
