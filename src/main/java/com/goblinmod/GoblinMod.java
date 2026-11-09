package com.goblinmod;

import com.goblinmod.entity.GoblinEntity;
import com.goblinmod.registry.ModCreativeTabs;
import com.goblinmod.registry.ModEntities;
import com.goblinmod.registry.ModItems;
import com.goblinmod.registry.ModSounds;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(GoblinMod.MODID)
public class GoblinMod {
    public static final String MODID = "goblinmod";

    public GoblinMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modBus);
        modBus.addListener(this::onAttributeCreation);
        modBus.addListener(this::onSpawnPlacements);
    }

    private void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.GOBLIN.get(), GoblinEntity.createAttributes().build());
    }

    private void onSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(ModEntities.GOBLIN.get(), SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
    }
}
