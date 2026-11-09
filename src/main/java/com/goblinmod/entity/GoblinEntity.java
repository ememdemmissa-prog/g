package com.goblinmod.entity;

import com.goblinmod.ai.GoblinFleeGoal;
import com.goblinmod.ai.GoblinGrabLootGoal;
import com.goblinmod.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class GoblinEntity extends Monster implements GeoEntity {
    private static final int ATTACK_IMPACT_TICKS = 7;
    private static final double WALK_SPEED = 0.37;
    private static final double FLEE_SPEED = 0.46;
    private static final int MAX_STOLEN_STACKS = 27;
    private static final BlockParticleOption DUST = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState());
    private static final DustParticleOptions GOLD_SPARKLE = new DustParticleOptions(new Vector3f(1.0F, 0.84F, 0.1F), 0.8F);
    private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("animation.goblin_walk");
    private static final RawAnimation RUN_ANIM = RawAnimation.begin().thenLoop("animation.goblin_run");
    private static final RawAnimation ATTACK_ANIM = RawAnimation.begin().thenPlay("animation.goblin_attack");
    private static final EntityDataAccessor<Boolean> DATA_FLEEING = SynchedEntityData.defineId(GoblinEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_HAS_LOOT = SynchedEntityData.defineId(GoblinEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final List<ItemStack> stolenItems = new ArrayList<>();
    private int fleeTicks = 0;
    private Entity pendingTarget;
    private int attackDelay = 0;

    public GoblinEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 12.0)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.MOVEMENT_SPEED, WALK_SPEED)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(ForgeMod.ENTITY_GRAVITY.get(), 0.11);
    }

    @Override
    protected float getJumpPower() {
        return 0.5F;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new GoblinFleeGoal(this, FLEE_SPEED / WALK_SPEED));
        this.goalSelector.addGoal(3, new GoblinGrabLootGoal(this, 1.1));
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 6, true, false, null));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!(target instanceof Player)) {
            return super.doHurtTarget(target);
        }
        this.pendingTarget = target;
        this.attackDelay = ATTACK_IMPACT_TICKS;
        this.triggerAnim("attack", "attack");
        return true;
    }

    private void tickAttack() {
        if (this.attackDelay > 0 && --this.attackDelay <= 0) {
            Entity target = this.pendingTarget;
            this.pendingTarget = null;
            if (!(target instanceof Player player) || !this.isAlive() || !player.isAlive()) {
                return;
            }
            if (this.distanceToSqr(player) <= 4.0 && super.doHurtTarget(player)) {
                this.stealFrom(player);
                this.fleeTicks = 20 * (20 + this.random.nextInt(21));
            }
        }
    }

    /** Goblins have an eye for treasure: rare and enchanted items are far more likely to be picked. */
    private static int greedWeight(ItemStack stack) {
        int weight = switch (stack.getRarity()) {
            case COMMON -> 1;
            case UNCOMMON -> 4;
            case RARE -> 8;
            case EPIC -> 12;
        };
        if (stack.isEnchanted()) {
            weight += 6;
        }
        return weight;
    }

    private void stealFrom(Player player) {
        Inventory inventory = player.getInventory();
        int totalWeight = 0;
        int[] weights = new int[inventory.items.size()];
        for (int i = 0; i < weights.length; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                weights[i] = greedWeight(stack);
                totalWeight += weights[i];
            }
        }

        if (totalWeight == 0 || !this.canCarryMore()) {
            this.showActionBar(player, Component.translatable("message.goblinmod.steal_failed").withStyle(ChatFormatting.GRAY));
            return;
        }

        int roll = this.random.nextInt(totalWeight);
        int slot = 0;
        while (roll >= weights[slot]) {
            roll -= weights[slot++];
        }

        ItemStack stolen = inventory.getItem(slot).copy();
        this.stolenItems.add(stolen);
        inventory.setItem(slot, ItemStack.EMPTY);
        player.inventoryMenu.broadcastChanges();

        Component name = stolen.getHoverName().copy().withStyle(stolen.getRarity().getStyleModifier());
        this.showActionBar(player, Component.translatable("message.goblinmod.stolen", name).withStyle(stolen.getRarity().getStyleModifier()));
        // Higher pitch so the theft sounds like a cackle rather than the regular idle call.
        this.playSound(ModSounds.GOBLIN_IDLE.get(), 1.0F, 1.5F);
        this.playSound(SoundEvents.ITEM_PICKUP, 0.6F, 0.8F);
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 0.6, this.getZ(), 8, 0.25, 0.3, 0.25, 0.02);
        }
    }

    public boolean canCarryMore() {
        return this.stolenItems.size() < MAX_STOLEN_STACKS;
    }

    public void pocket(ItemEntity item) {
        ItemStack stack = item.getItem();
        this.take(item, stack.getCount());
        this.stolenItems.add(stack.copy());
        item.discard();
        this.playSound(SoundEvents.ITEM_PICKUP, 0.4F, 1.4F + this.random.nextFloat() * 0.3F);
    }

    private void showActionBar(Player player, Component text) {
        player.displayClientMessage(text, true);
    }

    public boolean isFleeing() {
        return this.entityData.get(DATA_FLEEING);
    }

    public boolean hasLoot() {
        return this.entityData.get(DATA_HAS_LOOT);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_FLEEING, false);
        this.entityData.define(DATA_HAS_LOOT, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.fleeTicks > 0) {
            this.fleeTicks--;
        }

        Level level = this.level();
        if (!level.isClientSide) {
            this.tickAttack();
            this.entityData.set(DATA_FLEEING, this.fleeTicks > 0);
            this.entityData.set(DATA_HAS_LOOT, !this.stolenItems.isEmpty());
            return;
        }

        if (this.onGround() && this.getDeltaMovement().horizontalDistanceSqr() > 0.02 && this.tickCount % 4 == 0) {
            double dx = (this.random.nextDouble() - 0.5) * 0.4;
            double dz = (this.random.nextDouble() - 0.5) * 0.4;
            level.addParticle(DUST, this.getX() + dx, this.getY() + 0.05, this.getZ() + dz, 0.0, 0.01, 0.0);
        }
        if (this.hasLoot() && this.tickCount % 6 == 0) {
            level.addParticle(GOLD_SPARKLE, this.getRandomX(0.6), this.getRandomY(), this.getRandomZ(0.6), 0.0, 0.0, 0.0);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        // Never despawn while carrying loot, otherwise stolen items would be lost forever.
        return this.stolenItems.isEmpty() && super.removeWhenFarAway(distanceToClosestPlayer);
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        for (ItemStack stack : this.stolenItems) {
            this.spawnAtLocation(stack);
        }
        this.stolenItems.clear();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("FleeTicks", this.fleeTicks);
        ListTag list = new ListTag();
        for (ItemStack stack : this.stolenItems) {
            list.add(stack.save(new CompoundTag()));
        }
        tag.put("StolenItems", list);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.fleeTicks = tag.getInt("FleeTicks");
        this.stolenItems.clear();
        ListTag list = tag.getList("StolenItems", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            this.stolenItems.add(ItemStack.of(list.getCompound(i)));
        }
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.GOBLIN_IDLE.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.GOBLIN_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.GOBLIN_DEATH.get();
    }

    private PlayState movementPredicate(AnimationState<GoblinEntity> state) {
        return state.isMoving() ? state.setAndContinue(this.isFleeing() ? RUN_ANIM : WALK_ANIM) : PlayState.STOP;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, this::movementPredicate));
        controllers.add(new AnimationController<>(this, "attack", 0, state -> PlayState.STOP).triggerableAnim("attack", ATTACK_ANIM));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
