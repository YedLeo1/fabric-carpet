package carpet.helpers.bots;

import carpet.CarpetSettings;
import carpet.fakes.ServerPlayerInterface;
import carpet.helpers.EntityPlayerActionPack;
import carpet.patches.EntityPlayerMPFake;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;
import java.util.List;

public class BotFluidHandler {
    private static final List<Item> FLUID_BLOCKS = List.of(Items.SAND, Items.GRAVEL);

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

    public static boolean handleFluid(ServerPlayer player) {
        if (!(player instanceof EntityPlayerMPFake)) {
            return false; // 非假人直接返回失败
        }
        EntityPlayerMPFake bot = (EntityPlayerMPFake) player;

        // 查找流体位置
        BlockPos fluidPos = findFluidBlock(bot, 3);
        if (fluidPos == null) return false;

        // 查找沙子类物品槽位
        int slot = findSandSlot(bot);
        if (slot == -1) return false;

        // 设置选中槽位并执行放置
        setBotSelectedSlot(bot,slot);
        ServerPlayerInterface botInterface = (ServerPlayerInterface) bot;
        botInterface.getActionPack()
                .lookAt(fluidPos.getCenter())
                .start(EntityPlayerActionPack.ActionType.USE, EntityPlayerActionPack.Action.once());

        return true; // 操作成功
    }

    private static BlockPos findFluidBlock(EntityPlayerMPFake bot, int radius) {
        for (BlockPos pos : BotUtils.get3DCube(bot.blockPosition(), radius)) {
            BlockState state = bot.level().getBlockState(pos);
            if (state.getFluidState().isSource() &&
                    bot.level().getBlockState(pos.above()).isAir()) {
                return pos;
            }
        }
        return null;
    }

    private static int findSandSlot(EntityPlayerMPFake bot) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = bot.getInventory().getItem(i);
            if (FLUID_BLOCKS.contains(stack.getItem())) return i;
        }
        return -1;
    }
}