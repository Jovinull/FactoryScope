package factoryscope.network;

import factoryscope.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResourceConstraintTest{
    @Test
    void alternativesPreserveTheUnionOfBoundaryItemRules(){
        ResourceRef copper = item("copper", "Copper"), lead = item("lead", "Lead"), sand = item("sand", "Sand");

        ResourceConstraint union = ResourceConstraint.anyOf(List.of(ResourceConstraint.only(lead), ResourceConstraint.only(copper)));

        assertTrue(union.allows(copper));
        assertTrue(union.allows(lead));
        assertFalse(union.allows(sand));
        assertEquals(union.toString(), ResourceConstraint.anyOf(List.of(ResourceConstraint.only(copper), ResourceConstraint.only(lead))).toString());
    }

    @Test
    void payloadRulesAcceptUnitsAndBlocksWithoutAdmittingItems(){
        ResourceRef copper = new ResourceRef(ResourceKind.item, "copper", "Copper");
        ResourceRef dagger = new ResourceRef(ResourceKind.unit, "dagger", "Dagger");
        ResourceRef copperWall = new ResourceRef(ResourceKind.block, "copper-wall", "Copper Wall");

        assertTrue(ResourceConstraint.anyPayloads().allows(dagger));
        assertTrue(ResourceConstraint.anyPayloads().allows(copperWall));
        assertFalse(ResourceConstraint.anyPayloads().allows(copper));
        assertTrue(ResourceConstraint.only(dagger).allows(dagger));
        assertFalse(ResourceConstraint.except(dagger).allows(dagger));
        assertTrue(ResourceConstraint.except(dagger).allows(copperWall));
    }

    @Test
    void unionsCanCombineItemAndPayloadTransportRules(){
        ResourceRef copper = new ResourceRef(ResourceKind.item, "copper", "Copper");
        ResourceRef dagger = new ResourceRef(ResourceKind.unit, "dagger", "Dagger");
        ResourceConstraint mixed = ResourceConstraint.anyOf(List.of(
            ResourceConstraint.anyItems(), ResourceConstraint.only(dagger)));

        assertTrue(mixed.allows(copper));
        assertTrue(mixed.allows(dagger));
        assertFalse(mixed.allows(new ResourceRef(ResourceKind.block, "copper-wall", "Copper Wall")));
    }

    private static ResourceRef item(String id, String name){
        return new ResourceRef(ResourceKind.item, id, name);
    }
}
