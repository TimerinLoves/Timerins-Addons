package dev.timerin.timerinsaddons.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.api.FabricLoader;

public final class FirmamentIntegration {
	private static final Logger LOGGER = LoggerFactory.getLogger("timerins_addons");

	private FirmamentIntegration() {
	}

	public static void logIfPresent() {
		if (FabricLoader.getInstance().isModLoaded("firmament")) {
			LOGGER.info("Firmament is loaded; item ids prefer Firmament's raw SkyBlock id when available.");
		}
	}
}
