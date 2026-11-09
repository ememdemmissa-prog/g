package com.goblinmod.entity;

import com.goblinmod.ai.GoblinFleeGoal;
import com.goblinmod.ai.GoblinGrabLootGoal;
import com.goblinmod.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
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
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.Nullable;
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
    private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("animation.goblin_walk");
    private static final RawAnimation RUN_ANIM = RawAnimation.begin().thenLoop("animation.goblin_run");
    private static final RawAnimation ATTACK_ANIM = RawAnimation.begin().thenPlay("animation.goblin_attack");
    private static final EntityDataAccessor<Boolean> DATA_FLEEING = SynchedEntityData.defineId(GoblinEntity.class, EntityDataSerializers.BOOLEAN);

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
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
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

    private void stealFrom(Player player) {
        Inventory inventory = player.getInventory();
        // Reservoir sampling: uniform random non-empty slot in one pass, no allocation.
        int slot = -1;
        int seen = 0;
        for (int i = 0; i < inventory.items.size(); i++) {
            if (!inventory.items.get(i).isEmpty() && this.random.nextInt(++seen) == 0) {
                slot = i;
            }
        }

        if (slot < 0 || !this.canCarryMore()) {
            this.showActionBar(player, Component.translatable("message.goblinmod.steal_failed").withStyle(ChatFormatting.GRAY));
            return;
        }

        ItemStack stolen = inventory.items.get(slot);
        inventory.setItem(slot, ItemStack.EMPTY);
        this.stolenItems.add(stolen);
        player.inventoryMenu.broadcastChanges();

        UnaryOperator<Style> style = stolen.getRarity().getStyleModifier();
        Component name = stolen.getHoverName().copy().withStyle(style);
        this.showActionBar(player, Component.translatable("message.goblinmod.stolen", name).withStyle(style));
        this.playSound(ModSounds.GOBLIN_IDLE.get(), 1.0F, 1.0F);
    }

    public boolean canCarryMore() {
        return this.stolenItems.size() < MAX_STOLEN_STACKS;
    }

    public void pocket(ItemEntity item) {
        ItemStack stack = item.getItem();
        this.take(item, stack.getCount());
        this.stolenItems.add(stack.copy());
        item.discard();
    }

    private void showActionBar(Player player, Component text) {
        player.displayClientMessage(text, true);
    }

    public boolean isFleeing() {
        return this.entityData.get(DATA_FLEEING);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_FLEEING, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            if (this.fleeTicks > 0) {
                this.fleeTicks--;
            }
            this.tickAttack();
            this.entityData.set(DATA_FLEEING, this.fleeTicks > 0);
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
