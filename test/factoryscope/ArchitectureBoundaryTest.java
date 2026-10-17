package factoryscope;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** Guards the documented boundary between FactoryScope-owned reasoning and engine/presentation code. */
class ArchitectureBoundaryTest{
    private static final List<String> PURE_PACKAGES = List.of(
        "analysis", "model", "area", "network", "trace", "liquid", "power"
    );
    private static final List<String> FORBIDDEN_IMPORTS = List.of(
        "import arc.", "import mindustry.", "import factoryscope.ui.", "import factoryscope.probe."
    );

    @Test
    void domainAndAnalysisPackagesDoNotReachIntoEngineOrPresentation() throws IOException{
        for(String packageName : PURE_PACKAGES){
            try(var files = Files.walk(Path.of("src", "factoryscope", packageName))){
                for(Path file : files.filter(path -> path.toString().endsWith(".java")).toList()){
                    String source = Files.readString(file);
                    for(String forbidden : FORBIDDEN_IMPORTS){
                        assertFalse(source.contains(forbidden), () -> file + " imports " + forbidden);
                    }
                }
            }
        }
    }
}
