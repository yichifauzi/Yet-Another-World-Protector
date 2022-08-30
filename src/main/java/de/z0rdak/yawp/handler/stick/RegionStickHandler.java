package de.z0rdak.yawp.handler.stick;

import de.z0rdak.yawp.core.stick.RegionStick;
import de.z0rdak.yawp.util.StickType;
import de.z0rdak.yawp.util.StickUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import static de.z0rdak.yawp.util.StickUtil.STICK;

public class RegionStickHandler {

    public static void onCycleRegionStick(ItemStack regionStickItem) {
        CompoundTag nbt = regionStickItem.getTag();
        RegionStick regionStick = new RegionStick(nbt.getCompound(STICK));
        // cycle mode
        regionStick.cycleMode();
        // update stick name
        regionStickItem.getTag().put(STICK, regionStick.serializeNBT());
        StickUtil.setStickName(regionStickItem, StickType.REGION_STICK);
    }
}
