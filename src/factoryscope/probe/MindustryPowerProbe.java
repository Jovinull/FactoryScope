package factoryscope.probe;

import arc.struct.Seq;
import arc.util.Time;
import factoryscope.FsLog;
import factoryscope.analysis.*;
import factoryscope.area.*;
import factoryscope.model.FactorySnapshot;
import factoryscope.power.*;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.world.blocks.power.PowerDiode;
import mindustry.world.blocks.power.PowerGraph;
import mindustry.world.consumers.ConsumePower;

import java.util.*;

/** Read-only adapter from Mindustry's established PowerGraph state to immutable PowerScope snapshots. */
public final class MindustryPowerProbe{
    private static final float MIN_FRAME_TICKS = 0.0001f;

    private MindustryPowerProbe(){
    }

    /** Captures each live graph at most once, even when many selected buildings belong to it. */
    public static PowerGridReport scan(Iterable<Building> selected, Team viewer,
                                       Map<BuildingRef, FactorySnapshot> knownSnapshots){
        if(selected == null || viewer == null) return PowerGridReport.empty();
        Map<PowerGraph, GraphSeed> graphs = new IdentityHashMap<>();
        List<DiodeSeed> diodes = new ArrayList<>();
        Set<BuildingRef> selectedRefs = new HashSet<>();

        for(Building build : selected){
            if(build == null || !MindustryFactoryProbe.canInspect(build, viewer) || build.team != viewer) continue;
            BuildingRef ref = AreaProbe.refOf(build);
            selectedRefs.add(ref);
            if(build.power != null && build.power.graph != null){
                graphs.computeIfAbsent(build.power.graph, graph -> new GraphSeed(graph, viewer));
            }
            if(build instanceof PowerDiode.PowerDiodeBuild diode){
                Building front = diode.front();
                Building back = diode.back();
                if(validDiodeSide(front, build.team) && validDiodeSide(back, build.team)
                    && front.power.graph != back.power.graph){
                    graphs.computeIfAbsent(front.power.graph, graph -> new GraphSeed(graph, viewer));
                    graphs.computeIfAbsent(back.power.graph, graph -> new GraphSeed(graph, viewer));
                    //PowerDiode.updateTile transfers from back to front only when battery percentages allow it.
                    diodes.add(new DiodeSeed(ref, back.power.graph, front.power.graph));
                }
            }
        }

        List<GraphSeed> ordered = new ArrayList<>(graphs.values());
        for(GraphSeed seed : ordered) seed.collectMembers();
        ordered.removeIf(seed -> seed.members.isEmpty());
        ordered.sort(Comparator.comparing(GraphSeed::anchor, MindustryPowerProbe::compareRefs));

        Map<PowerGraph, Integer> ids = new IdentityHashMap<>();
        for(int i = 0; i < ordered.size(); i++) ids.put(ordered.get(i).graph, i);

        List<PowerGridResult> results = new ArrayList<>(ordered.size());
        Map<Integer, PowerGridSnapshot> snapshots = new HashMap<>();
        for(int i = 0; i < ordered.size(); i++){
            GraphSeed seed = ordered.get(i);
            try{
                PowerGridSnapshot snapshot = seed.snapshot(i, selectedRefs, knownSnapshots);
                results.add(PowerGridAnalyzer.analyze(snapshot));
                snapshots.put(i, snapshot);
            }catch(Exception e){
                FsLog.warnOnce("power-grid:" + seed.anchor(),
                    "could not snapshot power grid at " + seed.anchor(), e);
            }
        }

        List<PowerDiodeLink> links = new ArrayList<>();
        for(DiodeSeed diode : diodes){
            Integer from = ids.get(diode.from), to = ids.get(diode.to);
            if(from != null && to != null && !from.equals(to)){
                PowerGridSnapshot fromSnapshot = snapshots.get(from), toSnapshot = snapshots.get(to);
                PowerDiodeBatteryState batteryState;
                if(fromSnapshot == null || toSnapshot == null || !fromSnapshot.hasMetrics || !toSnapshot.hasMetrics
                    || !fromSnapshot.visibilityComplete || !toSnapshot.visibilityComplete){
                    batteryState = PowerDiodeBatteryState.unavailable;
                }else if(fromSnapshot.batteryCapacity <= 0f || toSnapshot.batteryCapacity <= 0f){
                    batteryState = PowerDiodeBatteryState.atLeastOneEndpointLacksCapacity;
                }else{
                    batteryState = PowerDiodeBatteryState.bothEndpointsHaveCapacity;
                }
                links.add(new PowerDiodeLink(diode.ref, from, to, batteryState));
            }
        }
        links.sort(Comparator.comparing(link -> link.diode, MindustryPowerProbe::compareRefs));
        return new PowerGridReport(results, links);
    }

