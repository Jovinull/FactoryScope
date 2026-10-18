package factoryscope.tools;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * Real multiplayer acceptance: an official dedicated server and an official desktop client, both isolated.
 *
 * <p>The server runs the FactoryScope artifact and the multiplayer fixture mod, which builds a fog-of-war world
 * and answers truth requests with the server's own diagnoses. The client runs the same FactoryScope artifact
 * and the acceptance harness in multiplayer mode, joins the server over the loopback interface, and drives the
 * real UI. This source set is build tooling and is never part of a mod artifact.
 */
public final class MultiplayerAcceptanceLauncher{
    private static final String FIXTURE_READY = "[MP-FIXTURE] hosting on port";
    private static final Pattern SERVER_ERROR = Pattern.compile("(?im)^\\[E].*(FactoryScope|MP-FIXTURE|factoryscope)");

    private MultiplayerAcceptanceLauncher(){
    }

    public static void main(String[] args){
        try{
            System.exit(run(parse(args)));
        }catch(Exception e){
            System.err.println("FactoryScope multiplayer acceptance: " + e.getMessage());
            System.exit(1);
        }
    }

    static int run(Map<String, String> options) throws Exception{
        Path project = Paths.get(options.getOrDefault("--project", ".")).toAbsolutePath().normalize();
        Path serverJar = required(options, "--server-jar");
        Path modJar = path(options.get("--mod-jar"), project.resolve("build/libs/FactoryScopeDesktop.jar"));
        Path harnessJar = path(options.get("--harness-jar"), project.resolve("build/libs/FactoryScopeAcceptance.jar"));
        Path fixtureJar = path(options.get("--fixture-jar"), project.resolve("build/libs/FactoryScopeMultiplayerFixture.jar"));
        int timeout = Integer.parseInt(options.getOrDefault("--timeout-seconds", "600"));
        boolean keep = Boolean.parseBoolean(options.getOrDefault("--keep-sandbox", "false"))
            || Boolean.parseBoolean(options.getOrDefault("--capture", "false"));
        for(Path file : List.of(serverJar, modJar, harnessJar, fixtureJar)) AcceptanceLauncher.requireFile(file, file.getFileName().toString());
        verifyServerJar(serverJar);

        AcceptanceLauncher.Options clientOptions = new AcceptanceLauncher.Options();
        clientOptions.project = project;
        clientOptions.mindustryJar = options.containsKey("--mindustry-jar") ? Paths.get(options.get("--mindustry-jar")) : null;
        clientOptions.mindustryPath = options.containsKey("--mindustry-path") ? Paths.get(options.get("--mindustry-path")) : null;
        clientOptions.capture = Boolean.parseBoolean(options.getOrDefault("--capture", "false"));
        if(options.containsKey("--locale")) clientOptions.locale = AcceptanceLauncher.Options.parse(new String[]{"--locale", options.get("--locale")}).locale;
        AcceptanceLauncher.Client client = AcceptanceLauncher.resolveClient(clientOptions);

        int port = freePort();
        Path sandbox = Files.createTempDirectory("factoryscope-multiplayer-");
        Path serverDir = sandbox.resolve("server");
        Path serverMods = serverDir.resolve("config/mods");
        Files.createDirectories(serverMods);
        Files.copy(modJar, serverMods.resolve(modJar.getFileName()));
        Files.copy(fixtureJar, serverMods.resolve(fixtureJar.getFileName()));
        Path serverLog = sandbox.resolve("server-process.log");

        System.out.println("==> Dedicated server: " + serverJar + " on 127.0.0.1:" + port);
        System.out.println("==> Client: " + client.display);
        System.out.println("==> FactoryScope artifact: " + modJar);
        System.out.println("==> Sandbox " + sandbox);

        Process server = new ProcessBuilder(AcceptanceLauncher.javaExecutable(client.operatingSystem).toString(),
            "-Dfactoryscope.mp.port=" + port, "-jar", serverJar.toString())
            .directory(serverDir.toFile())
            .redirectErrorStream(true)
            .redirectOutput(serverLog.toFile())
            .start();
        Process clientProcess = null;
        boolean passed = false;
        try{
            if(!awaitText(server, serverLog, FIXTURE_READY, 120)){
                System.err.println("The dedicated server did not host the fixture world. Server output: " + serverLog);
                return finish(sandbox, true, 1);
            }

            Path clientSandbox = sandbox.resolve("client");
            Files.createDirectories(clientSandbox);
            Map<String, String> platform = AcceptanceLauncher.isolatedEnvironment(clientSandbox, client.operatingSystem);
            Path dataDirectory = AcceptanceLauncher.dataDirectory(clientSandbox, client.operatingSystem, platform);
            Path mods = dataDirectory.resolve("mods");
            Files.createDirectories(mods);
            Files.copy(modJar, mods.resolve(modJar.getFileName()));
            Files.copy(harnessJar, mods.resolve(harnessJar.getFileName()));
            Files.writeString(clientSandbox.resolve("version.properties"), "number=8\nbuild=" + AcceptanceLauncher.GAME_VERSION
                + "\nmodifier=release\ntype=official\ncommitHash=acceptance\n", StandardCharsets.US_ASCII);

            List<String> command = new ArrayList<>(client.command(clientSandbox, dataDirectory, clientOptions));
            command.add(1, "-Dfactoryscope.acceptance.server=127.0.0.1:" + port);
            ProcessBuilder builder = new ProcessBuilder(command).directory(clientSandbox.toFile());
            builder.environment().putAll(AcceptanceLauncher.isolatedEnvironment(clientSandbox, dataDirectory, client.operatingSystem));
            builder.redirectErrorStream(true).redirectOutput(sandbox.resolve("client-process.log").toFile());
            clientProcess = builder.start();

            Path gameLog = dataDirectory.resolve("last_log.txt");
            AcceptanceLauncher.ProcessResult result = AcceptanceLauncher.await(clientProcess, gameLog, timeout);
            String log = result.log;
            String serverText = read(serverLog);

            for(String line : log.split("\\R")) if(line.contains("[HARNESS]")) System.out.println(line);
            for(String line : serverText.split("\\R")) if(line.contains("[MP-FIXTURE]") || line.contains("FactoryScope")) System.out.println("[server] " + line);

            boolean clientPassed = AcceptanceLauncher.acceptanceLogPassed(modJar, log, result.completed);
            boolean serverLoadedMod = serverText.contains("factory-scope") || serverText.contains("FactoryScope");
            boolean serverClean = !SERVER_ERROR.matcher(serverText).find();
            if(result.timedOut) System.err.println("The multiplayer suite did not finish within " + timeout + " seconds.");
            if(!serverLoadedMod) System.err.println("The dedicated server did not load FactoryScope.");
            if(!serverClean) System.err.println("The dedicated server logged a FactoryScope or fixture error.");

            passed = clientPassed && serverLoadedMod && serverClean;
            System.out.println(passed ? "MULTIPLAYER ACCEPTANCE PASSED." : "MULTIPLAYER ACCEPTANCE FAILED. Sandbox retained at " + sandbox);
            if(passed){
                Path saved = project.resolve("build/multiplayer-acceptance-client.log");
                Files.createDirectories(saved.getParent());
                Files.copy(gameLog, saved, StandardCopyOption.REPLACE_EXISTING);
                Files.copy(serverLog, project.resolve("build/multiplayer-acceptance-server.log"), StandardCopyOption.REPLACE_EXISTING);
            }
            return finish(sandbox, keep || !passed, passed ? 0 : 1);
        }finally{
            stop(clientProcess, null);
            stop(server, "exit");
        }
    }

