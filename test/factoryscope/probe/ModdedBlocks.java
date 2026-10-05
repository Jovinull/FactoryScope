package factoryscope.probe;

import arc.func.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.distribution.Conveyor;
import mindustry.world.blocks.distribution.ItemBridge;
import mindustry.world.blocks.liquid.Conduit;
import mindustry.world.blocks.production.*;
import mindustry.world.blocks.power.PowerGenerator;
import mindustry.world.consumers.*;
import mindustry.world.meta.*;

/**
 * Blocks that stand in for the shapes other mods actually ship, registered alongside vanilla content
 * in the headless game. They exist only in tests and are never part of the mod.
 */
final class ModdedBlocks{
    static GenericCrafter conventional;
    static GenericCrafter traceConsumer;
    static GenericCrafter boosted;
    static AttributeCrafter yieldScaled;
    static GenericCrafter exotic;
    static Block oddBuilding;
    static Block coalConsumer;
    static Block unknownTransport;
    static Conveyor inheritedConveyor;
    static Conveyor rejectingConveyor;
    static Conduit inheritedConduit;
    static Conduit rejectingConduit;
    static ItemBridge crossTypeBridge;
    static ItemBridge otherCrossTypeBridge;
    static ItemBridge sameTypeBridge;
    static PowerGenerator moddedGenerator;
    static Block moddedPowerConsumer;
    static GenericCrafter liquidSource;
    static GenericCrafter directedLiquidSource;
    static GenericCrafter dualLiquidSource;
    static GenericCrafter mixedDirectionLiquidSource;
    static GenericCrafter invalidDirectionLiquidSource;
    static GenericCrafter invalidPositiveDirectionLiquidSource;
    static GenericCrafter liquidConsumer;
    static GenericCrafter oilConsumer;
    static GenericCrafter filteredLiquidConsumer;
    static GenericCrafter anyLiquidConsumer;
    static GenericCrafter throwingFilterLiquidConsumer;
    static boolean failFilterEnumeration;
    static boolean filterSawWater;
    static GenericCrafter dynamicLiquidConsumer;
    static LiquidStack[] dynamicLiquidRequirements;
    static GenericCrafter hydrogenConsumer;
    static SolidPump solidLiquidPump;
    static Block unknownLiquidConsumer;
    static Block unknownLiquidTransport;

    private ModdedBlocks(){
    }

