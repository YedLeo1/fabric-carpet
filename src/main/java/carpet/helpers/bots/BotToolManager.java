package carpet.helpers.bots;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.Comparator;
import java.util.stream.IntStream;

public class BotToolManager {
    // 耐久阈值配置（剩余耐久百分比）
    private static final double DURABILITY_THRESHOLD = 0.1; // 10%

    // 智能工具切换逻辑
    public static boolean checkAndReplaceTool(ServerPlayer bot) {
        ItemStack currentTool = bot.getMainHandItem();

        // 步骤1：检查当前工具是否需要更换
        if (needsReplacement(currentTool)) {
            // 步骤2：在背包中寻找最佳替代工具
            int bestSlot = findBestToolInInventory(bot, currentTool);

            if (bestSlot != -1) {
                // 步骤3：执行工具交换
                swapTools(bot, bestSlot);
                return true;
            } else {
                // 步骤4：没有可用工具时的处理
                handleNoToolAvailable(bot);
                return false;
            }
        }
        return false;
    }

    // 判断工具是否需要更换
    private static boolean needsReplacement(ItemStack tool) {
        if (tool.isEmpty() || !isTool(tool.getItem())) return false;

        int maxDamage = tool.getMaxDamage();
        int currentDamage = tool.getDamageValue();
        double remaining = 1.0 - (double)currentDamage / maxDamage;

        return remaining < DURABILITY_THRESHOLD;
    }

    // 背包搜索算法（优先同类型更高等级工具）
    private static int findBestToolInInventory(ServerPlayer bot, ItemStack currentTool) {
        ToolClass currentToolClass = getToolClass(currentTool.getItem());
        if (currentToolClass == ToolClass.UNKNOWN) return -1;

        return IntStream.range(0, bot.getInventory().getContainerSize())
                .filter(i -> {
                    ItemStack stack = bot.getInventory().getItem(i);
                    return isValidTool(stack, currentToolClass);
                })
                .boxed()
                .max(Comparator.comparingInt(i -> {
                    ItemStack stack = bot.getInventory().getItem(i);
                    return getToolPriority(stack, currentToolClass);
                }))
                .orElse(-1);
    }

    // 工具优先级评分
    private static int getToolPriority(ItemStack stack, ToolClass currentToolClass) {
        Item item = stack.getItem();
        if (!isTool(item)) return 0;

        int score = 0;
        ToolClass toolClass = getToolClass(item);

        // 同类工具+100分
        if (toolClass == currentToolClass) score += 100;

        // 更高材质等级+50分/级
        score += (getMaterialLevel(item) - getMaterialLevel(stack.getItem())) * 50;

        // 耐久剩余比例分数（0-100）
        score += (int)((1.0 - (double)stack.getDamageValue()/stack.getMaxDamage())*100);

        return score;
    }

    // 获取工具材质等级（使用类名判断）
    private static int getMaterialLevel(Item item) {
        String className = item.getClass().getName();

        if (className.contains("Netherite")) return 4;
        if (className.contains("Diamond")) return 3;
        if (className.contains("Iron")) return 2;
        if (className.contains("Golden") || className.contains("Gold")) return 1;
        if (className.contains("Stone")) return 0;
        if (className.contains("Wooden") || className.contains("Wood")) return 0;

        return 0; // 未知材质
    }

    // 工具类型枚举
    private enum ToolClass {
        PICKAXE, AXE, SHOVEL, HOE, SWORD, SHEARS, UNKNOWN
    }

    // 判断工具类型（使用类名和 instanceof）
    private static ToolClass getToolClass(Item item) {
        String className = item.getClass().getName();

        if (className.contains("Pickaxe")) return ToolClass.PICKAXE;
        if (className.contains("Axe")) return ToolClass.AXE;
        if (className.contains("Shovel")) return ToolClass.SHOVEL;
        if (className.contains("Hoe")) return ToolClass.HOE;
        if (className.contains("Sword")) return ToolClass.SWORD;
        if (item instanceof ShearsItem) return ToolClass.SHEARS;

        return ToolClass.UNKNOWN;
    }

    // 判断是否为有效工具
    private static boolean isValidTool(ItemStack stack, ToolClass currentToolClass) {
        if (stack.isEmpty()) return false;
        return getToolClass(stack.getItem()) == currentToolClass;
    }

    // 判断物品是否为工具
    private static boolean isTool(Item item) {
        String className = item.getClass().getName();

        return className.contains("Pickaxe") ||
                className.contains("Axe") ||
                className.contains("Shovel") ||
                className.contains("Hoe") ||
                className.contains("Sword") ||
                item instanceof ShearsItem;
    }

    // 执行工具交换
    private static void swapTools(ServerPlayer bot, int newSlot) {
        ItemStack oldTool = bot.getMainHandItem().copy();
        ItemStack newTool = bot.getInventory().getItem(newSlot).copy();

        // 交换物品
        bot.getInventory().setItem(newSlot, oldTool);
        bot.setItemSlot(EquipmentSlot.MAINHAND, newTool);
    }

    // 无可用工具处理
    private static void handleNoToolAvailable(ServerPlayer bot) {
        // 发送警告粒子效果
        Level level = bot.level();
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    ParticleTypes.ANGRY_VILLAGER,
                    bot.getX(), bot.getY() + 1.5, bot.getZ(),
                    5, 0.3, 0.3, 0.3, 0.02
            );
        }

        // 停止挖掘动作
        BotExcavator.stopExcavate(bot);
    }
}