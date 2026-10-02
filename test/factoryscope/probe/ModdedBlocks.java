package factoryscope.probe;

import arc.func.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.distribution.ItemBridge;
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
    static ItemBridge crossTypeBridge;
    static ItemBridge otherCrossTypeBridge;
    static ItemBridge sameTypeBridge;
    static PowerGenerator moddedGenerator;

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
        }};
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
