package factoryscope.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class MultiplayerAcceptanceLauncherTest{
    @TempDir Path temp;

    @Test void acceptsOnlyTheOfficialServerOfTheValidatedBuild() throws Exception{
        Path official = temp.resolve("server release.jar");
        serverJar(official, "mindustry/server/ServerLauncher.class", "160.7", "official");
        assertDoesNotThrow(() -> MultiplayerAcceptanceLauncher.verifyServerJar(official));

        Path older = temp.resolve("server-160.5.jar");
        serverJar(older, "mindustry/server/ServerLauncher.class", "160.5", "official");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> MultiplayerAcceptanceLauncher.verifyServerJar(older));
        assertTrue(error.getMessage().contains("160.7"));
        assertTrue(error.getMessage().contains("160.5"));

        Path custom = temp.resolve("server-custom.jar");
        serverJar(custom, "mindustry/server/ServerLauncher.class", "160.7", "custom");
        assertThrows(IllegalArgumentException.class, () -> MultiplayerAcceptanceLauncher.verifyServerJar(custom));
    }

    @Test void rejectsADesktopClientOrAJarWithoutVersionMetadata() throws Exception{
        Path desktop = temp.resolve("Mindustry.jar");
        serverJar(desktop, "mindustry/desktop/DesktopLauncher.class", "160.7", "official");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> MultiplayerAcceptanceLauncher.verifyServerJar(desktop));
        assertTrue(error.getMessage().contains("dedicated server"));

        Path unversioned = temp.resolve("server-unversioned.jar");
        try(JarOutputStream output = new JarOutputStream(Files.newOutputStream(unversioned))){
            output.putNextEntry(new JarEntry("mindustry/server/ServerLauncher.class"));
            output.write(new byte[]{0});
            output.closeEntry();
        }
        assertThrows(IllegalArgumentException.class, () -> MultiplayerAcceptanceLauncher.verifyServerJar(unversioned));

        assertThrows(IllegalArgumentException.class,
            () -> MultiplayerAcceptanceLauncher.verifyServerJar(temp.resolve("missing.jar")));
    }

    private static void serverJar(Path path, String launcher, String build, String type) throws IOException{
        try(JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))){
            output.putNextEntry(new JarEntry(launcher));
            output.write(new byte[]{0});
            output.closeEntry();
            output.putNextEntry(new JarEntry("version.properties"));
            output.write(("number=8\nbuild=" + build + "\nmodifier=release\ntype=" + type + "\n")
                .getBytes(StandardCharsets.US_ASCII));
            output.closeEntry();
        }
    }
}