    /** The server jar must be the official build this tooling validates. */
    static void verifyServerJar(Path jar){
        try(JarFile file = new JarFile(jar.toFile())){
            if(file.getEntry("mindustry/server/ServerLauncher.class") == null){
                throw new IllegalArgumentException("Not a Mindustry dedicated server jar: " + jar);
            }
            var metadata = file.getEntry("version.properties");
            if(metadata == null) throw new IllegalArgumentException("Server jar has no version.properties: " + jar);
            Properties properties = new Properties();
            try(var input = file.getInputStream(metadata)){
                properties.load(input);
            }
            String build = properties.getProperty("build", "").trim();
            String type = properties.getProperty("type", "").trim();
            if(!AcceptanceLauncher.GAME_VERSION.equals(build) || !"official".equalsIgnoreCase(type)){
                throw new IllegalArgumentException("Server jar is not the official v" + AcceptanceLauncher.GAME_VERSION
                    + " server (build=" + build + ", type=" + type + "): " + jar);
            }
        }catch(IOException e){
            throw new IllegalArgumentException("Could not inspect server jar: " + jar, e);
        }
    }

    private static boolean awaitText(Process process, Path log, String text, int seconds) throws InterruptedException{
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while(System.nanoTime() < deadline){
            if(read(log).contains(text)) return true;
            if(!process.isAlive()) return false;
            Thread.sleep(200L);
        }
        return false;
    }

    private static void stop(Process process, String command) throws InterruptedException{
        if(process == null || !process.isAlive()) return;
        if(command != null){
            try{
                OutputStream input = process.getOutputStream();
                input.write((command + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
                input.flush();
                if(process.waitFor(15, TimeUnit.SECONDS)) return;
            }catch(IOException ignored){
                //fall through to termination
            }
        }
        process.destroy();
        if(process.waitFor(5, TimeUnit.SECONDS)) return;
        process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        process.waitFor(10, TimeUnit.SECONDS);
    }

    private static int finish(Path sandbox, boolean keep, int code) throws IOException{
        if(keep) System.out.println("Sandbox kept at " + sandbox);
        else AcceptanceLauncher.cleanupSandbox(sandbox);
        return code;
    }

    private static int freePort() throws IOException{
        try(ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())){
            return socket.getLocalPort();
        }
    }

    private static String read(Path path){
        try{
            //lenient: the server console may print platform-encoded warnings that are not valid UTF-8
            return Files.isRegularFile(path) ? new String(Files.readAllBytes(path), StandardCharsets.UTF_8) : "";
        }catch(IOException | UncheckedIOException e){
            return "";
        }
    }

    private static Path required(Map<String, String> options, String key){
        String value = options.get(key);
        if(value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key);
        return Paths.get(value).toAbsolutePath().normalize();
    }

    private static Path path(String value, Path fallback){
        return value == null || value.isBlank() ? fallback : Paths.get(value).toAbsolutePath().normalize();
    }

    private static Map<String, String> parse(String[] args){
        Map<String, String> options = new HashMap<>();
        for(int i = 0; i + 1 < args.length; i += 2){
            if(!args[i].startsWith("--")) throw new IllegalArgumentException("Unexpected argument " + args[i]);
            if(!args[i + 1].isBlank()) options.put(args[i], args[i + 1]);
        }
        return options;
    }
}
