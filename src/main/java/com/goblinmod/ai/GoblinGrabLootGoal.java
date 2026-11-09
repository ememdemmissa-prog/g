package com.goblinmod.ai;

import com.goblinmod.entity.GoblinEntity;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;

/** Greedy goblins run to dropped items nearby and pocket them. */
public class GoblinGrabLootGoal extends Goal {
    private static final double SEARCH_RADIUS = 12.0;
    private static final double GRAB_DIST_SQR = 2.25;
    private static final int GIVE_UP_TICKS = 200;

    private final GoblinEntity goblin;
    private final double speed;
    private ItemEntity loot;
    private int ticks;

    public GoblinGrabLootGoal(GoblinEntity goblin, double speed) {
        this.goblin = goblin;
        this.speed = speed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.goblin.isFleeing() || !this.goblin.canCarryMore() || this.goblin.getRandom().nextInt(reducedTickDelay(10)) != 0) {
            return false;
        }
        List<ItemEntity> items = this.goblin.level().getEntitiesOfClass(ItemEntity.class,
                this.goblin.getBoundingBox().inflate(SEARCH_RADIUS, 4.0, SEARCH_RADIUS),
                item -> item.isAlive() && !item.getItem().isEmpty() && !item.hasPickUpDelay() && this.goblin.hasLineOfSight(item));
        ItemEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (ItemEntity item : items) {
            double d = this.goblin.distanceToSqr(item);
            if (d < best) {
                best = d;
                nearest = item;
            }
        }
        this.loot = nearest;
        return nearest != null;
    }

    @Override
    public void start() {
        this.ticks = 0;
        this.goblin.getNavigation().moveTo(this.loot, this.speed);
    }

    @Override
    public boolean canContinueToUse() {
        return this.loot != null && this.loot.isAlive() && !this.goblin.isFleeing() && this.goblin.canCarryMore() && this.ticks < GIVE_UP_TICKS;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        // GoalSelector may tick again in the same server tick after the item was pocketed.
        if (this.loot == null || !this.loot.isAlive()) {
            return;
        }
        this.ticks++;
        this.goblin.getLookControl().setLookAt(this.loot, 30.0F, 30.0F);
        if (this.goblin.distanceToSqr(this.loot) <= GRAB_DIST_SQR) {
            this.goblin.pocket(this.loot);
            this.loot = null;
        } else if (this.ticks % 10 == 0) {
            this.goblin.getNavigation().moveTo(this.loot, this.speed);
        }
    }

    @Override
    public void stop() {
        this.loot = null;
        this.goblin.getNavigation().stop();
    }
}
