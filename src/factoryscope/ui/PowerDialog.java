package factoryscope.ui;

import arc.Core;
import arc.func.Cons;
import arc.graphics.Color;
import arc.scene.ui.layout.*;
import factoryscope.*;
import factoryscope.area.*;
import factoryscope.probe.AreaProbe;
import factoryscope.power.*;
import mindustry.Vars;
import mindustry.gen.*;
import mindustry.graphics.Pal;
import mindustry.ui.*;
import mindustry.ui.dialogs.BaseDialog;

import java.util.*;

/** Snapshot view of complete engine-maintained power grids intersecting a selected building/area. */
final class PowerDialog extends BaseDialog{
    private static final int MEMBER_PAGE = 30;
    private PowerGridReport report;
    private Runnable onRefresh;
    private Runnable onBack;
    private Cons<Building> onInspect;
    private Cons<BuildingRef> onLocate;
    private Runnable onViewWorld;
    private Table body;
    private Cell<Table> bodyCell;

    PowerDialog(){
        super("");
        name = "factoryscope-power-dialog";
        title.setText(FsBundle.get("power.title"));
        cont.pane(outer -> {
            outer.top();
            bodyCell = outer.add(body = new Table()).top();
        }).grow().with(pane -> pane.setScrollingDisabled(true, false));
        buttons.button(FsBundle.ref("power.view-world"), Icon.eye, () -> {
            if(report != null && onViewWorld != null){
                hide();
                onViewWorld.run();
            }
        }).size(220f, 64f).name("factoryscope-power-view-world");
        buttons.button(FsBundle.ref("area.refresh"), Icon.refresh, this::refresh)
            .size(180f, 64f).name("factoryscope-power-refresh");
        buttons.button(FsBundle.ref("area.return"), Icon.left, this::back)
            .size(180f, 64f).name("factoryscope-power-back");
    }

    void show(PowerGridReport report, Runnable onRefresh, Cons<Building> onInspect, Cons<BuildingRef> onLocate,
              Runnable onBack){
        this.report = report == null ? PowerGridReport.empty() : report;
        this.onRefresh = onRefresh;
        this.onInspect = onInspect;
        this.onLocate = onLocate;
        this.onBack = onBack;
        rebuild();
        show();
    }

    void refresh(PowerGridReport report){
        if(report == null) return;
        this.report = report;
        rebuild();
    }

    void setOnViewWorld(Runnable onViewWorld){
        this.onViewWorld = onViewWorld;
    }

    PowerGridReport report(){
        return report;
    }

    void reopen(){
        if(report == null) return;
        rebuild();
        show();
    }

    void clearReport(){
        report = null;
        onRefresh = null;
        onBack = null;
        if(body != null) body.clear();
        if(isShown()) hide();
    }

    private void back(){
        Runnable action = onBack;
        clearReport();
        if(action != null) action.run();
    }

    private void refresh(){
        if(onRefresh != null) onRefresh.run();
    }

    private void rebuild(){
        if(body == null) return;
        body.clear();
        body.top().defaults().growX().left();
        float available = Core.scene.getWidth() / Scl.scl() - 40f;
        bodyCell.width(Math.min(Math.max(1f, available), 760f));
        if(report == null) return;

        body.labelWrap(FsBundle.get("snapshot.note")).color(Pal.gray).padBottom(6f).growX().row();
        body.labelWrap(FsBundle.get("power.scope-note")).color(Pal.lightishGray).padBottom(8f).growX().row();
        body.labelWrap(FsBundle.get("power.diode-scope")).color(Pal.gray).padBottom(8f).growX().row();
        if(report.grids.isEmpty()){
            body.labelWrap(FsBundle.get("power.no-grid")).color(Pal.lightOrange).growX().row();
            return;
        }

        section(body, "power.grids");
        for(int i = 0; i < report.grids.size(); i++) buildGrid(i, report.grids.get(i));
        if(!report.diodeLinks.isEmpty()){
            section(body, "power.diodes");
            for(PowerDiodeLink link : report.diodeLinks){
                body.labelWrap(FsBundle.format("power.diode-link", link.diode.blockName,
                    link.fromGrid + 1, link.toGrid + 1)).color(Pal.lightishGray).growX().padBottom(4f).row();
                String diodeStatus = switch(link.batteryState){
                    case bothEndpointsHaveCapacity -> "power.diode-unmeasured";
                    case atLeastOneEndpointLacksCapacity -> "power.diode-no-batteries";
                    case unavailable -> "power.diode-capacity-unavailable";
                };
                body.labelWrap(FsBundle.get(diodeStatus))
                    .color(Pal.gray).padLeft(8f).growX().padBottom(6f).row();
            }
        }
    }

