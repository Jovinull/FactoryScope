package factoryscope.probe;

import factoryscope.area.*;
import factoryscope.liquid.*;
import factoryscope.model.*;
import factoryscope.trace.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.Floor;
import org.junit.jupiter.api.*;

import java.util.*;

import static mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Dead ends on real layouts: a route ends where the transport it reaches leads nowhere, not at every free side
 * of a conveyor, router or conduit that the route passes through.
 */
class TraceDeadEndsTest{
    private static final ResourceRef sand = new ResourceRef(ResourceKind.item, "sand", "Sand");
    private static final ResourceRef water = new ResourceRef(ResourceKind.liquid, "water", "Water");

    @BeforeAll
    static void boot(){ HeadlessGame.start(); }

    @BeforeEach
    void freshWorld(){ HeadlessGame.newWorld(32); }

    @Test
    void aFedConveyorLineIsNotADeadEndAtItsFreeSides(){
        Building drill = sandDrill(8, 10);
        for(int x = 10; x <= 12; x++) place(Blocks.conveyor, x, 10, 0);
        Building smelter = place(Blocks.siliconSmelter, 13, 10, 0);

        SupplyTrace trace = TraceAnalyzer.input(AreaProbe.scan(AreaSelection.of(7, 7, 17, 14), Team.sharded),
            AreaProbe.refOf(smelter), sand);

        assertTrue(trace.complete);
        assertEquals(List.of(AreaProbe.refOf(drill)), trace.producers().stream().map(endpoint -> endpoint.building).toList());
        assertEquals(List.of(), trace.structuralDeadEnds);
    }

    @Test
    void anUnfedConveyorLineEndsAtItsFirstConveyor(){
        Building first = place(Blocks.conveyor, 10, 10, 0);
        place(Blocks.conveyor, 11, 10, 0);
        place(Blocks.conveyor, 12, 10, 0);
        Building smelter = place(Blocks.siliconSmelter, 13, 10, 0);

        SupplyTrace trace = TraceAnalyzer.input(AreaProbe.scan(AreaSelection.of(7, 7, 17, 14), Team.sharded),
            AreaProbe.refOf(smelter), sand);

        assertTrue(trace.complete);
        assertFalse(trace.noRouteProven);
        assertEquals(List.of(AreaProbe.refOf(first)), trace.structuralDeadEnds);
    }

    @Test
    void anOutputTraceThroughARouterEndsOnlyOnTheBranchThatLeadsNowhere(){
        Building drill = sandDrill(8, 10);
        place(Blocks.conveyor, 10, 10, 0);
        place(Blocks.router, 11, 10, 0);
        place(Blocks.conveyor, 12, 10, 0);
        Building smelter = place(Blocks.siliconSmelter, 13, 10, 0);
        Building branch = place(Blocks.conveyor, 11, 11, 1);

        SupplyTrace trace = TraceAnalyzer.output(AreaProbe.scan(AreaSelection.of(7, 7, 17, 14), Team.sharded),
            AreaProbe.refOf(drill), sand);

        assertEquals(List.of(AreaProbe.refOf(smelter)), trace.endpoints.stream().map(endpoint -> endpoint.building).toList());
        assertEquals(List.of(AreaProbe.refOf(branch)), trace.structuralDeadEnds,
            "the router feeds the smelter; only the conveyor pointing at empty ground ends the route");
    }

    @Test
    void aFedConduitLineIsNotADeadEndAtItsFreeSidesAndAnUnfedOneEndsAtItsFirstConduit(){
        world.tile(8, 10).setFloor((Floor)Blocks.water);
        Building pump = place(Blocks.mechanicalPump, 8, 10, 0);
        for(int x = 9; x <= 11; x++) place(Blocks.conduit, x, 10, 0);
        Building fed = place(Blocks.cryofluidMixer, 12, 10, 0);
        Building first = place(Blocks.conduit, 9, 14, 0);
        for(int x = 10; x <= 11; x++) place(Blocks.conduit, x, 14, 0);
        Building unfed = place(Blocks.cryofluidMixer, 12, 14, 0);

        AreaDiagnosticResult area = AreaProbe.scan(AreaSelection.of(6, 7, 16, 18), Team.sharded);
        LiquidTrace fedTrace = LiquidTraceAnalyzer.input(area, AreaProbe.refOf(fed), water);
        LiquidTrace unfedTrace = LiquidTraceAnalyzer.input(area, AreaProbe.refOf(unfed), water);

        assertEquals(List.of(AreaProbe.refOf(pump)), fedTrace.producers().stream().map(endpoint -> endpoint.building).toList());
        assertEquals(List.of(), fedTrace.structuralDeadEnds);
        assertEquals(List.of(AreaProbe.refOf(first)), unfedTrace.structuralDeadEnds);
    }

    private static Building sandDrill(int x, int y){
        for(int dx = 0; dx <= 1; dx++) for(int dy = 0; dy <= 1; dy++) world.tile(x + dx, y + dy).setFloor((Floor)Blocks.sand);
        return place(Blocks.mechanicalDrill, x, y, 0);
    }

    private static Building place(Block block, int x, int y, int rotation){
        Tile tile = world.tile(x, y);
        tile.setBlock(block, Team.sharded, rotation);
        assertNotNull(tile.build, "failed to place " + block.name);
        return tile.build;
    }
}
