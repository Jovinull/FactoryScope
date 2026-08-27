package factoryscope.probe;

import arc.func.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.production.*;
import mindustry.world.consumers.*;

/**
 * Blocks that stand in for the shapes other mods actually ship, registered alongside vanilla content
 * in the headless game. They exist only in tests and are never part of the mod.
 */
final class ModdedBlocks{
    static GenericCrafter conventional;
    static GenericCrafter boosted;
    static GenericCrafter exotic;
    static Block oddBuilding;

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

        boosted = new GenericCrafter("fs-test-boosted"){{
            craftTime = 30f;
            outputItem = new ItemStack(Items.silicon, 1);
            consumeItem(Items.sand, 2);
            //the shape every mod uses for a speed booster: optional, and never required to operate
            consume(new ConsumeItemFlammable()).boost();
        }};

        exotic = new GenericCrafter("fs-test-exotic"){{
            craftTime = 45f;
            outputItem = new ItemStack(Items.titanium, 1);
            consumeItem(Items.copper, 1);
            consume(new ConsumeMystery());
        }};

        oddBuilding = new OddBlock("fs-test-odd");
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
}
