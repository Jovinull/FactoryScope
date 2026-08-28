package factoryscope.area;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The tile arithmetic behind area selection.
 *
 * <p>Small, boring and worth writing: every defect FactoryScope has shipped so far lived in a
 * coordinate conversion, and this is the one place the rules are written down.
 */
class AreaSelectionTest{
    @Test
    void allFourDragDirectionsNormalizeToTheSameArea(){
        AreaSelection expected = AreaSelection.of(10, 10, 15, 15);

        assertEquals(expected, AreaSelection.of(10, 15, 15, 10), "top-left to bottom-right");
        assertEquals(expected, AreaSelection.of(15, 10, 10, 15), "bottom-right to top-left");
        assertEquals(expected, AreaSelection.of(10, 10, 15, 15), "bottom-left to top-right");
        assertEquals(expected, AreaSelection.of(15, 15, 10, 10), "top-right to bottom-left");
    }

    @Test
    void bothEndpointsAreIncluded(){
        AreaSelection area = AreaSelection.of(10, 10, 15, 15);

        assertEquals(6, area.width());
        assertEquals(6, area.height());
        assertEquals(36, area.tileCount());
        assertTrue(area.contains(10, 10));
        assertTrue(area.contains(15, 15));
        assertFalse(area.contains(16, 15));
        assertFalse(area.contains(9, 10));
    }

    @Test
    void aDragThatNeverLeftItsTileIsASingleTile(){
        AreaSelection area = AreaSelection.of(4, 7, 4, 7);

        assertTrue(area.single());
        assertEquals(1, area.tileCount());
    }

    @Test
    void aTwoByTwoAreaIsNotSingle(){
        assertFalse(AreaSelection.of(4, 7, 5, 8).single());
    }

    @Test
    void equalAreasHashTheSame(){
        assertEquals(AreaSelection.of(1, 2, 3, 4).hashCode(), AreaSelection.of(3, 4, 1, 2).hashCode());
    }

    // ------------------------------------------------------------------ footprints

    @Test
    void aSingleTileBlockIsInsideOnlyItsOwnTile(){
        AreaSelection area = AreaSelection.of(10, 10, 12, 12);

        assertTrue(area.intersectsFootprint(11, 11, 1));
        assertFalse(area.intersectsFootprint(13, 11, 1));
        assertFalse(area.intersectsFootprint(11, 9, 1));
    }

    @Test
    void aThreeByThreeBlockCountsWhenOnlyItsEdgeIsSelected(){
        AreaSelection area = AreaSelection.of(10, 10, 12, 12);

        //centred on 13,11 the block covers 12..14 horizontally, so its left column is inside
        assertTrue(area.intersectsFootprint(13, 11, 3));
        //one tile further out and nothing overlaps
        assertFalse(area.intersectsFootprint(14, 11, 3));
    }

    @Test
    void anEvenSizedBlockUsesTheOffsetTheEngineUses(){
        //Tile.setBlock offsets by -(size - 1) / 2, integer division: a 2x2 block occupies its own tile
        //and the one above and to the right, never the one below or to the left
        AreaSelection below = AreaSelection.of(9, 9, 9, 9);
        AreaSelection own = AreaSelection.of(10, 10, 10, 10);
        AreaSelection upper = AreaSelection.of(11, 11, 11, 11);

        assertFalse(below.intersectsFootprint(10, 10, 2));
        assertTrue(own.intersectsFootprint(10, 10, 2));
        assertTrue(upper.intersectsFootprint(10, 10, 2));
    }

    @Test
    void aLargeBlockIsFoundFromAnySingleTileOfItsFootprint(){
        //a 5x5 block on 20,20 covers 18..22 in both axes
        for(int x = 18; x <= 22; x++){
            for(int y = 18; y <= 22; y++){
                assertTrue(AreaSelection.of(x, y, x, y).intersectsFootprint(20, 20, 5),
                    "tile " + x + "," + y + " is part of the footprint");
            }
        }
        assertFalse(AreaSelection.of(23, 20, 23, 20).intersectsFootprint(20, 20, 5));
        assertFalse(AreaSelection.of(17, 20, 17, 20).intersectsFootprint(20, 20, 5));
    }

    // ------------------------------------------------------------------ clamping

    @Test
    void aSelectionPartlyOutsideTheWorldIsClampedToIt(){
        AreaSelection clamped = AreaSelection.of(-5, -5, 3, 3).clampedTo(10, 10);

        assertNotNull(clamped);
        assertEquals(AreaSelection.of(0, 0, 3, 3), clamped);
    }

    @Test
    void aSelectionEntirelyOutsideTheWorldClampsToNothing(){
        assertNull(AreaSelection.of(-30, -30, -20, -20).clampedTo(10, 10));
        assertNull(AreaSelection.of(50, 50, 60, 60).clampedTo(10, 10));
    }

    @Test
    void aSelectionInsideTheWorldIsUnchangedByClamping(){
        AreaSelection area = AreaSelection.of(2, 2, 7, 7);

        assertEquals(area, area.clampedTo(10, 10));
    }
}
