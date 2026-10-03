package factoryscope.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class AcceptanceLauncherTest{
    @TempDir Path temp;

    @Test void identifiesSupportedHostNamesWithoutMistakingDarwinForWindows(){
        assertEquals(AcceptanceLauncher.OperatingSystem.windows,
            AcceptanceLauncher.OperatingSystem.fromName("Windows 11"));
        assertEquals(AcceptanceLauncher.OperatingSystem.linux,
            AcceptanceLauncher.OperatingSystem.fromName("Linux"));
        assertEquals(AcceptanceLauncher.OperatingSystem.mac,
            AcceptanceLauncher.OperatingSystem.fromName("Darwin"));
        assertEquals(AcceptanceLauncher.OperatingSystem.mac,
            AcceptanceLauncher.OperatingSystem.fromName("Mac OS X"));
    }

    @Test void appDataMatchesArcDirectoriesOnWindowsLinuxAndMac(){
        Path sandbox = temp.resolve("FactoryScope test λ");
        Map<String, String> windows = AcceptanceLauncher.isolatedEnvironment(sandbox,
            AcceptanceLauncher.OperatingSystem.windows);
        assertEquals(sandbox.toString(), windows.get("APPDATA"));
        assertEquals(sandbox.resolve("Mindustry"), AcceptanceLauncher.dataDirectory(sandbox,
            AcceptanceLauncher.OperatingSystem.windows, windows));
        assertEquals(sandbox.resolve("Mindustry").toString(), AcceptanceLauncher.isolatedEnvironment(sandbox,
            sandbox.resolve("Mindustry"), AcceptanceLauncher.OperatingSystem.windows).get("MINDUSTRY_DATA_DIR"));

        Map<String, String> linux = AcceptanceLauncher.isolatedEnvironment(sandbox,
            AcceptanceLauncher.OperatingSystem.linux);
        assertEquals(sandbox.toString(), linux.get("HOME"));
        assertEquals(sandbox.resolve("xdg-data/Mindustry"), AcceptanceLauncher.dataDirectory(sandbox,
            AcceptanceLauncher.OperatingSystem.linux, linux));
        assertEquals(sandbox.resolve(".local/share/Mindustry"), AcceptanceLauncher.dataDirectory(sandbox,
            AcceptanceLauncher.OperatingSystem.linux, Map.of()));

        Map<String, String> mac = AcceptanceLauncher.isolatedEnvironment(sandbox,
            AcceptanceLauncher.OperatingSystem.mac);
        assertEquals(sandbox.toString(), mac.get("HOME"));
        assertEquals(sandbox.resolve("Library/Application Support/Mindustry"),
            AcceptanceLauncher.dataDirectory(sandbox, AcceptanceLauncher.OperatingSystem.mac, mac));
    }

    @Test void explicitDesktopJarWithSpacesAndUnicodeIsAcceptedAndPassedAsOneArgument() throws Exception{
        Path jar = temp.resolve("Mindustry client λ with spaces.jar");
        desktopJar(jar);
        AcceptanceLauncher.Options options = new AcceptanceLauncher.Options();
        options.mindustryJar = jar;
        AcceptanceLauncher.Client client = AcceptanceLauncher.resolveClient(options);
        Path sandbox = temp.resolve("sandbox λ");
        Path data = sandbox.resolve("isolated-data");
        List<String> command = client.command(sandbox, data, options);

        assertEquals(jar.toAbsolutePath().normalize(), client.path);
        assertEquals(jar.toString(), command.get(command.indexOf("-cp") + 1).split(
            java.util.regex.Pattern.quote(System.getProperty("path.separator")), 2)[1]);
        assertFalse(command.stream().anyMatch(argument -> argument.startsWith("\"") || argument.endsWith("\"")));
        assertEquals("mindustry.desktop.DesktopLauncher", command.get(command.size() - 1));
        assertTrue(command.contains("-Dmindustry.data.dir=" + data));
    }

    @Test void explicitInstallFindsBundledDesktopJarBeforeNativeExecutable() throws Exception{
        Path install = temp.resolve("Steam Library λ/Mindustry");
        Path jar = install.resolve("jre/desktop.jar");
        Files.createDirectories(jar.getParent());
        desktopJar(jar);
        Files.writeString(install.resolve("Mindustry.exe"), "native fallback");

        AcceptanceLauncher.Options options = new AcceptanceLauncher.Options();
        options.mindustryPath = install;
        AcceptanceLauncher.Client client = AcceptanceLauncher.resolveClient(options);
        assertTrue(client.jar);
        assertEquals(jar.toAbsolutePath().normalize(), client.path);
    }

    @Test void missingAndInvalidClientsFailClearly() throws Exception{
        AcceptanceLauncher.Options missing = new AcceptanceLauncher.Options();
        missing.mindustryJar = temp.resolve("missing.jar");
        assertThrows(IllegalArgumentException.class, () -> AcceptanceLauncher.resolveClient(missing));

        Path invalid = temp.resolve("not-mindustry.jar");
        try(JarOutputStream output = new JarOutputStream(Files.newOutputStream(invalid))){
            output.putNextEntry(new JarEntry("readme.txt"));
            output.write("not a game client".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
        AcceptanceLauncher.Options invalidOptions = new AcceptanceLauncher.Options();
        invalidOptions.mindustryJar = invalid;
        assertThrows(IllegalArgumentException.class, () -> AcceptanceLauncher.resolveClient(invalidOptions));

        AcceptanceLauncher.Options conflict = new AcceptanceLauncher.Options();
        conflict.mindustryJar = invalid;
        conflict.mindustryPath = temp;
        assertThrows(IllegalArgumentException.class, () -> AcceptanceLauncher.resolveClient(conflict));
    }

    @Test void explicitInstallDirectoryAndDiscoveryUseNormalFilesystemPaths(){
        Path programFiles = temp.resolve("Program Files λ");
        Map<String, String> env = Map.of("ProgramFiles", programFiles.toString());
        List<Path> candidates = AcceptanceLauncher.discoverInstallations(AcceptanceLauncher.OperatingSystem.windows, env);
        assertTrue(candidates.contains(programFiles.resolve("Mindustry").toAbsolutePath().normalize()));
    }

    @Test void parserPreservesExplicitArtifactsLocaleAndSandboxOptions(){
        AcceptanceLauncher.Options options = AcceptanceLauncher.Options.parse(new String[]{
            "--project", temp.toString(), "--mindustry-jar", temp.resolve("Mindustry Client.jar").toString(),
            "--mod-jar", temp.resolve("FactoryScope λ.jar").toString(), "--locale", "pt-BR",
            "--capture", "true", "--keep-sandbox", "true", "--timeout-seconds", "77"
        });
        assertEquals(temp.resolve("Mindustry Client.jar"), options.mindustryJar);
        assertEquals(temp.resolve("FactoryScope λ.jar"), options.modJar);
        assertEquals("pt-BR", options.locale);
        assertTrue(options.capture);
        assertTrue(options.keepSandbox);
        assertEquals(77, options.timeoutSeconds);
    }

    @Test void nativeWindowsFallbackRejectsOptionsItCannotApply(){
        AcceptanceLauncher.Client client = AcceptanceLauncher.Client.nativeExe(temp.resolve("Mindustry.exe"),
            AcceptanceLauncher.OperatingSystem.windows, "test");
        AcceptanceLauncher.Options capture = new AcceptanceLauncher.Options();
        capture.capture = true;
        assertThrows(IllegalArgumentException.class, () -> client.command(temp, temp.resolve("data"), capture));
        AcceptanceLauncher.Options locale = new AcceptanceLauncher.Options();
        locale.locale = "pt-BR";
        assertThrows(IllegalArgumentException.class, () -> client.command(temp, temp.resolve("data"), locale));
    }

    @Test void completionAndClientCrashAreDistinguished(){
        FakeProcess process = new FakeProcess(true);
        FakeProcess crash = new FakeProcess(false);
        Path log = temp.resolve("last_log.txt");
        assertDoesNotThrow(() -> Files.writeString(log, "[HARNESS] RESULT PASS"));
        AcceptanceLauncher.ProcessResult complete = assertDoesNotThrow(() ->
            AcceptanceLauncher.await(process, log, 1));
        assertTrue(complete.completed);
        assertFalse(complete.timedOut);

        AcceptanceLauncher.ProcessResult exited = assertDoesNotThrow(() ->
            AcceptanceLauncher.await(crash, temp.resolve("missing-log.txt"), 1));
        assertFalse(exited.completed);
        assertFalse(exited.timedOut);
    }

    @Test void timeoutStopsTheClientAndIsReportedSeparately() throws Exception{
        FakeProcess process = new FakeProcess(true);
        AcceptanceLauncher.ProcessResult result = AcceptanceLauncher.await(process,
            temp.resolve("missing-log.txt"), 1);
        assertTrue(result.timedOut);
        assertFalse(process.isAlive());
    }

    @Test void sandboxCleanupRemovesOnlyTheProvidedSandbox() throws Exception{
        Path sandbox = temp.resolve("sandbox to remove");
        Files.createDirectories(sandbox.resolve("Mindustry/mods"));
        Files.writeString(sandbox.resolve("Mindustry/mods/user-data.marker"), "isolated");
        AcceptanceLauncher.cleanupSandbox(sandbox);
        assertFalse(Files.exists(sandbox));
        assertTrue(Files.isDirectory(temp));
    }

    private static void desktopJar(Path path) throws IOException{
        Files.createDirectories(path.getParent());
        try(JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))){
            output.putNextEntry(new JarEntry("mindustry/desktop/DesktopLauncher.class"));
            output.write(new byte[]{0});
            output.closeEntry();
        }
    }

    private static final class FakeProcess extends Process{
        private boolean alive;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        FakeProcess(boolean alive){ this.alive = alive; }

        @Override public OutputStream getOutputStream(){ return output; }
        @Override public java.io.InputStream getInputStream(){ return new ByteArrayInputStream(new byte[0]); }
        @Override public java.io.InputStream getErrorStream(){ return new ByteArrayInputStream(new byte[0]); }
        @Override public int waitFor(){ alive = false; return 0; }
        @Override public boolean waitFor(long timeout, java.util.concurrent.TimeUnit unit){ alive = false; return true; }
        @Override public int exitValue(){ if(alive) throw new IllegalThreadStateException(); return 0; }
        @Override public void destroy(){ alive = false; }
        @Override public Process destroyForcibly(){ alive = false; return this; }
        @Override public boolean isAlive(){ return alive; }
    }
}
