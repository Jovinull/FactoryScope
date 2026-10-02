package factoryscope.trace;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TraceWordingTest{
    @Test
    void traceMessagesDoNotClaimCausationOrRecommendChanges() throws IOException{
        List<String> forbidden = List.of("root cause", "caused by", "bottleneck", "recommend", "causa raiz",
            "causado por", "gargalo", "recomend", "construa outro", "substitua");
        for(Path bundle : List.of(Path.of("assets/bundles/bundle.properties"),
            Path.of("assets/bundles/bundle_pt_BR.properties"))){
            List<String> traceLines = Files.readAllLines(bundle, StandardCharsets.UTF_8).stream()
                .filter(line -> line.startsWith("factoryscope.trace.")).toList();
            assertFalse(traceLines.isEmpty(), bundle + " contains trace messages");
            for(String line : traceLines){
                String message = line.toLowerCase(Locale.ROOT);
                for(String phrase : forbidden) assertFalse(message.contains(phrase), bundle + ": " + line);
            }
        }
    }
}
