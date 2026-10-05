package factoryscope;

import factoryscope.analysis.*;
import factoryscope.area.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.nio.ByteBuffer;
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
        List<String> inventory = new ArrayList<>();
        inventory.add("bundle\tkeys\tmissing\textra\tplaceholder_mismatches\tempty\traw_keys\tutf8_errors");

        try(Stream<Path> files = Files.list(BUNDLES)){
            for(Path file : files.filter(BundleTest::isTranslation).toList()){
                Properties translation = read(file);
                String name = file.getFileName().toString();
                int missing = 0, extra = 0, placeholderMismatches = 0, empty = 0, rawKeys = 0, utf8Errors = 0;

                for(String key : bundle.stringPropertyNames()){
                    if(!translation.containsKey(key)){
                        missing++;
                        problems.add(name + " is missing " + key);
                    }
                }
                for(String key : translation.stringPropertyNames()){
                    if(!bundle.containsKey(key)){
                        extra++;
                        problems.add(name + " has an unknown key " + key);
                    }
                }
                for(String key : translation.stringPropertyNames()){
                    if(!bundle.containsKey(key)) continue;
                    try{
                        if(!placeholderUsage(bundle.getProperty(key)).equals(placeholderUsage(translation.getProperty(key)))){
                            placeholderMismatches++;
                            problems.add(name + " changes placeholder indices or multiplicity of " + key);
                        }
                    }catch(IllegalArgumentException malformed){
                        placeholderMismatches++;
                        problems.add(name + " has malformed placeholders in " + key + ": " + malformed.getMessage());
                    }
                    String value = translation.getProperty(key);
                    if(value.isBlank()){
                        empty++;
                        problems.add(name + " has an empty value for " + key);
                    }
                    if(value.equals(key) || value.equals("???" + key + "???") || value.contains("???")){
                        rawKeys++;
                        problems.add(name + " exposes a raw bundle key for " + key);
                    }
                }
                try{
                    String decoded = decodeUtf8(file);
                    if(decoded.contains("�")){
                        utf8Errors++;
                        problems.add(name + " contains a replacement character");
                    }
                }catch(IOException invalidUtf8){
                    utf8Errors++;
                    problems.add(name + " is not valid UTF-8: " + invalidUtf8.getMessage());
                }
                inventory.add(String.join("\t", name, Integer.toString(translation.size()), Integer.toString(missing),
                    Integer.toString(extra), Integer.toString(placeholderMismatches), Integer.toString(empty),
                    Integer.toString(rawKeys), Integer.toString(utf8Errors)));
            }
        }

        Path audit = Path.of("build", "reports", "localization", "bundle-inventory.tsv");
        Files.createDirectories(audit.getParent());
        Files.writeString(audit, String.join("\n", inventory) + "\n", StandardCharsets.UTF_8);
        assertEquals(List.of(), problems, "translations that have drifted from the default bundle");
    }

    @Test
    void translationsAreReadableAsUtf8() throws IOException{
        //Fi.reader() decodes mod bundles as UTF-8, so malformed bytes or replacement glyphs reach players.
        try(Stream<Path> files = Files.list(BUNDLES)){
            for(Path file : files.filter(BundleTest::isTranslation).toList()){
                String text = assertDoesNotThrow(() -> decodeUtf8(file), file.getFileName() + " is not valid UTF-8");
                assertFalse(text.contains("�"), file.getFileName() + " is not valid UTF-8");
            }
        }
    }

    @Test
    void translationsAreNonemptyAndDoNotContainRawBundleKeys() throws IOException{
        try(Stream<Path> files = Files.list(BUNDLES)){
            for(Path file : files.filter(BundleTest::isTranslation).toList()){
                Properties translation = read(file);
                for(String key : translation.stringPropertyNames()){
                    String value = translation.getProperty(key);
                    assertFalse(value.isBlank(), file.getFileName() + " has an empty value for " + key);
                    assertFalse(value.equals(key) || value.equals("???" + key + "???") || value.contains("???"),
                        file.getFileName() + " exposes a raw bundle key for " + key);
                }
            }
        }
    }

    @Test
    void placeholderValidationAllowsReorderingButPreservesExactMultiplicity(){
        assertEquals(Map.of(0, 2, 1, 1), placeholderUsage("{0} / {0} / {1}"));
        assertTrue(placeholdersMatch("{0} of {1}", "из {1}: {0}"), "natural-language order may differ");
        assertFalse(placeholdersMatch("{0} of {1}", "{0}"), "a required placeholder cannot be omitted");
        assertFalse(placeholdersMatch("{0} of {1}", "{0} / {0} / {1}"), "a placeholder cannot be duplicated");
        assertFalse(placeholdersMatch("{0} of {1}", "{0} of {2}"), "an unknown placeholder cannot replace a required one");
    }

    @Test
    void malformedPlaceholderBracesAreRejected(){
        assertThrows(IllegalArgumentException.class, () -> placeholderUsage("missing {0"));
        assertThrows(IllegalArgumentException.class, () -> placeholderUsage("unexpected }"));
        assertThrows(IllegalArgumentException.class, () -> placeholderUsage("named {resource}"));
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

    private static String decodeUtf8(Path file) throws IOException{
        byte[] bytes = Files.readAllBytes(file);
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        try{
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        }catch(CharacterCodingException invalid){
            throw new IOException("invalid UTF-8", invalid);
        }
    }

    /** Exact placeholder usage; grammatical order may change, but indices and repetitions may not. */
    static Map<Integer, Integer> placeholderUsage(String text){
        Map<Integer, Integer> usage = new TreeMap<>();
        for(int index = 0; index < text.length();){
            char current = text.charAt(index);
            if(current == '}') throw new IllegalArgumentException("unexpected closing brace at " + index);
            if(current != '{'){
                index++;
                continue;
            }

            int closing = text.indexOf('}', index + 1);
            if(closing < 0) throw new IllegalArgumentException("unclosed placeholder at " + index);
            String number = text.substring(index + 1, closing);
            if(!number.matches("\\d+")) throw new IllegalArgumentException("invalid placeholder at " + index);
            try{
                usage.merge(Integer.parseInt(number), 1, Integer::sum);
            }catch(NumberFormatException invalid){
                throw new IllegalArgumentException("placeholder index is out of range at " + index, invalid);
            }
            index = closing + 1;
        }
        return usage;
    }

    static boolean placeholdersMatch(String source, String translation){
        return placeholderUsage(source).equals(placeholderUsage(translation));
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
