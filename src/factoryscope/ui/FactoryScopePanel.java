package factoryscope.ui;

import arc.graphics.*;
import arc.func.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import factoryscope.*;
import factoryscope.analysis.*;
import factoryscope.model.*;
import factoryscope.probe.*;
import mindustry.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import mindustry.world.meta.*;

/**
 * The diagnostic window.
 *
 * <p>Refreshes only while it is on screen and only every {@value #REFRESH_TICKS} ticks, so an open
 * panel costs a handful of reads five times a second and a closed one costs nothing at all.
 */
public final class FactoryScopePanel extends BaseDialog{
    /** 12 ticks is 0.2s at normal speed: fast enough to feel live, slow enough that numbers stay readable. */
    private static final float REFRESH_TICKS = 12f;
    private static final float ICON_SIZE = 24f;

    private final Interval timer = new Interval();
    private Table body;
    private Building target;

    public FactoryScopePanel(){
        super("");
        addCloseButton();

        cont.pane(table -> body = table).grow().with(pane -> pane.setScrollingDisabled(true, false));
        //the dialog must not keep a dead building alive, nor keep polling one that was removed
        update(() -> {
            if(target == null) return;
            if(targetLost()){
                //the building went away under the panel; say so rather than vanishing
                target = null;
                showTargetLost();
                return;
            }
            if(timer.get(REFRESH_TICKS)) rebuild();
        });
        hidden(() -> target = null);
    }

    public void inspect(Building build){
        target = build;
        title.setText(build.block.localizedName);
        timer.reset(0, 0f);
        rebuild();
        show();
    }

    /** The building on screen right now, or null when the panel is closed or its target is gone. */
    public Building inspected(){
        return isShown() ? target : null;
    }

    private boolean targetLost(){
        return !target.isValid() || target.tile == null || target.tile.build != target;
    }

    private void showTargetLost(){
        body.clear();
        body.top().defaults().growX().left();
        body.labelWrap(FsBundle.get("panel.target-lost")).color(Pal.lightOrange).growX().padTop(8f);
    }

    private void rebuild(){
        if(body == null || target == null) return;
        body.clear();
        body.top().defaults().growX().left();

        FactorySnapshot snapshot;
        DiagnosticResult result;
        try{
            snapshot = MindustryFactoryProbe.probe(target);
            result = FactoryAnalyzer.analyze(snapshot);
        }catch(Exception e){
            FsLog.warnOnce("probe:" + target.block.name,
                "could not analyse " + MindustryFactoryProbe.describe(target), e);
            body.add(FsBundle.get("panel.analysis-failed")).color(Pal.remove).padTop(8f);
            return;
        }

        buildVerdict(snapshot, result);
        if(snapshot.efficiencyTracked) buildEfficiency(snapshot);
        if(snapshot.hasKnownProduction()) buildProduction(snapshot);
        if(!snapshot.inputs.isEmpty()) buildInputs(snapshot);
        if(!snapshot.outputs.isEmpty()) buildBuffers(snapshot);
        if(snapshot.power != null) buildPower(snapshot.power);
        buildNotes(snapshot);
    }

    // ------------------------------------------------------------------ sections

    private void buildVerdict(FactorySnapshot snapshot, DiagnosticResult result){
        Color color = Diagnostics.color(result.reason());

        section("section.status");
        panel(table -> {
            table.add(Diagnostics.status(result.reason())).color(color).growX().left().row();
            table.labelWrap(Diagnostics.explanation(result.primary))
                .color(Pal.lightishGray).growX().padTop(4f).row();

            for(Finding finding : result.secondary()){
                if(finding.severity == Severity.normal) continue;
                table.labelWrap(FsBundle.format("panel.also", Diagnostics.explanation(finding)))
                    .color(Pal.gray).growX().padTop(4f).row();
            }
        });
    }

