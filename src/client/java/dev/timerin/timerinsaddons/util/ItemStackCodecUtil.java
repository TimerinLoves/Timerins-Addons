package dev.timerin.timerinsaddons.util;

import java.util.Optional;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Persists a display {@link ItemStack} for tracker rows (JSON via {@link ItemStack#CODEC}).
 */
public final class ItemStackCodecUtil {
	private ItemStackCodecUtil() {
	}

	private static RegistryAccess registryAccess(Minecraft mc) {
		Level level = mc.level;
		if (level != null) {
			return level.registryAccess();
		}
		Player player = mc.player;
		if (player != null) {
			return player.registryAccess();
		}
		return RegistryAccess.EMPTY;
	}

	public static Optional<String> encode(ItemStack stack) {
		if (stack.isEmpty()) {
			return Optional.empty();
		}
		Minecraft mc = Minecraft.getInstance();
		RegistryAccess access = registryAccess(mc);
		var ops = RegistryOps.create(JsonOps.INSTANCE, access);
		var result = ItemStack.CODEC.encode(stack, ops, ops.empty());
		return result.result().map(el -> el.toString());
	}

	public static ItemStack decode(String json) {
		if (json == null || json.isBlank()) {
			return ItemStack.EMPTY;
		}
		Minecraft mc = Minecraft.getInstance();
		RegistryAccess access = registryAccess(mc);
		var ops = RegistryOps.create(JsonOps.INSTANCE, access);
		try {
			var el = JsonParser.parseString(json);
			var parsed = ItemStack.CODEC.parse(ops, el);
			return parsed.resultOrPartial(s -> {
			}).orElse(ItemStack.EMPTY);
		} catch (Exception e) {
			return ItemStack.EMPTY;
		}
	}
}
