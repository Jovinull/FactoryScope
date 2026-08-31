package factoryscope.probe;

import arc.math.geom.*;
import arc.struct.*;
import factoryscope.*;
import factoryscope.analysis.*;
import factoryscope.area.*;
import factoryscope.model.*;
import mindustry.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;

import java.util.*;

/**
 * Collects the buildings inside an {@link AreaSelection} and runs the existing single-building
 * pipeline over each of them.
 *
 * <h2>How the buildings are found</h2>
 * Through {@code Vars.indexer.eachBlock(Team, Rect, ...)}, which queries the team's own
 * {@code buildingTree} quadtree. Two things follow from that, and both are deliberate:
 * <ul>
 *   <li>The cost scales with the buildings near the selection, not with the size of the map. Walking
 *       {@code Groups.build} would be a full scan - that group is declared without {@code spatial},
 *       so it has no quadtree at all and {@code Groups.build.intersect} is not usable.</li>
 *   <li>The query is rooted at one team's index, so a building belonging to anyone else is never even
 *       visited. An area selection therefore cannot become a way to see through the fog of war.</li>
 * </ul>
 *
 * <p>The quadtree answers with block hitboxes, so the exact tile-footprint test is applied afterwards
 * rather than trusted from the query rectangle.
 */
public final class AreaProbe{
    /** Grows the query rectangle so a footprint exactly on the boundary cannot be lost to float error. */
    private static final float QUERY_MARGIN = 1f;

    private AreaProbe(){
    }

    /**
     * Buildings whose footprint intersects the area, each appearing exactly once.
     *
     * @param viewer the team whose buildings may be inspected; null collects nothing
     */
    public static Seq<Building> collect(AreaSelection selection, Team viewer){
        Seq<Building> found = new Seq<>();
        if(selection == null || viewer == null || Vars.indexer == null || Vars.world == null) return found;

        AreaSelection clamped = selection.clampedTo(Vars.world.width(), Vars.world.height());
        if(clamped == null) return found;

        //what keeps a 3x3 building out of the results nine times is that the quadtree holds one entry
        //per building, not one per occupied tile. This set is insurance against that ever changing:
        //the contract this method promises is one entry per building, whatever the index does
        ObjectSet<Building> seen = new ObjectSet<>();
        Vars.indexer.eachBlock(viewer, worldRect(clamped), build -> true, build -> {
            if(build == null || build.tile == null || !build.isValid()) return;
            if(!clamped.intersectsFootprint(build.tile.x, build.tile.y, build.block.size)) return;
            if(!MindustryFactoryProbe.canInspect(build, viewer)) return;
            if(seen.add(build)) found.add(build);
        });

        return found;
    }

    /** Probes and analyses every building in the area, then aggregates the results. */
    public static AreaDiagnosticResult scan(AreaSelection selection, Team viewer){
        Seq<Building> buildings = collect(selection, viewer);
        List<AreaEntry> entries = new ArrayList<>(buildings.size);

        for(Building build : buildings){
            try{
                FactorySnapshot snapshot = MindustryFactoryProbe.probe(build);
                entries.add(new AreaEntry(refOf(build), snapshot.support, FactoryAnalyzer.analyze(snapshot)));
            }catch(Exception e){
                //one hostile block must not cost the player the whole report
                FsLog.warnOnce("area:" + build.block.name,
                    "could not analyse " + MindustryFactoryProbe.describe(build), e);
            }
        }

        return AreaAnalyzer.analyze(selection, buildings.size, entries).withNetwork(MindustryNetworkProbe.scan(selection, viewer));
    }

    public static BuildingRef refOf(Building build){
        return new BuildingRef(build.tile.x, build.tile.y, build.block.name,
            build.block.localizedName, build.block.size, build.team.id);
    }

    /**
     * The live building a reference points at, or null when it is gone.
     *
     * <p>Identity is re-checked, not just presence: between the scan and the player following a link,
     * the original can have been destroyed and something else built on the same tile. Navigating to
     * that instead would be quietly wrong.
     */
    public static Building resolve(BuildingRef ref){
        if(ref == null || Vars.world == null) return null;
        Tile tile = Vars.world.tile(ref.tileX, ref.tileY);
        if(tile == null) return null;

        Building build = tile.build;
        if(build == null || !build.isValid() || build.tile == null) return null;
        if(build.tile.x != ref.tileX || build.tile.y != ref.tileY) return null;
        if(!build.block.name.equals(ref.blockId) || build.team.id != ref.teamId) return null;
        return build;
    }

    /** Tile bounds as a world-space rectangle, with the half-tile the grid puts around each centre. */
    private static Rect worldRect(AreaSelection selection){
        float half = Vars.tilesize / 2f;
        float x = selection.minX * Vars.tilesize - half - QUERY_MARGIN;
        float y = selection.minY * Vars.tilesize - half - QUERY_MARGIN;
        float x2 = selection.maxX * Vars.tilesize + half + QUERY_MARGIN;
        float y2 = selection.maxY * Vars.tilesize + half + QUERY_MARGIN;
        return new Rect(x, y, x2 - x, y2 - y);
    }
}
