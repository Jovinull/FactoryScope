package factoryscope.network;

import factoryscope.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ItemConstraintTest{
    @Test
    void alternativesPreserveTheUnionOfBoundaryItemRules(){
        ResourceRef copper = item("copper", "Copper"), lead = item("lead", "Lead"), sand = item("sand", "Sand");

        ItemConstraint union = ItemConstraint.anyOf(List.of(ItemConstraint.only(lead), ItemConstraint.only(copper)));

        assertTrue(union.allows(copper));
        assertTrue(union.allows(lead));
        assertFalse(union.allows(sand));
        assertEquals(union.toString(), ItemConstraint.anyOf(List.of(ItemConstraint.only(copper), ItemConstraint.only(lead))).toString());
    }

    private static ResourceRef item(String id, String name){
        return new ResourceRef(ResourceKind.item, id, name);
    }
}
