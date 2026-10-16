package factoryscope.mpshared;

import factoryscope.analysis.*;
import factoryscope.area.*;
import factoryscope.liquid.*;
import factoryscope.model.*;
import factoryscope.power.*;
import factoryscope.probe.*;
import factoryscope.trace.*;
import mindustry.gen.*;

import java.util.*;

/**
 * Shared by the dedicated-server fixture and the client harness of the multiplayer acceptance run.
 *
 * <p>It fixes where the server builds each fixture, and it reduces a FactoryScope result to a text digest. The
 * server computes the digest from authoritative state and sends it to the client; the client computes it from
 * the state it was sent. Equal digests mean the client reached the diagnosis the server would. Only stable,
 * rounded quantities enter a digest, so the comparison does not depend on frame timing.
 */
public final class MultiplayerDigest{
    /** Custom packet the client sends to ask for the server's digests, and the server answers with. */
    public static final String TRUTH = "fs-mp-truth";

    public static final int WORLD_SIZE = 160;
    public static final int CORE_X = 30, CORE_Y = 30;
    public static final int ENEMY_CORE_X = 130, ENEMY_CORE_Y = 130;

    public static final String SMELTER_OK = "smelter-ok";
    public static final String SMELTER_STARVED = "smelter-starved";
    public static final String LASER_DRILL = "laser-drill";
    public static final String KILN = "kiln";
    public static final String GENERATOR = "generator";
    public static final String MIXER = "mixer";
    public static final String PUMP = "pump";
    public static final String DUO = "duo";
    public static final String FACTORY = "factory";
    public static final String VISIBLE_ENEMY = "visible-enemy";
    public static final String HIDDEN_ENEMY = "hidden-enemy";
    public static final String AREA_KEY = "area";

    /** Centre tile of each fixture building. */
    public static final Map<String, int[]> FIXTURES;

    static{
        Map<String, int[]> fixtures = new LinkedHashMap<>();
        fixtures.put(SMELTER_OK, new int[]{40, 34});
        fixtures.put(SMELTER_STARVED, new int[]{25, 25});
        fixtures.put(LASER_DRILL, new int[]{19, 25});
        fixtures.put(KILN, new int[]{37, 23});
        fixtures.put(GENERATOR, new int[]{36, 23});
        fixtures.put(MIXER, new int[]{22, 36});
        fixtures.put(PUMP, new int[]{18, 36});
        //out of the duo's range of the visible enemy building, which it would otherwise shoot
        fixtures.put(DUO, new int[]{42, 22});
        fixtures.put(FACTORY, new int[]{30, 23});
        fixtures.put(VISIBLE_ENEMY, new int[]{19, 39});
        fixtures.put(HIDDEN_ENEMY, new int[]{124, 130});
        FIXTURES = Collections.unmodifiableMap(fixtures);
    }

    /** Own-team fixtures the client inspects one by one. */
    public static final List<String> OWN = List.of(SMELTER_OK, SMELTER_STARVED, LASER_DRILL, KILN, GENERATOR, MIXER, PUMP, DUO, FACTORY);

    /** The rectangle covering every own fixture and the visible enemy building, in tiles. */
    public static final AreaSelection AREA = AreaSelection.of(17, 21, 43, 39);
    /** The rectangle the client selects to trace sand into the starved smelter. */
    public static final AreaSelection TRACE_AREA = AreaSelection.of(17, 23, 28, 28);
    /** The rectangle the client selects to trace water into the mixer. */
    public static final AreaSelection LIQUID_AREA = AreaSelection.of(17, 35, 25, 39);

    private MultiplayerDigest(){
    }

    public static String building(Building build){
        FactorySnapshot snapshot = MindustryFactoryProbe.probe(build);
        DiagnosticResult result = FactoryAnalyzer.analyze(snapshot);
        StringBuilder text = new StringBuilder()
            .append(snapshot.family).append('|').append(result.reason()).append('|').append(result.severity())
            .append("|gate=").append(snapshot.gate)
            .append("|eff=").append(round(snapshot.efficiency))
            .append("|power=").append(snapshot.power == null ? "-" : round(snapshot.power.satisfaction));
        for(OutputState output : snapshot.outputs){
            text.append("|out=").append(output.contentId).append(':').append(round(output.theoreticalPerSecond));
        }
        return text.toString();
    }

    /** The area report plus the item and liquid traces and power grids derived from it. */
    public static String area(AreaDiagnosticResult report){
        List<String> entries = new ArrayList<>();
        for(AreaEntry entry : report.entries){
            entries.add(ref(entry.ref) + ":" + entry.result.reason());
        }
        Collections.sort(entries);

        List<String> grids = new ArrayList<>();
        if(report.power != null){
            for(PowerGridResult grid : report.power.grids){
                List<String> members = new ArrayList<>();
                for(PowerMemberSnapshot member : grid.snapshot.members) members.add(ref(member.ref));
                Collections.sort(members);
                grids.add(round(grid.snapshot.satisfaction) + "@" + String.join(",", members));
            }
        }
        Collections.sort(grids);

        StringBuilder text = new StringBuilder()
            .append("entries=").append(String.join(";", entries))
            .append(" grids=").append(String.join(";", grids));

        BuildingRef starved = refAt(report, SMELTER_STARVED);
        if(starved != null && report.network != null){
            SupplyTrace trace = TraceAnalyzer.input(report, starved, new ResourceRef(ResourceKind.item, "sand", "sand"));
            text.append(" trace=").append(trace.complete).append(':').append(endpoints(trace));
        }
        BuildingRef mixer = refAt(report, MIXER);
        if(mixer != null && report.liquids != null){
            LiquidTrace trace = LiquidTraceAnalyzer.input(report, mixer, new ResourceRef(ResourceKind.liquid, "water", "water"));
            text.append(" liquid=").append(trace.complete).append(':').append(liquidEndpoints(trace));
        }
        return text.toString();
    }

    public static BuildingRef refAt(AreaDiagnosticResult report, String fixture){
        int[] position = FIXTURES.get(fixture);
        for(AreaEntry entry : report.entries){
            if(entry.ref.tileX == position[0] && entry.ref.tileY == position[1]) return entry.ref;
        }
        return null;
    }

    public static String endpoints(SupplyTrace trace){
        List<String> endpoints = new ArrayList<>();
        for(TraceEndpoint endpoint : trace.endpoints) endpoints.add(endpoint.kind + "@" + ref(endpoint.building));
        Collections.sort(endpoints);
        return String.join(",", endpoints);
    }

    public static String liquidEndpoints(LiquidTrace trace){
        List<String> endpoints = new ArrayList<>();
        for(LiquidTraceEndpoint endpoint : trace.endpoints) endpoints.add(endpoint.kind + "@" + ref(endpoint.building));
        Collections.sort(endpoints);
        return String.join(",", endpoints);
    }

    static String ref(BuildingRef ref){
        return ref.tileX + "," + ref.tileY + ":" + ref.blockId + ":t" + ref.teamId;
    }

    static String round(float value){
        return Float.isNaN(value) || Float.isInfinite(value) ? "?" : String.format(Locale.ROOT, "%.1f", value);
    }

    /** {@code name=digest} lines, as the truth packet carries them. */
    public static Map<String, String> parse(String packet){
        Map<String, String> result = new LinkedHashMap<>();
        for(String line : packet.split("\n")){
            int split = line.indexOf('=');
            if(split > 0) result.put(line.substring(0, split), line.substring(split + 1));
        }
        return result;
    }
}
