package factoryscope.probe;

import factoryscope.model.FactorySnapshot;
import mindustry.gen.Building;

/** Single live-snapshot seam for topology probes; production always uses the default reader. */
final class ProbeSnapshotReader{
    @FunctionalInterface
    interface Reader{
        FactorySnapshot read(Building building);
    }

    private static Reader reader = MindustryFactoryProbe::probe;

    private ProbeSnapshotReader(){
    }

    static FactorySnapshot read(Building building){
        return reader.read(building);
    }

    /** Package-private test seam; callers must close it to restore the production reader. */
    static AutoCloseable replaceForTests(Reader replacement){
        Reader previous = reader;
        reader = replacement;
        return () -> reader = previous;
    }
}
