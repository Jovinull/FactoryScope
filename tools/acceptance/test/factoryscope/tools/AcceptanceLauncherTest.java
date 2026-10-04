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

    @Test void rejectsWrongClientBuildBeforeSandboxVersionOverride() throws Exception{
        Path jar = temp.resolve("Mindustry 159.2.jar");
        desktopJar(jar, "159.2", "release");

        AcceptanceLauncher.Options options = new AcceptanceLauncher.Options();
        options.mindustryJar = jar;
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> AcceptanceLauncher.resolveClient(options));
        assertTrue(error.getMessage().contains("160.5"));
        assertTrue(error.getMessage().contains("159.2"));
    }

    @Test void requiresAuthoritativeVersionMetadataAndAllowsSteam1605Jar() throws Exception{
        Path missingMetadata = temp.resolve("missing-version.jar");
        desktopJarWithoutVersion(missingMetadata);
        AcceptanceLauncher.Options invalid = new AcceptanceLauncher.Options();
        invalid.mindustryJar = missingMetadata;
        assertThrows(IllegalArgumentException.class, () -> AcceptanceLauncher.resolveClient(invalid));

        Path steamJar = temp.resolve("Steam 160.5.jar");
        desktopJar(steamJar, "160.5", "steam");
        AcceptanceLauncher.Options steam = new AcceptanceLauncher.Options();
        steam.mindustryJar = steamJar;
        assertEquals(steamJar.toAbsolutePath().normalize(), AcceptanceLauncher.resolveClient(steam).path);
    }

    @Test void inspectorReadyLineMustMatchTheSelectedModArtifactVersion() throws Exception{
        Path mod = temp.resolve("FactoryScope.jar");
        modArtifact(mod);
        assertTrue(AcceptanceLauncher.reportsInspectorReady(mod,
            "[FactoryScope] 1.0.0 inspector ready"));
        assertFalse(AcceptanceLauncher.reportsInspectorReady(mod,
            "[FactoryScope] 0.6.0 inspector ready"));
    }

    @Test void malformedHarnessLogsCannotPassAcceptance() throws Exception{
        Path mod = temp.resolve("FactoryScope.jar");
        modArtifact(mod);
        String valid = "[I] [Mindustry] Version: 160.5\n"
            + "[I] Loading mod: factory-scope\n[I] Loading mod: factory-scope-acceptance\n"
            + "[I] [FactoryScope] 1.0.0 inspector ready\n"
            + "[I] [HARNESS]   PASS the sandbox loaded no external mods\n"
            + "[I] [HARNESS] ===== 1 checks, 0 failures =====\n[I] [HARNESS] RESULT PASS\n";
        assertTrue(AcceptanceLauncher.acceptanceLogPassed(mod, valid, true));
        assertFalse(AcceptanceLauncher.acceptanceLogPassed(mod, valid, false));
        assertFalse(AcceptanceLauncher.acceptanceLogPassed(mod,
            valid.replace("===== 1 checks, 0 failures =====\n", ""), true));
        assertFalse(AcceptanceLauncher.acceptanceLogPassed(mod,
            valid.replace("1 checks, 0 failures", "0 checks, 0 failures"), true));
        assertFalse(AcceptanceLauncher.acceptanceLogPassed(mod,
            valid.replace("===== 1 checks, 0 failures =====", "===== 1 checks, 1 failures ====="), true));
        assertFalse(AcceptanceLauncher.acceptanceLogPassed(mod,
            valid.replace("RESULT PASS", "RESULT FAIL"), true));
        assertFalse(AcceptanceLauncher.acceptanceLogPassed(mod,
            valid + "[I] [HARNESS] ===== 1 checks, 0 failures =====\n", true));
        assertFalse(AcceptanceLauncher.acceptanceLogPassed(mod,
            valid + "[I] [HARNESS] RESULT FAIL\n", true));
        assertFalse(AcceptanceLauncher.acceptanceLogPassed(mod,
            valid.replace("PASS the sandbox loaded no external mods", "loaded an external mod"), true));
        assertFalse(AcceptanceLauncher.acceptanceLogPassed(mod,
            valid + "[E] [FactoryScope] test error\n", true));
        assertFalse(AcceptanceLauncher.acceptanceLogPassed(mod,
            valid.replace("Version: 160.5", "Version: 159.2"), true));
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
        assertEquals("pt-BR", AcceptanceLauncher.Options.parse(new String[]{"--locale", "pt_BR"}).locale);
        assertThrows(IllegalArgumentException.class,
            () -> AcceptanceLauncher.Options.parse(new String[]{"--locale", "portuguese"}));
    }

    @Test void nativeOnlyInstallIsRejectedBecauseItsBinaryCannotBeVerified() throws Exception{
        Path install = temp.resolve("native-only");
        Files.createDirectories(install);
        Files.writeString(install.resolve("Mindustry.exe"), "native client");
        AcceptanceLauncher.Options options = new AcceptanceLauncher.Options();
        options.mindustryPath = install;
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> AcceptanceLauncher.resolveClient(options));
        assertTrue(error.getMessage().contains("official v160.5 desktop jar"));
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
        desktopJar(path, "160.5", "release");
    }

    private static void modArtifact(Path path) throws IOException{
        try(JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))){
            output.putNextEntry(new JarEntry("mod.hjson"));
            output.write("name: \"factory-scope\"\nversion: \"1.0.0\"\n"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }

    private static void desktopJar(Path path, String build, String modifier) throws IOException{
        Files.createDirectories(path.getParent());
        try(JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))){
            output.putNextEntry(new JarEntry("mindustry/desktop/DesktopLauncher.class"));
            output.write(new byte[]{0});
            output.closeEntry();
            output.putNextEntry(new JarEntry("version.properties"));
            output.write(("number=8\nbuild=" + build + "\nmodifier=" + modifier + "\ntype=official\n")
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            output.closeEntry();
        }
    }

    private static void desktopJarWithoutVersion(Path path) throws IOException{
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
