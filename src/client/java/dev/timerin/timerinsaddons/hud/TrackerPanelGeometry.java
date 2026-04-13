package dev.timerin.timerinsaddons.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import dev.timerin.timerinsaddons.collections.CollectionStore;
import dev.timerin.timerinsaddons.config.ModConfig;
import dev.timerin.timerinsaddons.tracker.TrackerStore;

/**
 * Shared layout math for hit-testing and rendering the tracker panel.
 * Visual scale uses {@link ModConfig#getHudScale()} and is normalized against Minecraft's
 * GUI scale so the panel size does not follow Options → GUI Scale — only the mod slider / scroll.
 */
public final class TrackerPanelGeometry {
	/** Minecraft "medium" GUI scale index; used to keep mod-sized HUD stable when MC GUI scale changes. */
	private static final float REFERENCE_MC_GUI_SCALE = 2.0f;

	private TrackerPanelGeometry() {
	}

	/**
	 * Combined scale in screen (GUI-scaled) space: mod hud scale × compensation for MC GUI scale.
	 */
	public static float effectiveScale(ModConfig cfg, Minecraft client) {
		int g = client.getWindow().getGuiScale();
		if (g < 1) {
			g = 1;
		}
		return cfg.getHudScale() * (REFERENCE_MC_GUI_SCALE / (float) g);
	}

	public static int lineHeight(ModConfig cfg, Minecraft client) {
		return Mth.ceil(TrackerOverlayRenderer.LOCAL_ROW_HEIGHT * effectiveScale(cfg, client));
	}

	public static int panelWidth(ModConfig cfg, Minecraft client) {
		return Mth.ceil(TrackerOverlayRenderer.LOCAL_PANEL_WIDTH * effectiveScale(cfg, client));
	}

	public static int panelHeight(TrackerStore store, ModConfig cfg, Minecraft client) {
		int n = Math.max(1, store.getEntries().size());
		return Mth.ceil(TrackerOverlayRenderer.LOCAL_ROW_HEIGHT * (float) n * effectiveScale(cfg, client));
	}

	public static int panelHeightForRowCount(int rowCount, ModConfig cfg, Minecraft client) {
		int n = Math.max(1, rowCount);
		return Mth.ceil(TrackerOverlayRenderer.LOCAL_ROW_HEIGHT * (float) n * effectiveScale(cfg, client));
	}

	public static boolean contains(
			double mouseX,
			double mouseY,
			int panelX,
			int panelY,
			TrackerStore store,
			ModConfig cfg,
			Minecraft client) {
		int w = panelWidth(cfg, client);
		int h = panelHeight(store, cfg, client);
		return mouseX >= panelX && mouseY >= panelY && mouseX < panelX + w && mouseY < panelY + h;
	}

	public static boolean containsPanel(
			double mouseX,
			double mouseY,
			int panelX,
			int panelY,
			int rowCount,
			ModConfig cfg,
			Minecraft client) {
		int w = panelWidth(cfg, client);
		int h = panelHeightForRowCount(rowCount, cfg, client);
		return mouseX >= panelX && mouseY >= panelY && mouseX < panelX + w && mouseY < panelY + h;
	}

	/** Rows for hit-testing the collection panel. */
	public static int collectionPanelRowCount(CollectionStore store) {
		return store.getGoals().size();
	}
}