    private void buildGrid(int index, PowerGridResult result){
        PowerGridSnapshot grid = result.snapshot;
        panel(body, table -> {
            table.add(FsBundle.format("power.grid", index + 1)).color(Pal.accent).left().padBottom(5f).row();
            value(table, FsBundle.get("power.status"), FsBundle.get("power.state." + result.state.name()), stateColor(result.state));
            value(table, FsBundle.get("power.selected-members"),
                FsBundle.format("power.members", grid.selectedMemberCount, grid.members.size()), Pal.lightishGray);
            if(grid.extendsOutsideSelection){
                table.labelWrap(FsBundle.get("power.extends-outside")).color(Pal.lightOrange).growX().padTop(3f).row();
            }
            if(grid.hasMetrics){
                value(table, FsBundle.get("power.satisfaction"), Numbers.percent(grid.satisfaction),
                    Diagnostics.efficiencyColor(grid.satisfaction));
                value(table, FsBundle.get("power.generation"), powerRate(grid.generationPerSecond), Pal.lightishGray);
                value(table, FsBundle.get("power.demand"), powerRate(grid.demandPerSecond), Pal.lightishGray);
                if(grid.balanceReliable){
                    value(table, FsBundle.get("power.balance"),
                        FsBundle.format("value.power-rate", Numbers.signedRate(grid.balancePerSecond)),
                        grid.balancePerSecond < 0f ? Pal.remove : Pal.accent);
                }else{
                    table.labelWrap(FsBundle.get("power.balance-collecting")).color(Pal.gray).growX().row();
                }
                if(grid.batteryCapacity > 0f){
                    value(table, FsBundle.get("power.battery"),
                        FsBundle.format("value.of", Numbers.amount(grid.batteryStored), Numbers.amount(grid.batteryCapacity)),
                        Pal.lightishGray);
                }
            }else{
                table.labelWrap(FsBundle.get("power.metrics-unavailable")).color(Pal.lightOrange).growX().row();
            }
            if(!grid.visibilityComplete){
                table.labelWrap(FsBundle.get("power.visibility-incomplete")).color(Pal.lightOrange).growX().row();
            }
            value(table, FsBundle.get("power.generators"), Integer.toString(grid.producers.size()), Pal.lightishGray);
            if(!grid.producers.isEmpty()){
                table.labelWrap(FsBundle.format("power.generator-summary", result.generatorsOperating,
                    result.generatorsStopped, result.generatorsWithProblems, result.generatorsUnclassified))
                    .color(Pal.lightishGray).growX().padTop(2f).row();
            }
            value(table, FsBundle.get("power.consumers"), Integer.toString(grid.consumers.size()), Pal.lightishGray);
            value(table, FsBundle.get("power.batteries"), Integer.toString(grid.batteries.size()), Pal.lightishGray);
            if(result.has(PowerFinding.BATTERY_RESERVES_PRESENT)){
                table.labelWrap(FsBundle.get("power.battery-reserves")).color(Pal.lightOrange).growX().padTop(5f).row();
            }
            if(result.generatorsWithProblems > 0){
                table.labelWrap(FsBundle.format("power.generator-problems", result.generatorsWithProblems))
                    .color(Pal.lightOrange).growX().padTop(3f).row();
            }
        });

        memberSection(body, grid.producers, "power.generator-list", true, grid.hasMetrics);
        memberSection(body, grid.batteries, "power.battery-list", false, grid.hasMetrics);
        memberSection(body, grid.consumers, "power.consumer-list", false, grid.hasMetrics);
    }

    private void memberSection(Table parent, List<PowerMemberSnapshot> members, String titleKey,
                               boolean showDiagnostics, boolean showRates){
        if(members.isEmpty()) return;
        Table listing = new Table();
        Table rows = new Table();
        Table footer = new Table();
        listing.add(rows).growX().row();
        listing.add(footer).growX().row();
        Collapser collapser = new Collapser(listing, true);
        collapser.setDuration(0.15f);
        boolean[] filled = {false};
        parent.button(row -> {
            row.left().margin(4f);
            row.add(FsBundle.get(titleKey)).growX().left();
            row.add("(" + members.size() + ")").color(Pal.gray).right();
        }, Styles.flatt, () -> {
            if(!filled[0]){
                fillMembers(rows, footer, members, 0, showDiagnostics, showRates);
                filled[0] = true;
            }
            collapser.setCollapsed(!collapser.isCollapsed());
        }).growX().padTop(4f).name("factoryscope-power-list-toggle").row();
        parent.add(collapser).growX().padLeft(10f).row();
    }

