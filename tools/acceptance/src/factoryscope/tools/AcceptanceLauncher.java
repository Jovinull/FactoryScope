package factoryscope.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/** Portable real-client acceptance launcher. This source set is not part of the mod artifact. */
public final class AcceptanceLauncher{
    private static final Pattern CHECKS = Pattern.compile("\\[HARNESS] ===== (\\d+) checks, (\\d+) failures =====");
    private static final Pattern STEAM_LIBRARY = Pattern.compile("\\\"path\\\"\\s+\\\"([^\\\"]+)\\\"");
    private static final String GAME_VERSION = "160.5";
    private static final String MOD_NAME = "factory-scope";
    private static final String HARNESS_NAME = "factory-scope-acceptance";

    private AcceptanceLauncher(){
    }

    public static void main(String[] args){
        try{
            Options options = Options.parse(args);
            if(options.help){
                printUsage();
                return;
            }
            int result = run(options);
            System.exit(result);
        }catch(Exception e){
            System.err.println("FactoryScope acceptance: " + e.getMessage());
            System.exit(1);
        }
    }

    static int run(Options options) throws Exception{
        Path project = options.project.toAbsolutePath().normalize();
        Path modJar = options.modJar == null
            ? project.resolve("build/libs/FactoryScopeDesktop.jar") : options.modJar.toAbsolutePath().normalize();
        Path harnessJar = options.harnessJar == null
            ? project.resolve("build/libs/FactoryScopeAcceptance.jar") : options.harnessJar.toAbsolutePath().normalize();
        requireFile(modJar, "FactoryScope mod jar");
        requireFile(harnessJar, "FactoryScope acceptance harness jar");

        Client client = resolveClient(options);
        Path sandbox = Files.createTempDirectory("factoryscope-acceptance-");
        boolean keep = options.keepSandbox || options.capture;
        Map<String, String> platformEnvironment = isolatedEnvironment(sandbox, client.operatingSystem);
        Path dataDirectory = dataDirectory(sandbox, client.operatingSystem, platformEnvironment);
        Map<String, String> sandboxEnvironment = isolatedEnvironment(sandbox, dataDirectory, client.operatingSystem);
        Path mods = dataDirectory.resolve("mods");
        Files.createDirectories(mods);
        Files.copy(modJar, mods.resolve(modJar.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(harnessJar, mods.resolve(harnessJar.getFileName()), StandardCopyOption.REPLACE_EXISTING);

        // Mindustry reads this resource to detect Steam. Put the sandbox first on the classpath so a
        // Steam installation does not initialize Steamworks or import Workshop/user mods.
        Path version = sandbox.resolve("version.properties");
        Files.writeString(version, "number=8\nbuild=160.5\nmodifier=release\ntype=official\ncommitHash=acceptance\n",
            StandardCharsets.US_ASCII);

        List<String> command = client.command(sandbox, dataDirectory, options);
        ProcessBuilder builder = new ProcessBuilder(command).directory(sandbox.toFile());
        Map<String, String> environment = builder.environment();
        environment.putAll(sandboxEnvironment);
        Path processLog = sandbox.resolve("client-process.log");
        builder.redirectErrorStream(true).redirectOutput(processLog.toFile());

        System.out.println("==> Mindustry " + GAME_VERSION + " client: " + client.display);
        System.out.println("==> FactoryScope artifact: " + modJar);
        System.out.println("==> Running in isolated sandbox " + sandbox);
        Process process = builder.start();
        Path gameLog = dataDirectory.resolve("last_log.txt");
        ProcessResult result = await(process, gameLog, options.timeoutSeconds);
        String log = result.log;
        boolean completed = result.completed;

        if(!Files.isRegularFile(gameLog)){
            System.err.println("No Mindustry log was produced. Process output: " + processLog);
            keep = true;
        }
        if(!log.contains("[Mindustry] Version: " + GAME_VERSION)){
            System.err.println("Acceptance client did not identify itself as Mindustry " + GAME_VERSION + ".");
            keep = true;
        }
        Matcher counts = CHECKS.matcher(log);
        int checkCount = -1, failures = -1;
        while(counts.find()){
            checkCount = Integer.parseInt(counts.group(1));
            failures = Integer.parseInt(counts.group(2));
        }
        boolean noExternalMods = log.contains("[HARNESS]   PASS the sandbox loaded no external mods");
        boolean factoryScopeErrors = Pattern.compile("(?im)^\\[E].*\\[FactoryScope] ").matcher(log).find();
        boolean passed = completed && log.contains("[HARNESS] RESULT PASS") && checkCount >= 0 && failures == 0
            && noExternalMods && !factoryScopeErrors
            && log.contains("Loading mod: " + MOD_NAME) && log.contains("Loading mod: " + HARNESS_NAME);

        for(String line : log.split("\\R")) if(line.contains("[HARNESS]")) System.out.println(line);
        if(result.timedOut) System.err.println("Acceptance suite did not finish within " + options.timeoutSeconds + " seconds.");
        else if(!completed) System.err.println("Mindustry exited before the harness reported completion.");
        if(factoryScopeErrors) System.err.println("FactoryScope logged an error during acceptance.");
        if(!noExternalMods) System.err.println("The harness did not prove that no external mods were loaded.");
        if(passed){
            System.out.println("ACCEPTANCE SUITE PASSED: " + checkCount + " checks.");
            Path savedLog = project.resolve("build/acceptance-test.log");
            Files.createDirectories(savedLog.getParent());
            Files.copy(gameLog, savedLog, StandardCopyOption.REPLACE_EXISTING);
            if(!keep) cleanupSandbox(sandbox);
            else System.out.println("Sandbox kept at " + sandbox);
            return 0;
        }

        System.err.println("ACCEPTANCE SUITE FAILED. Sandbox retained at " + sandbox);
        if(Files.isRegularFile(gameLog)) System.err.println("Mindustry log: " + gameLog);
        if(Files.isRegularFile(processLog)) System.err.println("Process output: " + processLog);
        return 1;
    }

    static Path dataDirectory(Path sandbox, OperatingSystem os, Map<String, String> environment){
        switch(os){
            case windows: return sandbox.resolve("Mindustry");
            case linux:
                String xdg = environment.get("XDG_DATA_HOME");
                return (xdg == null || xdg.isBlank() ? sandbox.resolve(".local/share") : Paths.get(xdg))
                    .resolve("Mindustry");
            case mac: return sandbox.resolve("Library/Application Support/Mindustry");
            default: return sandbox.resolve("Mindustry");
        }
    }

    static Map<String, String> isolatedEnvironment(Path sandbox, OperatingSystem os){
        Map<String, String> result = new HashMap<>();
        switch(os){
            case windows:
                result.put("APPDATA", sandbox.toString());
                result.put("LOCALAPPDATA", sandbox.resolve("local-app-data").toString());
                result.put("USERPROFILE", sandbox.toString());
                break;
            case linux:
                result.put("HOME", sandbox.toString());
                result.put("XDG_DATA_HOME", sandbox.resolve("xdg-data").toString());
                break;
            case mac:
                result.put("HOME", sandbox.toString());
                break;
            default:
                result.put("HOME", sandbox.toString());
        }
        return result;
    }

    static Map<String, String> isolatedEnvironment(Path sandbox, Path dataDirectory, OperatingSystem os){
        Map<String, String> result = isolatedEnvironment(sandbox, os);
        result.put("MINDUSTRY_DATA_DIR", dataDirectory.toString());
        return result;
    }

    static Client resolveClient(Options options) throws IOException{
        OperatingSystem os = OperatingSystem.current();
        if(options.mindustryJar != null && options.mindustryPath != null){
            throw new IllegalArgumentException("Choose either -PmindustryJar or -PmindustryPath, not both.");
        }
        if(options.mindustryJar != null){
            Path jar = options.mindustryJar.toAbsolutePath().normalize();
            requireFile(jar, "Mindustry desktop jar");
            try(JarFile file = new JarFile(jar.toFile())){
                if(file.getEntry("mindustry/desktop/DesktopLauncher.class") == null){
                    throw new IllegalArgumentException("Mindustry jar does not contain the desktop launcher: " + jar);
                }
            }
            return Client.jar(jar, os, javaExecutable(os), jar.toString());
        }

        List<Path> candidates = options.mindustryPath == null
            ? discoverInstallations(os, System.getenv()) : List.of(options.mindustryPath.toAbsolutePath().normalize());
        for(Path candidate : candidates){
            if(!Files.isDirectory(candidate)) continue;
            Path jar = findDesktopJar(candidate);
            if(jar != null) return Client.jar(jar, os, javaExecutable(os), candidate.toString());
            Path executable = candidate.resolve("Mindustry.exe");
            if(os == OperatingSystem.windows && Files.isRegularFile(executable)){
                if(isSteamPath(candidate)){
                    throw new IllegalArgumentException("This Steam install has no bundled desktop jar; use -PmindustryJar with an official desktop jar to preserve Workshop isolation.");
                }
                return Client.nativeExe(executable, os, candidate.toString());
            }
        }
        throw new IllegalArgumentException("Mindustry v160.5 was not found. Pass -PmindustryJar=<desktop jar> or -PmindustryPath=<install directory>.");
    }

    static Path findDesktopJar(Path install){
        for(String relative : List.of("jre/desktop.jar", "desktop.jar", "Mindustry.jar", "mindustry.jar")){
            Path candidate = install.resolve(relative);
            if(Files.isRegularFile(candidate)) return candidate.toAbsolutePath().normalize();
        }
        return null;
    }

    static List<Path> discoverInstallations(OperatingSystem os, Map<String, String> env){
        LinkedHashSet<Path> result = new LinkedHashSet<>();
        String homeValue = env.get(os == OperatingSystem.windows ? "USERPROFILE" : "HOME");
        Path home = homeValue == null ? Paths.get(System.getProperty("user.home")) : Paths.get(homeValue);
        if(os == OperatingSystem.windows){
            addIfPresent(result, env.get("ProgramFiles"), "Mindustry");
            addIfPresent(result, env.get("ProgramFiles(x86)"), "Mindustry");
            addIfPresent(result, env.get("LOCALAPPDATA"), "Programs", "Mindustry");
            addSteamLibrary(result, Paths.get("C:/Program Files (x86)/Steam"));
            addSteamLibrary(result, Paths.get("C:/Program Files/Steam"));
        }else if(os == OperatingSystem.linux){
            addSteamLibrary(result, home.resolve(".steam/steam"));
            addSteamLibrary(result, home.resolve(".local/share/Steam"));
            addSteamLibrary(result, home.resolve(".var/app/com.valvesoftware.Steam/.local/share/Steam"));
            addIfPresent(result, "/opt", "Mindustry");
            addIfPresent(result, "/usr/local/games", "Mindustry");
        }else if(os == OperatingSystem.mac){
            addSteamLibrary(result, home.resolve("Library/Application Support/Steam"));
            addIfPresent(result, "/Applications", "Mindustry.app/Contents/Resources");
        }
        return List.copyOf(result);
    }

    private static void addSteamLibrary(Set<Path> result, Path steam){
        addIfPresent(result, steam.toString(), "steamapps", "common", "Mindustry");
        Path vdf = steam.resolve("steamapps/libraryfolders.vdf");
        if(!Files.isRegularFile(vdf)) return;
        try{
            Matcher matcher = STEAM_LIBRARY.matcher(Files.readString(vdf));
            while(matcher.find()) addIfPresent(result, matcher.group(1).replace("\\\\", "\\"), "steamapps", "common", "Mindustry");
        }catch(IOException ignored){
            //Automatic discovery is best-effort; explicit paths remain deterministic.
        }
    }

    static ProcessResult await(Process process, Path logFile, int timeoutSeconds) throws InterruptedException{
        long deadline = System.nanoTime() + Duration.ofSeconds(timeoutSeconds).toNanos();
        String log = "";
        boolean completed = false;
        boolean timedOut = false;
        while(System.nanoTime() < deadline){
            log = readIfPresent(logFile);
            if(log.contains("[HARNESS] RESULT PASS") || log.contains("[HARNESS] RESULT FAIL")){
                completed = true;
                break;
            }
            if(!process.isAlive()) break;
            Thread.sleep(100L);
        }
        if(!completed && process.isAlive()) timedOut = true;
        stopProcessTree(process);
        log = readIfPresent(logFile);
        return new ProcessResult(completed, timedOut, log);
    }

    private static void stopProcessTree(Process process) throws InterruptedException{
        if(!process.isAlive()) return;
        process.destroy();
        if(process.waitFor(5, TimeUnit.SECONDS)) return;
        process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        if(!process.waitFor(10, TimeUnit.SECONDS)){
            throw new IllegalStateException("Mindustry client process did not stop after the harness ended.");
        }
    }

    static final class ProcessResult{
        final boolean completed;
        final boolean timedOut;
        final String log;

        ProcessResult(boolean completed, boolean timedOut, String log){
            this.completed = completed;
            this.timedOut = timedOut;
            this.log = log;
        }
    }

    private static void addIfPresent(Set<Path> result, String root, String... parts){
        if(root == null || root.isBlank()) return;
        Path path = Paths.get(root);
        for(String part : parts) path = path.resolve(part);
        result.add(path.toAbsolutePath().normalize());
    }

    private static boolean isSteamPath(Path path){
        String normalized = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.contains("/steamapps/common/mindustry");
    }

    private static Path javaExecutable(OperatingSystem os){
        Path bin = Paths.get(System.getProperty("java.home"), "bin");
        Path candidate = bin.resolve(os == OperatingSystem.windows ? "java.exe" : "java");
        return Files.isRegularFile(candidate) ? candidate : Paths.get(os == OperatingSystem.windows ? "java.exe" : "java");
    }

    static void requireFile(Path path, String label){
        if(!Files.isRegularFile(path)) throw new IllegalArgumentException(label + " not found: " + path);
    }

    private static String readIfPresent(Path path){
        try{
            return Files.isRegularFile(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
        }catch(IOException ignored){
            return "";
        }
    }

    static void cleanupSandbox(Path root) throws IOException{
        if(!Files.exists(root)) return;
        IOException failure = null;
        for(int attempt = 0; attempt < 8; attempt++){
            try(Stream<Path> paths = Files.walk(root)){
                for(Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                return;
            }catch(IOException e){
                failure = e;
                try{
                    Thread.sleep(250L);
                }catch(InterruptedException interrupted){
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while cleaning acceptance sandbox " + root, interrupted);
                }
            }
        }
        throw new IOException("Could not remove acceptance sandbox " + root, failure);
    }

    private static void printUsage(){
        System.out.println("Usage: gradlew acceptanceTest [-PmindustryJar=<desktop jar> | -PmindustryPath=<install directory>]"
            + " [-PmodJar=<artifact>] [-Plocale=en-US|pt-BR] [-Pcapture=true] [-PkeepSandbox=true]"
            + " [-PtimeoutSeconds=300]");
    }

    enum OperatingSystem{
        windows, linux, mac, other;

        static OperatingSystem current(){ return fromName(System.getProperty("os.name", "")); }

        static OperatingSystem fromName(String name){
            String lower = name.toLowerCase(Locale.ROOT);
            if(lower.contains("windows")) return windows;
            if(lower.contains("mac") || lower.contains("darwin")) return mac;
            if(lower.contains("linux") || lower.contains("bsd")) return linux;
            return other;
        }
    }

    static final class Client{
        final Path path;
        final Path java;
        final OperatingSystem operatingSystem;
        final boolean jar;
        final String display;

        private Client(Path path, Path java, OperatingSystem os, boolean jar, String display){
            this.path = path;
            this.java = java;
            this.operatingSystem = os;
            this.jar = jar;
            this.display = display;
        }

        static Client jar(Path path, OperatingSystem os, Path java, String display){ return new Client(path, java, os, true, display); }
        static Client nativeExe(Path path, OperatingSystem os, String display){ return new Client(path, null, os, false, display); }

        List<String> command(Path sandbox, Path dataDirectory, Options options){
            if(!jar){
                if(options.capture || options.locale != null){
                    throw new IllegalArgumentException("-Pcapture and -Plocale require a Mindustry desktop jar or an install with its bundled desktop jar.");
                }
                return List.of(path.toString());
            }
            List<String> result = new ArrayList<>();
            result.add(java.toString());
            result.add("-Duser.home=" + sandbox);
            result.add("-Dmindustry.data.dir=" + dataDirectory);
            if(options.capture) result.add("-Dfactoryscope.capture=true");
            if(options.locale != null){
                String[] locale = options.locale.split("[-_]", 2);
                result.add("-Duser.language=" + locale[0]);
                if(locale.length == 2) result.add("-Duser.country=" + locale[1]);
            }
            result.add("-cp");
            result.add(sandbox + System.getProperty("path.separator") + path);
            result.add("mindustry.desktop.DesktopLauncher");
            return result;
        }
    }

    static final class Options{
        Path project = Paths.get(".");
        Path mindustryJar;
        Path mindustryPath;
        Path modJar;
        Path harnessJar;
        String locale;
        boolean capture;
        boolean keepSandbox;
        boolean help;
        int timeoutSeconds = 300;

        static Options parse(String[] args){
            Options result = new Options();
            for(int i = 0; i < args.length; i++){
                String key = args[i];
                if(key.equals("--help")){ result.help = true; continue; }
                if(i + 1 >= args.length) throw new IllegalArgumentException("Missing value after " + key);
                String value = args[++i];
                switch(key){
                    case "--project": result.project = Paths.get(value); break;
                    case "--mindustry-jar": result.mindustryJar = blankPath(value); break;
                    case "--mindustry-path": result.mindustryPath = blankPath(value); break;
                    case "--mod-jar": result.modJar = blankPath(value); break;
                    case "--harness-jar": result.harnessJar = blankPath(value); break;
                    case "--locale": result.locale = blank(value); break;
                    case "--capture": result.capture = Boolean.parseBoolean(value); break;
                    case "--keep-sandbox": result.keepSandbox = Boolean.parseBoolean(value); break;
                    case "--timeout-seconds":
                        result.timeoutSeconds = Integer.parseInt(value);
                        if(result.timeoutSeconds < 1) throw new IllegalArgumentException("timeout must be positive");
                        break;
                    default: throw new IllegalArgumentException("Unknown option: " + key);
                }
            }
            return result;
        }

        private static Path blankPath(String value){ String normalized = blank(value); return normalized == null ? null : Paths.get(normalized); }
        private static String blank(String value){ return value == null || value.isBlank() ? null : value; }
    }
}
