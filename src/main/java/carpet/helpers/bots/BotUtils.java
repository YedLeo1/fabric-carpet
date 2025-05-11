package carpet.helpers.bots;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class BotUtils {
    // 生成三维立方体范围迭代器
    public static Iterable<BlockPos> get3DCube(BlockPos center, int radius) {
        return BlockPos.betweenClosed(
                center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius)
        );
    }

    // 优化扫描顺序：优先Y轴分层
    public static Iterable<BlockPos> layered3DScan(BlockPos center, int radius) {
        List<BlockPos> positions = new ArrayList<>();
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    positions.add(center.offset(x, y, z));
                }
            }
        }
        return positions;
    }
}