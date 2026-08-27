package factoryscope.ui;

import arc.graphics.*;
import arc.func.*;
import arc.graphics.g2d.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import factoryscope.*;
import factoryscope.analysis.*;
import factoryscope.model.*;
import factoryscope.probe.*;
import mindustry.*;
import mindustry.ctype.*;
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
            if(!target.isValid() || target.tile == null || target.tile.build != target){
                hide();
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
                "could not analyse " + target.block.name, e);
            body.add(FsBundle.get("panel.analysis-failed")).color(Pal.remove).padTop(8f);
            return;
        }

        buildVerdict(snapshot, result);
        buildEfficiency(snapshot);
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
            table.add(new Bar(
                () -> Numbers.percent(efficiency),
                () -> Diagnostics.efficiencyColor(efficiency),
                () -> efficiency)).height(20f).growX().padBottom(6f).row();

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
                value(table, FsBundle.get("label.block-condition"),
                    Numbers.percent(snapshot.blockEfficiencyScale), Pal.lightOrange);
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
                    row.add(output.name).growX().left();
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
            row.add(input.name).growX().left();

            if(input.hasAmounts()){
                row.add(amountText(input)).color(Pal.lightishGray).right().padRight(8f);
            }
            row.add(satisfactionText(input)).color(satisfactionColor(input)).right().width(72f);
        }).growX().padBottom(2f).row();

        if(!input.recognised){
            table.add(FsBundle.get("panel.unknown-consumer")).color(Pal.gray).padLeft(ICON_SIZE + 6f).left().row();
        }
    }

    private void buildBuffers(FactorySnapshot snapshot){
        section("section.buffers");
        panel(table -> {
            for(OutputState output : snapshot.outputs){
                if(!output.hasBuffer()) continue;
                table.table(row -> {
                    icon(row, output.kind, output.contentId);
                    row.add(output.name).growX().left();
                    row.add(FsBundle.format("value.of",
                            Numbers.amount(output.stored), Numbers.amount(output.capacity)))
                        .color(Pal.lightishGray).right().padRight(8f);
                    row.add(output.bufferFull ? FsBundle.get("value.full") : FsBundle.get("value.ok"))
                        .color(output.bufferFull ? Pal.remove : BlockStatus.active.color).right().width(72f);
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
        if(snapshot.support == SupportLevel.full && !snapshot.infiniteResources) return;

        panel(table -> {
            if(snapshot.support != SupportLevel.full){
                table.labelWrap(FsBundle.get("panel.limited-support")).color(Pal.lightOrange).growX().row();
            }
            if(snapshot.infiniteResources){
                table.labelWrap(FsBundle.get("panel.infinite-resources")).color(Pal.accent).growX().padTop(4f).row();
            }
        });
    }

    // ------------------------------------------------------------------ small helpers

    private void section(String key){
        body.add(FsBundle.get(key)).color(Pal.accent).padTop(10f).padBottom(2f).left().row();
        body.image().height(3f).color(Pal.accent).growX().padBottom(4f).row();
    }

    private void panel(Cons<Table> builder){
        body.table(Tex.pane, table -> {
            table.margin(8f).top().defaults().growX().left();
            builder.get(table);
        }).growX().row();
    }

    private void value(Table table, String label, String value, Color color){
        table.table(row -> {
            row.add(label).growX().left();
            row.add(value).color(color).right();
        }).growX().padBottom(2f).row();
    }

    private void icon(Table table, ResourceKind kind, String contentId){
        TextureRegion region = regionFor(kind, contentId);
        if(region != null){
            table.image(region).size(ICON_SIZE).padRight(6f);
        }else if(kind == ResourceKind.power){
            table.image(Icon.power).size(ICON_SIZE).color(Pal.power).padRight(6f);
        }else{
            table.image(Icon.none).size(ICON_SIZE).color(Pal.gray).padRight(6f);
        }
    }

    /** Resolves an icon from the content id recorded by the probe; missing content simply yields no icon. */
    private TextureRegion regionFor(ResourceKind kind, String contentId){
        if(contentId == null) return null;
        UnlockableContent content = switch(kind){
            case item -> Vars.content.item(contentId);
            case liquid -> Vars.content.liquid(contentId);
            default -> null;
        };
        return content == null ? null : content.uiIcon;
    }

    private String amountText(ResourceState input){
        return switch(input.unit){
            case perCraft -> FsBundle.format("value.per-craft",
                Numbers.amount(input.stored), Numbers.amount(input.required));
            case perSecond -> FsBundle.format("value.per-second",
                Numbers.amount(input.stored), Numbers.rate(input.required));
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