    private void fillMembers(Table rows, Table footer, List<PowerMemberSnapshot> members, int from,
                             boolean showDiagnostics, boolean showRates){
        rows.clear();
        rows.left().defaults().growX().left();
        int to = Math.min(members.size(), from + MEMBER_PAGE);
        for(int i = from; i < to; i++){
            PowerMemberSnapshot member = members.get(i);
            rows.table(row -> {
                row.add(member.ref.blockName).growX().left().ellipsis(true).minWidth(0f);
                if(showDiagnostics && member.diagnostic != null){
                    AreaStatus status = AreaStatus.of(member.diagnostic.reason());
                    row.add(AreaText.status(status)).color(AreaText.color(status)).right().padLeft(6f);
                }else if(member.battery && showRates){
                    row.add(FsBundle.format("value.of", Numbers.amount(member.batteryStored),
                        Numbers.amount(member.batteryCapacity))).color(Pal.lightishGray).right().padLeft(6f);
                }else if(member.producer && showRates){
                    row.add(powerRate(member.generationPerSecond)).color(Pal.lightishGray).right().padLeft(6f);
                }else if(member.consumer && showRates){
                    row.add(powerRate(member.demandPerSecond)).color(Pal.lightishGray).right().padLeft(6f);
                }
                row.button(Icon.zoomSmall, Styles.emptyi, () -> locate(member.ref)).size(32f)
                    .tooltip(FsBundle.ref("area.locate")).name("factoryscope-power-locate");
                row.button(Icon.info, Styles.emptyi, () -> inspect(member.ref)).size(32f)
                    .tooltip(FsBundle.ref("power.inspect")).name("factoryscope-power-inspect");
            }).growX().padBottom(2f).row();
        }

        footer.clear();
        if(to >= members.size()) return;
        footer.add(FsBundle.format("area.listing-shown", to, members.size())).color(Pal.lightOrange).left().padTop(4f);
        footer.button(FsBundle.ref("area.show-more"), Icon.downOpen, Styles.flatt,
            () -> fillMembers(rows, footer, members, to, showDiagnostics, showRates))
            .height(36f).padLeft(8f).name("factoryscope-power-more");
    }

    private void inspect(BuildingRef ref){
        Building build = FactoryScopeUI.visibleTarget(ref);
        if(build == null){
            FactoryScopeUI.showTargetUnavailable();
        }else if(onInspect != null){
            onInspect.get(build);
        }
    }

    private void locate(BuildingRef ref){
        if(FactoryScopeUI.visibleTarget(ref) == null){
            FactoryScopeUI.showTargetUnavailable();
        }else if(onLocate != null){
            hide();
            onLocate.get(ref);
        }
    }

    private static String powerRate(float value){
        return FsBundle.format("value.power-rate", Numbers.rate(value));
    }

    private static Color stateColor(PowerGridState state){
        return switch(state){
            case underpowered, noGeneration -> Pal.remove;
            case generationBelowDemand -> Pal.lightOrange;
            case cheatPowered, unavailable -> Pal.gray;
            default -> Pal.accent;
        };
    }

    private static void section(Table table, String key){
        table.add(FsBundle.get(key)).color(Pal.accent).padTop(10f).padBottom(2f).left().row();
        table.image(Tex.whiteui).height(3f).color(Pal.accent).growX().padBottom(4f).row();
    }

    private static void panel(Table parent, arc.func.Cons<Table> builder){
        parent.table(Tex.pane, table -> {
            table.margin(8f).top().defaults().growX().left();
            builder.get(table);
        }).growX().padBottom(6f).row();
    }

    private static void value(Table table, String label, String text, arc.graphics.Color color){
        table.table(row -> {
            row.add(label).growX().left().ellipsis(true).minWidth(0f);
            row.add(text).color(color).right().padLeft(8f);
        }).growX().padBottom(2f).row();
    }
}
