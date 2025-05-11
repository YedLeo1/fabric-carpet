package carpet.helpers.bots;

import carpet.fakes.ServerPlayerInterface;
import carpet.helpers.EntityPlayerActionPack;
import carpet.patches.EntityPlayerMPFake;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;

public class GhostPlacer {
    private static BlockPos calculatePlacementPos(ServerPlayer bot) {
        // 获取假人视线方向向量
        Vec3 lookVec = bot.getLookAngle();
        // 从眼睛位置延伸5格距离
        Vec3 eyePos = bot.getEyePosition();
        Vec3 targetPos = eyePos.add(lookVec.scale(5));

        return BlockPos.containing(targetPos);
    }

    // 支持三维范围检测（根据Carpet规则）
    private static BlockPos calculatePlacementPos(ServerPlayer bot, int radius) {
        BlockPos center = bot.blockPosition();
        Direction facing = bot.getDirection();
        return center.relative(facing, radius).above(); // 前方radius格，上方1格
    }
    private static ItemStack findPlaceableBlock(ServerPlayer bot) {
        // 检查是否为假人实体
        if (!(bot instanceof EntityPlayerMPFake)) {
            return ItemStack.EMPTY;
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = bot.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem blockItem) {
                // 过滤不可放置的方块（如火把需要支撑）
                if (canPlaceBlock(blockItem.getBlock(), bot.level(), calculatePlacementPos(bot))) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    // 判断方块能否在目标位置放置
    private static boolean canPlaceBlock(Block block, Level level, BlockPos pos) {
        BlockState state = block.defaultBlockState();
        return state.canSurvive(level, pos) &&
                level.getBlockState(pos).canBeReplaced();
    }
    public static boolean performGhostPlacement(ServerPlayer bot) {
        ServerPlayerInterface botInterface = (ServerPlayerInterface) bot;
        EntityPlayerActionPack actionPack = botInterface.getActionPack();

        BlockPos targetPos = calculatePlacementPos(bot);
        ItemStack stack = findPlaceableBlock(bot);

        if (stack.isEmpty()) return false;

        // 直接操作服务端方块状态
        ServerLevel world = (ServerLevel) bot.level();
        world.setBlock(targetPos, ((BlockItem) stack.getItem()).getBlock().defaultBlockState(), 3);
        stack.shrink(1);
        return true;
    }

    public static void setBotSelectedSlot(ServerPlayer bot, int slot) {
        try {
            // 获取 Inventory 类中的 selected 字段
            Field selectedField = Inventory.class.getDeclaredField("selected");
            selectedField.setAccessible(true); // 允许访问私有字段
            selectedField.setInt(bot.getInventory(), slot);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }
    private static ItemStack findPlaceableBlock(EntityPlayerMPFake bot) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = bot.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem) {
                setBotSelectedSlot(bot,i);
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}