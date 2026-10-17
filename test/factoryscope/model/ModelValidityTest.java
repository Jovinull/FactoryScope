package factoryscope.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelValidityTest{
    @Test
    void snapshotNeedsABlockName(){
        assertThrows(NullPointerException.class, () -> FactorySnapshot.builder(null));
    }

    @Test
    void resourceStateNeedsDomainIdentity(){
        assertThrows(NullPointerException.class, () -> ResourceState.of(null, "sand"));
        assertThrows(NullPointerException.class, () -> ResourceState.of(ResourceKind.item, null));
    }

    @Test
    void outputNeedsDomainIdentity(){
        assertThrows(NullPointerException.class, () -> new OutputState(null, "sand", "sand", 1f, 1f, 0f, 1f, false));
        assertThrows(NullPointerException.class, () -> new OutputState(ResourceKind.item, null, "sand", 1f, 1f, 0f, 1f, false));
    }
}