    /** Must run after {@code content.createBaseContent()} and before {@code content.init()}. */
    static void create(){
        if(conventional != null) return;

        conventional = new GenericCrafter("fs-test-conventional"){{
            craftTime = 60f;
            outputItem = new ItemStack(Items.graphite, 2);
            consumeItems(new ItemStack(Items.copper, 3), new ItemStack(Items.lead, 1));
            consumePower(1f);
        }};

        traceConsumer = new GenericCrafter("fs-test-trace-consumer"){{
            consumeItems(new ItemStack(Items.graphite, 1));
        }};

        boosted = new GenericCrafter("fs-test-boosted"){{
            craftTime = 30f;
            outputItem = new ItemStack(Items.silicon, 1);
            consumeItem(Items.sand, 2);
            //the shape every mod uses for a speed booster: optional, and never required to operate
            consume(new ConsumeItemFlammable()).boost();
        }};

        yieldScaled = new AttributeCrafter("fs-test-yield-scaled"){{
            craftTime = 60f;
            outputItems = new ItemStack[]{
                new ItemStack(Items.graphite, 2),
                new ItemStack(Items.silicon, 1)
            };
            outputScale = 0.5f;
            boostScale = 0.5f;
            maxBoost = 1f;
            itemCapacity = 10;
        }};

        exotic = new GenericCrafter("fs-test-exotic"){{
            craftTime = 45f;
            outputItem = new ItemStack(Items.titanium, 1);
            consumeItem(Items.copper, 1);
            consume(new ConsumeMystery());
        }};

        oddBuilding = new OddBlock("fs-test-odd");
        coalConsumer = new ItemConsumerBlock("fs-test-item-consumer", Items.coal);
        unknownTransport = new UnknownTransportBlock("fs-test-unknown-transport");
        inheritedConveyor = new Conveyor("fs-test-inherited-conveyor");
        rejectingConveyor = new RejectingConveyor("fs-test-rejecting-conveyor");
        inheritedConduit = new Conduit("fs-test-inherited-conduit");
        rejectingConduit = new RejectingConduit("fs-test-rejecting-conduit");
        crossTypeBridge = new ItemBridge("fs-test-cross-type-bridge"){{
            linkSameType = false;
            range = 6;
        }};
        otherCrossTypeBridge = new ItemBridge("fs-test-other-cross-type-bridge"){{
            linkSameType = false;
            range = 6;
        }};
        sameTypeBridge = new ItemBridge("fs-test-same-type-bridge"){{
            range = 6;
        }};
        moddedGenerator = new PowerGenerator("fs-test-generator"){{
            powerProduction = 0.25f;
            canOverdrive = true;
        }};

        moddedPowerConsumer = new PowerConsumerBlock("fs-test-power-consumer");

        liquidSource = new GenericCrafter("fs-test-liquid-source"){{
            craftTime = 60f;
            liquidCapacity = 20f;
            outputLiquids = new LiquidStack[]{new LiquidStack(Liquids.water, 1f)};
        }};
        directedLiquidSource = new GenericCrafter("fs-test-directed-liquid-source"){{
            craftTime = 60f;
            liquidCapacity = 20f;
            outputLiquids = new LiquidStack[]{new LiquidStack(Liquids.water, 1f)};
            liquidOutputDirections = new int[]{0};
        }};
        dualLiquidSource = new GenericCrafter("fs-test-dual-liquid-source"){{
            craftTime = 60f;
            liquidCapacity = 20f;
            outputLiquids = new LiquidStack[]{new LiquidStack(Liquids.water, 1f), new LiquidStack(Liquids.oil, 1f)};
            liquidOutputDirections = new int[]{0, 1};
        }};
        mixedDirectionLiquidSource = new GenericCrafter("fs-test-mixed-direction-liquid-source"){{
            craftTime = 60f;
            liquidCapacity = 20f;
            outputLiquids = new LiquidStack[]{new LiquidStack(Liquids.water, 1f), new LiquidStack(Liquids.oil, 1f)};
            liquidOutputDirections = new int[]{-1, 0};
        }};
        invalidDirectionLiquidSource = new GenericCrafter("fs-test-invalid-liquid-direction-source"){{
            hasLiquids = true;
            liquidCapacity = 20f;
            outputLiquids = new LiquidStack[]{new LiquidStack(Liquids.water, 1f)};
            liquidOutputDirections = new int[]{-2};
        }};
        invalidPositiveDirectionLiquidSource = new GenericCrafter("fs-test-invalid-positive-liquid-direction-source"){{
            hasLiquids = true;
            liquidCapacity = 20f;
            outputLiquids = new LiquidStack[]{new LiquidStack(Liquids.water, 1f)};
            liquidOutputDirections = new int[]{4};
        }};
        liquidConsumer = new GenericCrafter("fs-test-liquid-consumer"){{
            consumeLiquid(Liquids.water, 0.1f);
        }};
        oilConsumer = new GenericCrafter("fs-test-oil-consumer"){{
            consumeLiquid(Liquids.oil, 0.1f);
        }};
        filteredLiquidConsumer = new GenericCrafter("fs-test-filter-liquid-consumer"){{
            consume(new ConsumeLiquidFilter(liquid -> liquid == Liquids.water || liquid == Liquids.cryofluid, 0.1f));
        }};
        anyLiquidConsumer = new GenericCrafter("fs-test-any-liquid-consumer"){{
            consume(new ConsumeLiquidFilter(liquid -> true, 0.1f));
        }};
        throwingFilterLiquidConsumer = new GenericCrafter("fs-test-throwing-filter-liquid-consumer"){{
            consume(new ConsumeLiquidFilter(liquid -> {
                if(failFilterEnumeration && liquid == Liquids.water) filterSawWater = true;
                if(failFilterEnumeration && liquid == Liquids.cryofluid) throw new IllegalStateException("fixture filter failure");
                return liquid == Liquids.water;
            }, 0.1f));
        }};
        dynamicLiquidRequirements = new LiquidStack[]{new LiquidStack(Liquids.water, 0.1f)};
        dynamicLiquidConsumer = new GenericCrafter("fs-test-dynamic-liquid-consumer"){{
            consume(new ConsumeLiquidsDynamic(build -> dynamicLiquidRequirements));
        }};
        hydrogenConsumer = new GenericCrafter("fs-test-hydrogen-consumer"){{
            consumeLiquid(Liquids.hydrogen, 0.1f);
        }};
        solidLiquidPump = new SolidPump("fs-test-solid-liquid-pump"){{
            result = Liquids.oil;
            pumpAmount = 0.1f;
        }};
        unknownLiquidConsumer = new Block("fs-test-unknown-liquid-consumer"){{
            update = true;
            solid = true;
            hasLiquids = true;
            consumeLiquid(Liquids.water, 0.1f);
        }};
        unknownLiquidTransport = new Block("fs-test-unknown-liquid-transport"){{
            update = true;
            solid = true;
            hasLiquids = true;
            outputsLiquid = true;
            consumeLiquid(Liquids.water, 0.1f);
        }};
    }

