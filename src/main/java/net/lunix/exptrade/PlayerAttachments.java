package net.lunix.exptrade;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.util.Identifier;

public class PlayerAttachments {

    public static AttachmentType<Integer> THRESHOLD;

    public static void register() {
        THRESHOLD = AttachmentRegistry.<Integer>builder()
                .persistent(Codec.INT)
                .initializer(() -> 0)
                .buildAndRegister(Identifier.of(Exptrade.MOD_ID, "threshold"));
    }
}
