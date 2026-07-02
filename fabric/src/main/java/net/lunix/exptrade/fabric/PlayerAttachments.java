package net.lunix.exptrade.fabric;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.lunix.exptrade.ExpTradeCommon;
import net.minecraft.resources.ResourceLocation;

/**
 * Kept solely for migrating legacy NBT threshold data to PlayerDataStore.
 * Do not use for new data storage.
 */
public class PlayerAttachments {

    public static final AttachmentType<Integer> THRESHOLD = AttachmentRegistry.create(
            ResourceLocation.fromNamespaceAndPath(ExpTradeCommon.MOD_ID, "threshold"),
            builder -> builder.persistent(Codec.INT).initializer(() -> 0)
    );
}