    /** A modded subclass with a custom build-level route rule. */
    static final class RejectingConveyor extends Conveyor{
        RejectingConveyor(String name){
            super(name);
        }

        public class RejectingConveyorBuild extends ConveyorBuild{
            @Override
            public boolean acceptItem(Building source, Item item){
                return false;
            }
        }
    }

    /** A modded Conduit whose custom Build rejects an otherwise ordinary engine route. */
    static final class RejectingConduit extends Conduit{
        RejectingConduit(String name){
            super(name);
            buildType = (Prov<Building>)RejectingConduitBuild::new;
        }

        class RejectingConduitBuild extends ConduitBuild{
            @Override
            public boolean acceptLiquid(Building source, Liquid liquid){
                return false;
            }
        }
    }

    /** A consumer type FactoryScope has never heard of, with a satisfaction the test can steer. */
    static class ConsumeMystery extends Consume{
        static float satisfaction = 1f;

        @Override
        public float efficiency(Building build){
            return satisfaction;
        }
    }

    /** A non-crafter that refuses to consume, the way many specialised blocks do. */
    static class OddBlock extends Block{
        OddBlock(String name){
            super(name);
            update = true;
            solid = true;
            hasItems = true;
            consumeItem(Items.coal, 1);
            buildType = (Prov<Building>)OddBuild::new;
        }

        class OddBuild extends Building{
            @Override
            public boolean shouldConsume(){
                return false;
            }
        }
    }

    static class ItemConsumerBlock extends Block{
        ItemConsumerBlock(String name, Item item){
            super(name);
            update = true;
            solid = true;
            hasItems = true;
            consumeItem(item, 1);
            buildType = (Prov<Building>)ItemConsumerBuild::new;
        }

        class ItemConsumerBuild extends Building{
        }
    }

    static class PowerConsumerBlock extends Block{
        PowerConsumerBlock(String name){
            super(name);
            update = true;
            consumePower(2f);
            buildType = (Prov<Building>)PowerConsumerBuild::new;
        }

        class PowerConsumerBuild extends Building{
        }
    }

    static class UnknownTransportBlock extends Block{
        UnknownTransportBlock(String name){
            super(name);
            hasItems = true;
            group = BlockGroup.transportation;
            solid = true;
            update = true;
            consumeItem(Items.sand, 1);
            buildType = UnknownTransportBuild::new;
        }

        class UnknownTransportBuild extends Building{
        }
    }
}