    private void buildEfficiency(FactorySnapshot snapshot){
        section("section.efficiency");
        panel(table -> {
            float efficiency = snapshot.efficiency;
            Bar bar = new Bar(
                () -> Numbers.percent(efficiency),
                () -> Diagnostics.efficiencyColor(efficiency),
                () -> efficiency);
            //the panel is rebuilt on every refresh, so the bar has to start at its value rather than
            //restart its fill animation from zero each time
            bar.snap();
            table.add(bar).height(20f).growX().padBottom(6f).row();

            if(snapshot.potentialEfficiency > snapshot.efficiency + 0.001f){
                value(table, FsBundle.get("label.potential-efficiency"),
                    Numbers.percent(snapshot.potentialEfficiency), Pal.lightishGray);
            }
            if(Math.abs(snapshot.timeScale - 1f) > 0.001f){
                value(table, FsBundle.get("label.time-scale"),
                    Numbers.multiplier(snapshot.timeScale), Pal.accent);
            }
            if(isMeaningfulMultiplier(snapshot.craftSpeedMultiplier)){
                value(table, FsBundle.get("label.block-speed"),
                    Numbers.multiplier(snapshot.craftSpeedMultiplier), Pal.accent);
            }
            if(isMeaningfulMultiplier(snapshot.blockEfficiencyScale)){
                boolean limiting = snapshot.blockEfficiencyScale < 1f;
                value(table, FsBundle.get(limiting ? "label.block-condition" : "label.block-boost"),
                    Numbers.percent(snapshot.blockEfficiencyScale),
                    limiting ? Pal.lightOrange : Pal.accent);
            }
            if(!snapshot.enabled){
                value(table, FsBundle.get("label.enabled"), FsBundle.get("value.no"), Pal.remove);
            }
        });
    }

    private void buildProduction(FactorySnapshot snapshot){
        section("section.production");
        panel(table -> {
            for(OutputState output : snapshot.outputs){
                table.table(row -> {
                    icon(row, output.kind, output.contentId);
                    name(row, output.name);
                    row.add(FsBundle.format("value.rate",
                            Numbers.rate(output.expectedPerSecond), Numbers.rate(output.theoreticalPerSecond)))
                        .color(rateColor(output)).right();
                }).growX().padBottom(2f).row();
            }
            if(snapshot.craftTimeSeconds > 0f){
                value(table, FsBundle.get("label.craft-time"),
                    FsBundle.format("value.seconds", Numbers.rate(snapshot.craftTimeSeconds)), Pal.lightishGray);
            }
            table.labelWrap(FsBundle.get("panel.rate-note")).color(Pal.gray).growX().padTop(6f).row();
        });
    }

    private void buildInputs(FactorySnapshot snapshot){
        var mandatory = snapshot.mandatoryInputs();
        if(!mandatory.isEmpty()){
            section("section.inputs");
            panel(table -> mandatory.forEach(input -> inputRow(table, input)));
        }

        var optional = snapshot.optionalInputs();
        if(!optional.isEmpty()){
            section("section.optional-inputs");
            panel(table -> {
                table.labelWrap(FsBundle.get("panel.optional-note")).color(Pal.gray).growX().padBottom(4f).row();
                optional.forEach(input -> inputRow(table, input));
            });
        }
    }

    private void inputRow(Table table, ResourceState input){
        table.table(row -> {
            icon(row, input.kind, input.contentId);
            name(row, input.name);

            if(input.hasAmounts()){
                row.add(amountText(input)).color(Pal.lightishGray).right().padRight(8f);
            }
            row.add(satisfactionText(input)).color(satisfactionColor(input)).right();
        }).growX().padBottom(2f).row();

        if(!input.recognised){
            table.add(FsBundle.get("panel.unknown-consumer")).color(Pal.gray).padLeft(ICON_SIZE + 6f).left().row();
        }
    }

    private void buildBuffers(FactorySnapshot snapshot){
        if(snapshot.outputs.stream().noneMatch(OutputState::hasBuffer)) return;

        section("section.buffers");
        panel(table -> {
            for(OutputState output : snapshot.outputs){
                if(!output.hasBuffer()) continue;
                table.table(row -> {
                    icon(row, output.kind, output.contentId);
                    name(row, output.name);
                    row.add(FsBundle.format("value.of",
                            Numbers.amount(output.stored), Numbers.amount(output.capacity)))
                        .color(Pal.lightishGray).right().padRight(8f);
                    row.add(output.bufferFull ? FsBundle.get("value.full") : FsBundle.get("value.ok"))
                        .color(output.bufferFull ? Pal.remove : BlockStatus.active.color).right();
                }).growX().padBottom(2f).row();
            }
        });
    }