    public static PowerGridReport scan(Building selected, Team viewer){
        return selected == null ? PowerGridReport.empty() : scan(List.of(selected), viewer, Map.of());
    }

    public static PowerGridReport scan(Collection<Building> selected, Team viewer){
        return selected == null ? PowerGridReport.empty() : scan((Iterable<Building>)selected, viewer, Map.of());
    }

    private static boolean validDiodeSide(Building build, Team team){
        return build != null && build.isValid() && build.team == team && build.power != null
            && build.power.graph != null && build.block.hasPower
            && MindustryFactoryProbe.canInspect(build, team);
    }

    private static int compareRefs(BuildingRef a, BuildingRef b){
        int x = Integer.compare(a.tileX, b.tileX);
        if(x != 0) return x;
        int y = Integer.compare(a.tileY, b.tileY);
        if(y != 0) return y;
        int block = a.blockId.compareTo(b.blockId);
        return block != 0 ? block : Integer.compare(a.teamId, b.teamId);
    }

    private static final class DiodeSeed{
        final BuildingRef ref;
        final PowerGraph from, to;
        DiodeSeed(BuildingRef ref, PowerGraph from, PowerGraph to){
            this.ref = ref;
            this.from = from;
            this.to = to;
        }
    }

    private static final class GraphSeed{
        final PowerGraph graph;
        final Team team;
        final List<Building> members = new ArrayList<>();
        final IdentityHashMap<Building, Boolean> memberSet = new IdentityHashMap<>();
        boolean visibilityComplete = true;

        GraphSeed(PowerGraph graph, Team team){
            this.graph = graph;
            this.team = team;
        }

        void collectMembers(){
            //PowerGraph.all is the engine's authoritative membership, not a spatial reconstruction.
            for(Building build : graph.all){
                if(build == null || !build.isValid() || build.power == null || build.power.graph != graph
                    || build.team != team || !MindustryFactoryProbe.canInspect(build, team)){
                    visibilityComplete = false;
                    continue;
                }
                if(memberSet.put(build, Boolean.TRUE) == null) members.add(build);
            }
            for(Building build : graph.producers) if(!memberSet.containsKey(build)) visibilityComplete = false;
            for(Building build : graph.consumers) if(!memberSet.containsKey(build)) visibilityComplete = false;
            for(Building build : graph.batteries) if(!memberSet.containsKey(build)) visibilityComplete = false;
            members.sort(Comparator.comparing(AreaProbe::refOf, MindustryPowerProbe::compareRefs));
        }

        BuildingRef anchor(){
            return members.isEmpty() ? null : AreaProbe.refOf(members.get(0));
        }

