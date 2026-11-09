package com.goblinmod.ai;

import com.goblinmod.entity.GoblinEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;

/** Greedy goblins run to dropped items nearby and pocket them. */
public class GoblinGrabLootGoal extends Goal {
    private static final double SEARCH_RADIUS = 12.0;
    private static final double GRAB_DIST_SQR = 2.25;
    private static final int SCAN_INTERVAL = 20;
    private static final int REPATH_INTERVAL = 20;
    private static final int GIVE_UP_TICKS = 200;

    private final GoblinEntity goblin;
    private final double speed;
    private ItemEntity loot;
    private int ticks;
    private int scanCooldown;

    public GoblinGrabLootGoal(GoblinEntity goblin, double speed) {
        this.goblin = goblin;
        this.speed = speed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (--this.scanCooldown > 0 || this.goblin.isFleeing() || !this.goblin.canCarryMore()) {
            return false;
        }
        this.scanCooldown = reducedTickDelay(SCAN_INTERVAL);

        ItemEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (ItemEntity item : this.goblin.level().getEntitiesOfClass(ItemEntity.class,
                this.goblin.getBoundingBox().inflate(SEARCH_RADIUS, 4.0, SEARCH_RADIUS),
                item -> !item.hasPickUpDelay() && !item.getItem().isEmpty())) {
            double d = this.goblin.distanceToSqr(item);
            if (d < best) {
                best = d;
                nearest = item;
            }
        }
        // Raycast only the winner rather than every candidate.
        if (nearest == null || !this.goblin.hasLineOfSight(nearest)) {
            return false;
        }
        this.loot = nearest;
        return true;
    }

    @Override
    public void start() {
        this.ticks = 0;
        this.goblin.getNavigation().moveTo(this.loot, this.speed);
    }

    @Override
    public boolean canContinueToUse() {
        return this.loot != null && this.loot.isAlive() && this.ticks < GIVE_UP_TICKS
                && !this.goblin.isFleeing() && this.goblin.canCarryMore();
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
        if (this.goblin.distanceToSqr(this.loot) <= GRAB_DIST_SQR) {
            this.goblin.pocket(this.loot);
            this.loot = null;
        } else if (++this.ticks % REPATH_INTERVAL == 0 || this.goblin.getNavigation().isDone()) {
            this.goblin.getNavigation().moveTo(this.loot, this.speed);
        }
    }

    @Override
    public void stop() {
        this.loot = null;
        this.goblin.getNavigation().stop();
    }
}