    private void buildPower(PowerState power){
        section("section.power");
        panel(table -> {
            value(table, FsBundle.get("label.satisfaction"),
                Numbers.percent(power.satisfaction), Diagnostics.efficiencyColor(power.satisfaction));
            if(power.buffered){
                table.add(FsBundle.get("panel.buffered-power")).color(Pal.gray).left().row();
            }else{
                value(table, FsBundle.get("label.demand"),
                    FsBundle.format("value.power-rate", Numbers.rate(power.usagePerSecond)), Pal.lightishGray);
            }
            value(table, FsBundle.get("label.grid-production"),
                FsBundle.format("value.power-rate", Numbers.rate(power.graphProducedPerSecond)), Pal.lightishGray);
            value(table, FsBundle.get("label.grid-demand"),
                FsBundle.format("value.power-rate", Numbers.rate(power.graphNeededPerSecond)), Pal.lightishGray);
            if(power.balanceReliable){
                value(table, FsBundle.get("label.grid-balance"),
                    FsBundle.format("value.power-rate", Numbers.signedRate(power.graphBalancePerSecond)),
                    power.graphBalancePerSecond < 0f ? Pal.remove : BlockStatus.active.color);
            }
            if(power.hasBatteries()){
                value(table, FsBundle.get("label.batteries"),
                    FsBundle.format("value.of", Numbers.amount(power.batteryStored), Numbers.amount(power.batteryCapacity)),
                    Pal.lightishGray);
            }
        });
    }

    private void buildNotes(FactorySnapshot snapshot){
        if(snapshot.support == SupportLevel.full && !snapshot.consumersBypassed) return;

        panel(table -> {
            if(snapshot.support != SupportLevel.full){
                table.labelWrap(FsBundle.get("panel.limited-support")).color(Pal.lightOrange).growX().row();
            }
            if(snapshot.consumersBypassed){
                table.labelWrap(FsBundle.get("panel.consumers-bypassed")).color(Pal.accent).growX().padTop(4f).row();
            }
        });
    }

    // ------------------------------------------------------------------ small helpers

    private void section(String key){
        body.add(FsBundle.get(key)).color(Pal.accent).padTop(10f).padBottom(2f).left().row();
        //Image.draw() skips a null drawable entirely, so the rule needs a real one
        body.image(Tex.whiteui).height(3f).color(Pal.accent).growX().padBottom(4f).row();
    }

    private void panel(Cons<Table> builder){
        body.table(Tex.pane, table -> {
            table.margin(8f).top().defaults().growX().left();
            builder.get(table);
        }).growX().row();
    }

    private void value(Table table, String label, String value, Color color){
        table.table(row -> {
            name(row, label);
            row.add(value).color(color).right();
        }).growX().padBottom(2f).row();
    }

    /** A label that yields space instead of stretching the dialog when the content name is long. */
    private void name(Table table, String text){
        table.add(text).growX().left().ellipsis(true).minWidth(0f);
    }

    private void icon(Table table, ResourceKind kind, String contentId){
        ContentIcons.add(table, kind, contentId, ICON_SIZE);
    }

    private String amountText(ResourceState input){
        //power has a requirement but nothing "held", so the stored half of the line is dropped
        boolean held = input.stored >= 0f;
        return switch(input.unit){
            case perCraft -> held
                ? FsBundle.format("value.per-craft", Numbers.amount(input.stored), Numbers.amount(input.required))
                : FsBundle.format("value.needed-per-craft", Numbers.amount(input.required));
            case perSecond -> held
                ? FsBundle.format("value.per-second", Numbers.amount(input.stored), Numbers.rate(input.required))
                : FsBundle.format("value.needed-per-second", Numbers.rate(input.required));
            case none -> "";
        };
    }

    private String satisfactionText(ResourceState input){
        if(input.satisfied()) return FsBundle.get("value.ok");
        if(input.missing()) return FsBundle.get("value.missing");
        return Numbers.percent(input.satisfaction);
    }

    private Color satisfactionColor(ResourceState input){
        if(input.satisfied()) return BlockStatus.active.color;
        if(input.optional) return Pal.lightishGray;
        return input.missing() ? Pal.remove : Pal.lightOrange;
    }

    private Color rateColor(OutputState output){
        if(output.theoreticalPerSecond <= 0f) return Pal.lightishGray;
        float ratio = output.expectedPerSecond / output.theoreticalPerSecond;
        return Diagnostics.efficiencyColor(ratio);
    }

    private static boolean isMeaningfulMultiplier(float value){
        return !Float.isNaN(value) && !Float.isInfinite(value) && Math.abs(value - 1f) > 0.01f;
    }
}