        PowerGridSnapshot snapshot(int id, Set<BuildingRef> selected,
                                   Map<BuildingRef, FactorySnapshot> knownSnapshots){
            BuildingRef anchor = anchor();
            if(anchor == null) throw new IllegalStateException("empty graph snapshot");

            Set<Building> engineProducers = identitySet(graph.producers);
            Set<Building> engineConsumers = identitySet(graph.consumers);
            Set<Building> engineBatteries = identitySet(graph.batteries);
            List<PowerMemberSnapshot> all = new ArrayList<>(members.size());
            List<PowerMemberSnapshot> producers = new ArrayList<>(engineProducers.size());
            List<PowerMemberSnapshot> consumers = new ArrayList<>(engineConsumers.size());
            List<PowerMemberSnapshot> batteries = new ArrayList<>(engineBatteries.size());
            Set<PowerConnection> connections = new TreeSet<>(Comparator
                .comparing((PowerConnection edge) -> edge.first, MindustryPowerProbe::compareRefs)
                .thenComparing(edge -> edge.second, MindustryPowerProbe::compareRefs));
            boolean failedRead = false;
            boolean cheatPowered = PowerGraphMetrics.cheatPowered(graph);
            int inArea = 0;
            double memberGeneration = 0d, memberDemand = 0d;

            for(Building build : members){
                BuildingRef ref = AreaProbe.refOf(build);
                if(selected.contains(ref)) inArea++;
                boolean producer = engineProducers.contains(build);
                boolean consumer = engineConsumers.contains(build);
                boolean battery = engineBatteries.contains(build);
                float generation = 0f, demand = 0f, batteryStored = 0f, batteryCapacity = 0f;
                try{
                    if(producer){
                        generation = PowerGraphMetrics.generationPerSecond(build);
                        memberGeneration += generation;
                    }
                    if(consumer && build.shouldConsumePower){
                        demand = PowerGraphMetrics.demandPerSecond(build);
                        memberDemand += demand;
                    }
                    if(battery){
                        batteryCapacity = build.enabled ? build.block.consPower.capacity : 0f;
                        batteryStored = batteryCapacity * build.power.status;
                    }
                }catch(Exception e){
                    failedRead = true;
                    FsLog.warnOnce("power-member:" + ref, "could not read power member " + ref, e);
                }

                DiagnosticResult diagnostic = null;
                if(producer){
                    try{
                        FactorySnapshot factory = knownSnapshots == null ? null : knownSnapshots.get(ref);
                        if(factory == null) factory = MindustryFactoryProbe.probeForArea(build);
                        diagnostic = FactoryAnalyzer.analyze(factory);
                    }catch(Exception e){
                        FsLog.warnOnce("power-diagnostic:" + ref,
                            "could not diagnose power producer " + ref, e);
                    }
                }
                PowerMemberSnapshot member = new PowerMemberSnapshot(ref, producer, consumer, battery,
                    generation, demand, batteryStored, batteryCapacity, diagnostic);
                all.add(member);
                if(producer) producers.add(member);
                if(consumer) consumers.add(member);
                if(battery) batteries.add(member);

                Seq<Building> connected = build.getPowerConnections(new Seq<>());
                for(Building other : connected){
                    if(other == build || !memberSet.containsKey(other)) continue;
                    connections.add(new PowerConnection(ref, AreaProbe.refOf(other)));
                }
            }

            float generationRate = (float)memberGeneration;
            float demandRate = (float)memberDemand;
            float satisfaction = 0f, balance = 0f, stored = 0f, capacity = 0f;
            boolean balanceReliable = false;
            if(!cheatPowered && visibilityComplete){
                try{
                    //These read-only graph methods are the engine's own aggregate formulas. Their
                    //integrated values are normalized to power/second using the current simulation delta.
                    generationRate = PowerGraphMetrics.generationPerSecond(graph);
                    demandRate = PowerGraphMetrics.demandPerSecond(graph);
                    satisfaction = PowerGraphMetrics.satisfaction(graph);
                    balanceReliable = PowerGraphMetrics.balanceReliable(graph);
                    balance = PowerGraphMetrics.balancePerSecond(graph);
                    stored = PowerGraphMetrics.batteryStored(graph);
                    capacity = PowerGraphMetrics.batteryCapacity(graph);
                }catch(Exception e){
                    failedRead = true;
                    FsLog.warnOnce("power-metrics:" + anchor,
                        "could not read aggregate power metrics for " + anchor, e);
                }
                if(!PowerGraphMetrics.finiteMetrics(generationRate, demandRate, satisfaction,
                    balance, stored, capacity)){
                    failedRead = true;
                }
            }

            boolean metricsAvailable = !cheatPowered && !failedRead && visibilityComplete;
            if(!metricsAvailable){
                generationRate = demandRate = satisfaction = balance = stored = capacity = 0f;
                balanceReliable = false;
            }

            return new PowerGridSnapshot(id, anchor, all, producers, consumers, batteries, connections,
                inArea, inArea < members.size(), cheatPowered, visibilityComplete,
                metricsAvailable,
                generationRate, demandRate, satisfaction, balance, balanceReliable, stored, capacity);
        }

        private static Set<Building> identitySet(Seq<Building> values){
            Set<Building> set = Collections.newSetFromMap(new IdentityHashMap<>());
            for(Building value : values) set.add(value);
            return set;
        }

    }
}
