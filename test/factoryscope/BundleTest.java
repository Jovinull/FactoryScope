package factoryscope;

import factoryscope.analysis.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks that every string the mod asks for actually exists.
 *
 * <p>A missing key does not crash Mindustry; it renders the raw key in the panel, which is the kind of
 * defect that survives every other test and is only ever noticed by a player.
 */
class BundleTest{
    private static final Path SOURCE_ROOT = Path.of("src");
    private static final Path BUNDLE = Path.of("assets", "bundles", "bundle.properties");
    //only whole-literal keys; keys assembled from an enum are covered by the dedicated tests below
    private static final Pattern LOOKUP = Pattern.compile("FsBundle\\.(?:get|format|ref)\\(\"([^\"]+)\"\\s*[),]");

    private static Properties bundle;

    @BeforeAll
    static void loadBundle() throws IOException{
        bundle = new Properties();
        try(Reader reader = Files.newBufferedReader(BUNDLE, StandardCharsets.UTF_8)){
            bundle.load(reader);
        }
    }

    @Test
    void everyKeyRequestedBySourceCodeIsDefined() throws IOException{
        List<String> missing = new ArrayList<>();

        try(Stream<Path> files = Files.walk(SOURCE_ROOT)){
            for(Path file : files.filter(path -> path.toString().endsWith(".java")).toList()){
                Matcher matcher = LOOKUP.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while(matcher.find()){
                    String key = FsBundle.PREFIX + matcher.group(1);
                    if(!bundle.containsKey(key)) missing.add(key + " (" + file.getFileName() + ")");
                }
            }
        }

        assertEquals(List.of(), missing, "bundle keys requested by the code but never defined");
    }

    @Test
    void everyDiagnosticReasonHasAStatusHeadline(){
        for(DiagnosticReason reason : DiagnosticReason.values()){
            String key = FsBundle.PREFIX + "status." + reason.slug();
            assertTrue(bundle.containsKey(key), "missing " + key);
        }
    }

    @Test
    void everyReachableReasonAndSeverityHasAnExplanation(){
        List<String> missing = new ArrayList<>();

        for(DiagnosticReason reason : DiagnosticReason.values()){
            for(Severity severity : Severity.values()){
                if(!reachable(reason, severity)) continue;
                String key = FsBundle.PREFIX + "diagnosis." + reason.slug() + "." + severity.name();
                if(!bundle.containsKey(key)) missing.add(key);
            }
        }

        assertEquals(List.of(), missing, "reachable diagnoses with no sentence to show");
    }

    @Test
    void reasonSlugsAreReadable(){
        assertEquals("missing-item-input", DiagnosticReason.missingItemInput.slug());
        assertEquals("active", DiagnosticReason.active.slug());
    }

    /** Mirrors what {@link FactoryAnalyzer} can actually emit; see the severity it assigns per reason. */
    private static boolean reachable(DiagnosticReason reason, Severity severity){
        return switch(reason){
            case active, limitedSupport -> severity == Severity.normal;
            case disabled, outputBlocked -> severity == Severity.stopped;
            default -> severity != Severity.normal;
        };
    }
}
