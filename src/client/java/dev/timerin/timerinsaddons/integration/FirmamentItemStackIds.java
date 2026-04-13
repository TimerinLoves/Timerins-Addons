package dev.timerin.timerinsaddons.integration;

import java.lang.reflect.Method;
import java.util.Optional;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

/**
 * Optional integration with Firmament's Kotlin helpers for SkyBlock item ids (same NEU-style ids).
 */
public final class FirmamentItemStackIds {
	private static final String[] SKYBLOCK_ID_KT_CLASSES = {
			"moe.nea.firmament.util.SkyblockIdKt",
			"moe.nea.firmament.util.SkyBlockIdKt",
	};
	private static Method rawSkyBlockIdGetter;

	private FirmamentItemStackIds() {
	}

	public static Optional<String> tryRawSkyBlockId(ItemStack stack) {
		if (!FabricLoader.getInstance().isModLoaded("firmament") || stack.isEmpty()) {
			return Optional.empty();
		}
		Method m = rawSkyBlockIdGetter;
		if (m == null) {
			for (String className : SKYBLOCK_ID_KT_CLASSES) {
				try {
					Class<?> kt = Class.forName(className);
					m = kt.getMethod("getRawSkyBlockId", ItemStack.class);
					rawSkyBlockIdGetter = m;
					break;
				} catch (ReflectiveOperationException ignored) {
				}
			}
			if (m == null) {
				return Optional.empty();
			}
		}
		try {
			Object out = m.invoke(null, stack);
			if (out instanceof String s && !s.isEmpty()) {
				return Optional.of(s);
			}
		} catch (ReflectiveOperationException ignored) {
		}
		return Optional.empty();
	}
}
