package factoryscope.throughput;

import factoryscope.model.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ItemCountsTest{
    @Test void countsOnlySuccessfulRemovals(){
        ResourceRef copper = new ResourceRef(ResourceKind.item, "copper", "Copper");
        ResourceRef lead = new ResourceRef(ResourceKind.item, "lead", "Lead");
        ItemCounts before = new ItemCounts(Map.of(copper, 3, lead, 2));
        ItemCounts after = new ItemCounts(Map.of(copper, 1, lead, 5));
        assertEquals(Map.of(copper, 2), after.removedSince(before));
    }
}
