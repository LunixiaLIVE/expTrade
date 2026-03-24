package net.lunix.exptrade;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;

/**
 * Kept solely for migrating legacy NBT threshold data to PlayerDataStore.
 * Do not use this for new data storage.
 */
public class PlayerAttachments {

    public static final AttachmentType<Integer> THRESHOLD = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(Exptrade.MOD_ID, "threshold"),
            builder -> builder.persistent(Codec.INT).initializer(() -> 0)
    );
}
