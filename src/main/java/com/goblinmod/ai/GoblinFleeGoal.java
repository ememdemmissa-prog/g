package com.goblinmod.ai;

import com.goblinmod.entity.GoblinEntity;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public class GoblinFleeGoal extends Goal {
    private static final double[] TIERS = {40.0, 8.0};
    private static final int[] SECTORS = {0, 1, -1, 2, -2};
    private static final double SEARCH_RANGE = 24.0;
    private static final int STUCK_TICKS = 10;
    private static final int FAILED_LIFETIME = 100;
    private static final int SEARCH_COOLDOWN = 10;

    private final GoblinEntity goblin;
    private final double speed;
    private LivingEntity threat;
    private Path chosen;
    private int lastNodeIndex = -1;
    private int stillTicks;
    private BlockPos askedPos;
    private BlockPos failedPos;
    private int failedTick;
    private int nextSearchTick;

    public GoblinFleeGoal(GoblinEntity goblin, double speed) {
        this.goblin = goblin;
        this.speed = speed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!this.goblin.isFleeing() || this.goblin.tickCount < this.nextSearchTick) {
            return false;
        }
        LivingEntity target = this.goblin.getTarget();
        if (target == null) {
            target = this.goblin.level().getNearestPlayer(this.goblin, SEARCH_RANGE);
        }
        if (target == null) {
            return false;
        }
        this.threat = target;
        this.chosen = this.findEscapePath();
        if (this.chosen == null) {
            // Each search runs up to 10 pathfinds; don't repeat it every tick when cornered.
            this.nextSearchTick = this.goblin.tickCount + SEARCH_COOLDOWN;
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        this.goblin.getNavigation().moveTo(this.chosen, this.speed);
    }

    @Override
    public boolean canContinueToUse() {
        return this.goblin.isFleeing() && this.threat != null && this.threat.isAlive() && !this.goblin.getNavigation().isDone();
    }

    @Override
    public void tick() {
        Path current = this.goblin.getNavigation().getPath();
        int index = current == null ? -1 : current.getNextNodeIndex();
        if (index == this.lastNodeIndex) {
            if (++this.stillTicks > STUCK_TICKS) {
                this.stillTicks = 0;
                this.goblin.getJumpControl().jump();
                this.failedPos = this.askedPos;
                this.failedTick = this.goblin.tickCount;
                this.goblin.getNavigation().stop();
            }
        } else {
            this.stillTicks = 0;
            this.lastNodeIndex = index;
        }
    }

    private Path findEscapePath() {
        Vec3 origin = this.goblin.position();
        Vec3 away = origin.subtract(this.threat.position());
        away = new Vec3(away.x, 0.0, away.z);
        if (away.lengthSqr() < 1.0E-4) {
            away = new Vec3(1.0, 0.0, 0.0);
        }
        away = away.normalize();

        double myDistSqr = this.threat.distanceToSqr(this.goblin);
        int side = this.goblin.getRandom().nextBoolean() ? 1 : -1;
        Path best = null;
        BlockPos bestPos = null;
        double bestGain = 0.0;

        for (int sector : SECTORS) {
            double angle = Math.toRadians(sector * side * 45.0);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            Vec3 dir = new Vec3(away.x * cos - away.z * sin, 0.0, away.x * sin + away.z * cos);

            for (double dist : TIERS) {
                BlockPos pos = this.targetPoint(origin, dir, dist, myDistSqr);
                if (pos == null) {
                    continue;
                }
                Path path = this.goblin.getNavigation().createPath(pos, 1);
                if (path != null) {
                    Node end = path.getEndNode();
                    if (end != null) {
                        double gain = this.threat.distanceToSqr(end.x + 0.5, end.y, end.z + 0.5) - myDistSqr;
                        if (gain > bestGain) {
                            bestGain = gain;
                            best = path;
                            bestPos = pos;
                        }
                    }
                    break;
                }
            }
        }

        this.askedPos = bestPos;
        return best;
    }

    private BlockPos targetPoint(Vec3 origin, Vec3 dir, double dist, double myDistSqr) {
        BlockPos pos = this.goblin.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(origin.add(dir.scale(dist))));
        if (!this.goblin.level().hasChunkAt(pos)) {
            return null;
        }
        if (this.threat.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) <= myDistSqr) {
            return null;
        }
        return pos.equals(this.failedPos) && this.goblin.tickCount - this.failedTick < FAILED_LIFETIME ? null : pos;
    }

    @Override
    public void stop() {
        this.threat = null;
        this.chosen = null;
        this.askedPos = null;
        this.failedPos = null;
    }
}
