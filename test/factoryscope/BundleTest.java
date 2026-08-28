package factoryscope;

import factoryscope.analysis.*;
import factoryscope.area.*;
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
    private static final Path BUNDLES = Path.of("assets", "bundles");
    private static final Path BUNDLE = BUNDLES.resolve("bundle.properties");
    //only whole-literal keys; keys assembled from an enum are covered by the dedicated tests below
    private static final Pattern LOOKUP = Pattern.compile("FsBundle\\.(?:get|format|ref)\\(\"([^\"]+)\"\\s*[),]");

    private static Properties bundle;

    @BeforeAll
    static void loadBundle() throws IOException{
        bundle = read(BUNDLE);
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
    void everyAreaStatusHasALabel(){
        for(AreaStatus status : AreaStatus.values()){
            String key = FsBundle.PREFIX + "area.status." + status.slug();
            assertTrue(bundle.containsKey(key), "missing " + key);
        }
    }

    /**
     * Every reason that can reach an issue group needs a headline, and the four that name a resource
     * need the variant that takes one. A missing key here would show a raw bundle id in the area list.
     */
    @Test
    void everyIssueReasonHasAHeadline(){
        List<String> missing = new ArrayList<>();

        for(DiagnosticReason reason : DiagnosticReason.values()){
            if(!canBecomeAnIssue(reason)) continue;
            String key = FsBundle.PREFIX + "area.issue." + reason.slug();
            if(!bundle.containsKey(key)) missing.add(key);
            if(namesResource(reason) && !bundle.containsKey(key + ".resource")) missing.add(key + ".resource");
        }

        assertEquals(List.of(), missing, "issue reasons with nothing to show");
    }

    @Test
    void areaStatusSlugsAreReadable(){
        assertEquals("item-shortage", AreaStatus.itemShortage.slug());
        assertEquals("operating", AreaStatus.operating.slug());
        assertEquals("limited-diagnostics", AreaStatus.limitedDiagnostics.slug());
    }

    @Test
    void everyTranslationCoversExactlyTheDefaultBundle() throws IOException{
        List<String> problems = new ArrayList<>();

        try(Stream<Path> files = Files.list(BUNDLES)){
            for(Path file : files.filter(BundleTest::isTranslation).toList()){
                Properties translation = read(file);
                String name = file.getFileName().toString();

                for(String key : bundle.stringPropertyNames()){
                    if(!translation.containsKey(key)) problems.add(name + " is missing " + key);
                }
                for(String key : translation.stringPropertyNames()){
                    if(!bundle.containsKey(key)) problems.add(name + " has an unknown key " + key);
                }
                for(String key : translation.stringPropertyNames()){
                    if(!bundle.containsKey(key)) continue;
                    if(placeholders(bundle.getProperty(key)) != placeholders(translation.getProperty(key))){
                        problems.add(name + " changes the placeholder count of " + key);
                    }
                }
            }
        }

        assertEquals(List.of(), problems, "translations that have drifted from the default bundle");
    }

    @Test
    void translationsAreReadableAsUtf8() throws IOException{
        //Fi.reader() decodes mod bundles as UTF-8, so anything else would reach players as mojibake
        try(Stream<Path> files = Files.list(BUNDLES)){
            for(Path file : files.filter(BundleTest::isTranslation).toList()){
                String text = Files.readString(file, StandardCharsets.UTF_8);
                assertFalse(text.contains("�"), file.getFileName() + " is not valid UTF-8");
            }
        }
    }

    private static boolean isTranslation(Path file){
        String name = file.getFileName().toString();
        return name.startsWith("bundle_") && name.endsWith(".properties");
    }

    private static Properties read(Path file) throws IOException{
        Properties properties = new Properties();
        try(Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)){
            properties.load(reader);
        }
        return properties;
    }

    /** Highest {N} index used, so a translation cannot silently drop an argument. */
    private static int placeholders(String text){
        int highest = -1;
        Matcher matcher = Pattern.compile("\\{(\\d+)}").matcher(text);
        while(matcher.find()) highest = Math.max(highest, Integer.parseInt(matcher.group(1)));
        return highest;
    }

    @Test
    void reasonSlugsAreReadable(){
        assertEquals("missing-item-input", DiagnosticReason.missingItemInput.slug());
        assertEquals("active", DiagnosticReason.active.slug());
    }

    /** An issue group only ever comes from a finding that is not {@link Severity#normal}. */
    private static boolean canBecomeAnIssue(DiagnosticReason reason){
        return reason != DiagnosticReason.active && reason != DiagnosticReason.limitedSupport;
    }

    /** Mirrors {@code AreaText.namesResource}. */
    private static boolean namesResource(DiagnosticReason reason){
        return switch(reason){
            case missingItemInput, missingLiquidInput, outputBlocked, otherConsumerLimited -> true;
            default -> false;
        };
    }

    /** Mirrors what {@link FactoryAnalyzer} can actually emit; see the severity it assigns per reason. */
    private static boolean reachable(DiagnosticReason reason, Severity severity){
        return switch(reason){
            case active, limitedSupport -> severity == Severity.normal;
            case disabled, inoperableHere, outputBlocked, notConsuming -> severity == Severity.stopped;
            default -> severity != Severity.normal;
        };
    }
}
