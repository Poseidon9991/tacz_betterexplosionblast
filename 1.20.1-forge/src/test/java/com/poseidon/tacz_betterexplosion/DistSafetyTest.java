package com.poseidon.tacz_betterexplosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Static guard for dedicated servers: client-only classes must be reachable only from the
 * {@code tacz_betterexplosion.client} package, which is loaded solely through {@code DistExecutor}.
 */
class DistSafetyTest {

    private static final String MOD_PACKAGE_DIR = "tacz_betterexplosion";
    private static final String CLIENT_PACKAGE_DIR = "client";
    private static final List<String> FORBIDDEN = List.of(
            "net.minecraft.client",
            "com.mojang.blaze3d",
            "net.minecraftforge.client");

    @Test
    void noClientImportsOutsideClientPackage() throws IOException {
        // Gradle runs tests with the project directory as the working directory.
        Path sources = Path.of("src", "main", "java");
        assertTrue(Files.isDirectory(sources), "expected sources at " + sources.toAbsolutePath());

        List<String> violations = findViolations(sources);

        assertTrue(violations.isEmpty(), "client-only references outside the client package:\n"
                + String.join("\n", violations));
    }

    @Test
    void scannerFlagsViolationsOutsideClientPackageOnly(@TempDir Path root) throws IOException {
        Path modDir = root.resolve("com").resolve("poseidon").resolve(MOD_PACKAGE_DIR);
        write(modDir.resolve("client").resolve("Allowed.java"), "import net.minecraft.client.Minecraft;");
        write(modDir.resolve("client").resolve("deep").resolve("AllowedToo.java"),
                "import com.mojang.blaze3d.systems.RenderSystem;");
        write(modDir.resolve("fx").resolve("Clean.java"), "import net.minecraft.world.level.Level;");
        write(modDir.resolve("fx").resolve("MinecraftClient.java"), "import net.minecraft.client.Minecraft;");
        write(modDir.resolve("net").resolve("Blaze.java"), "import com.mojang.blaze3d.vertex.PoseStack;");
        write(modDir.resolve("mixin").resolve("ForgeClient.java"),
                "import net.minecraftforge.client.event.ViewportEvent;");
        // A directory named "client" elsewhere in the tree is not the exempt package.
        write(modDir.resolve("fx").resolve("client").resolve("Sneaky.java"),
                "import net.minecraft.client.Minecraft;");

        List<String> violations = findViolations(root);

        assertEquals(4, violations.size(), violations.toString());
        assertTrue(violations.stream().anyMatch(v -> v.contains("MinecraftClient.java")), violations.toString());
        assertTrue(violations.stream().anyMatch(v -> v.contains("Blaze.java")), violations.toString());
        assertTrue(violations.stream().anyMatch(v -> v.contains("ForgeClient.java")), violations.toString());
        assertTrue(violations.stream().anyMatch(v -> v.contains("Sneaky.java")), violations.toString());
    }

    /** Lists every {@code .java} file under {@code root} (except the mod's client package) with a forbidden reference. */
    private static List<String> findViolations(Path root) throws IOException {
        List<String> violations = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (isModClientPackage(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.getFileName().toString().endsWith(".java")) {
                    String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                    for (String forbidden : FORBIDDEN) {
                        if (content.contains(forbidden)) {
                            violations.add(file + " contains '" + forbidden + "'");
                        }
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return violations;
    }

    /** True for exactly the directory {@code <...>/tacz_betterexplosion/client}. */
    private static boolean isModClientPackage(Path dir) {
        Path name = dir.getFileName();
        Path parent = dir.getParent();
        return name != null && parent != null && parent.getFileName() != null
                && name.toString().equals(CLIENT_PACKAGE_DIR)
                && parent.getFileName().toString().equals(MOD_PACKAGE_DIR);
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }
}
