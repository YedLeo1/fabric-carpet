package carpet.helpers.bots;

import carpet.CarpetSettings;
import carpet.fakes.ServerPlayerInterface;
import carpet.helpers.EntityPlayerActionPack;
import carpet.patches.EntityPlayerMPFake;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.BaseFireBlock;

import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

import java.util.ArrayList;
import java.util.List;


public class BotExcavator {
    // 新增：缓存脚底位置避免重复计算
    private static BlockPos lastFeetPos = BlockPos.ZERO;
    private static final int EXCAVATE_RADIUS = 3; // 3=7x7, 4=9x9
    private static BlockPos lockedCenter; // 新增：锁定扫描中心

    public static boolean performExcavate(ServerPlayer bot) {
        if (lockedCenter == null) {
            lockedCenter = bot.blockPosition();
        }
//        if (!BotToolManager.checkAndReplaceTool(bot)) {
//            return false;
//        }
        BlockPos targetPos = findMineableBlock(bot, EXCAVATE_RADIUS);
        ServerPlayerInterface botInterface = (ServerPlayerInterface) bot;
        EntityPlayerActionPack actionPack = botInterface.getActionPack();

        // 获取当前脚底位置
        BlockPos currentFeetPos = bot.blockPosition().below();

        // 获取有序方块列表（周围在前，脚底最后）
        List<BlockPos> targetQueue = getPrioritizedBlocks(bot, 3, currentFeetPos);

        for (BlockPos pos : targetQueue) {
            if (canReach(bot, pos)){
                actionPack
                        .lookAt(pos.getCenter())
                        .start(EntityPlayerActionPack.ActionType.ATTACK, EntityPlayerActionPack.Action.continuous());
                return true;
            }
        }
        return false;
    }

    private static List<BlockPos> getPrioritizedBlocks(ServerPlayer bot, int radius, BlockPos feetPos) {
        Level world = bot.serverLevel();
        BlockPos center = bot.blockPosition();
        List<BlockPos> blocks = new ArrayList<>();
        List<BlockPos> feetBlocks = new ArrayList<>();

        // 分层扫描逻辑（保持原有Y轴顺序）
        for (int yOffset = 0; yOffset <= radius; yOffset++) {
            // 向上扫描
            scanLayer(center.above(yOffset), radius, world, bot, feetPos, blocks, feetBlocks);
            // 向下扫描（跳过0层）
            if (yOffset != 0) {
                scanLayer(center.below(yOffset), radius, world, bot, feetPos, blocks, feetBlocks);
            }
        }

        // 合并列表：普通方块在前，脚底方块最后
        blocks.addAll(feetBlocks);
        return blocks;
    }
    private static void scanLayer(BlockPos layerCenter, int radius, Level world,
                                  ServerPlayer bot, BlockPos feetPos,
                                  List<BlockPos> normalBlocks, List<BlockPos> feetBlocks) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos pos = layerCenter.offset(x, 0, z);
                if (isTrulyMineable(world, pos, bot)) {
                    // 分离脚底方块
                    if (pos.equals(feetPos)) {
                        if (!feetBlocks.contains(pos)) feetBlocks.add(pos);
                    } else {
                        if (!normalBlocks.contains(pos)) normalBlocks.add(pos);
                    }
                }
            }
        }
    }

    private static BlockPos findMineableBlock(ServerPlayer bot, int radius) {
        ServerLevel world = bot.serverLevel();
        BlockPos center = bot.blockPosition();
        final int maxVertical = 3; // 最大垂直扫描层数

        // 新扫描顺序：从上到下（Y+max -> Y-max）
        for (int yOffset = maxVertical; yOffset >= -maxVertical; yOffset--) {
            // 跳过超出实际可扫描的层级
//            if (Math.abs(yOffset) > CarpetSettings.excavateMaxY) continue;

            BlockPos layerCenter = center.offset(0, yOffset, 0);
            BlockPos foundPos = scanLayerWithPriority(layerCenter, radius, world, bot);
            if (foundPos != null) {
                return foundPos;
            }
        }
        return null;
    }

    private static BlockPos scanLayerWithPriority(BlockPos layerCenter, int radius, ServerLevel world, ServerPlayer bot) {
        List<BlockPos> candidates = new ArrayList<>();

        // 扫描正方形区域
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos pos = layerCenter.offset(x, 0, z);
                if (isTrulyMineable(world, pos, bot)) {
                    candidates.add(pos);
                }
            }
        }

        // 按垂直高度+水平距离综合排序
        candidates.sort((a, b) -> {
            // 优先更高Y层
            int yCompare = Integer.compare(b.getY(), a.getY());
            if (yCompare != 0) return yCompare;

            // 同层时按距离中心近的优先
            return Double.compare(
                    a.distSqr(layerCenter),
                    b.distSqr(layerCenter)
            );
        });

        return candidates.isEmpty() ? null : candidates.get(0);
    }


    // 在指定Y层扫描正方形区域
    private static BlockPos scanSquareLayer(BlockPos layerCenter, int radius, ServerLevel world, ServerPlayer bot) {

//        drawScanningArea(world, layerCenter, radius);

        for (int xOffset = -radius; xOffset <= radius; xOffset++) {
            for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                double distance = Math.sqrt(xOffset*xOffset + zOffset*zOffset);
                if (distance > radius) continue;
                BlockPos pos = layerCenter.offset(xOffset, 0, zOffset);
                if (isTrulyMineable(world, pos, bot)) {
                    return pos;
                }
            }
        }
        return null;
    }
    private static void drawScanningArea(ServerLevel world, BlockPos center, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (Math.abs(x) == radius || Math.abs(z) == radius) {
                    BlockPos edgePos = center.offset(x, 0, z);
                    world.sendParticles(
                            ParticleTypes.END_ROD,  // 使用ParticleTypes替代Particles
                            edgePos.getX() + 0.5,
                            edgePos.getY() + 1.5,
                            edgePos.getZ() + 0.5,
                            1,  // 数量
                            0,  // deltaX
                            0,  // deltaY
                            0,  // deltaZ
                            0.02  // 速度
                    );
                }
            }
        }
    }


    // 详细可挖掘判断逻辑
    private static boolean isTrulyMineable(Level world, BlockPos pos, ServerPlayer bot) {
        BlockState state = world.getBlockState(pos);

        // 跳过空气、流体、火焰、基岩
        if (state.isAir() ||
                state.getFluidState().isSource() ||
                state.is(Blocks.FIRE) ||
                state.is(Blocks.BEDROCK)) {
            return false;
        }

        // 跳过需要特殊工具的方块
        if (!bot.gameMode.getGameModeForPlayer().isCreative() &&
                state.requiresCorrectToolForDrops() &&
                !bot.getMainHandItem().isCorrectToolForDrops(state)) {
            return false;
        }

        // 跳过带锁容器（如果启用规则）
        if (CarpetSettings.botSkipProtectedBlocks &&
                world.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity) {
            return false;
        }

        return state.getDestroySpeed(world, pos) >= 0;
    }


    // 操作范围检查（支持Carpet扩展）
    private static boolean canReach(ServerPlayer bot, BlockPos pos) {
        Vec3 eyePos = bot.getEyePosition();
        Vec3 targetCenter = Vec3.atCenterOf(pos);
        double reachDistance = 4.5; // 原版触及距离
        return eyePos.distanceToSqr(targetCenter) <= reachDistance * reachDistance;
    }


    public static void stopExcavate(ServerPlayer bot) {
        lockedCenter = null; // 重置锁定
        ServerPlayerInterface botInterface = (ServerPlayerInterface) bot;
        botInterface.getActionPack().stopAll();
    }
}