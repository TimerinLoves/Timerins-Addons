package dev.timerin.timerinsaddons.hud;

import org.joml.Matrix3x2fStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import dev.timerin.timerinsaddons.config.ModConfig;
import dev.timerin.timerinsaddons.skyblock.SkyBlockSackRules;
import dev.timerin.timerinsaddons.tracker.InventoryCounter;
import dev.timerin.timerinsaddons.tracker.TrackerEntry;
import dev.timerin.timerinsaddons.tracker.TrackerStore;

/**
 * Shared drawing for the item tracker (world HUD and inventory overlay).
 * Panel size uses {@link ModConfig#getHudScale()} via a matrix stack so it is independent of
 * Minecraft's GUI scale option (normalized against a medium reference).
 */
public final class TrackerOverlayRenderer {
	/** Full ARGB; plain 0xRRGGBB is treated as alpha 0 and will not draw in recent versions. */
	private static final int COLOR_RED = 0xFFFF5555;
	private static final int COLOR_YELLOW = 0xFFFFAA00;
	private static final int COLOR_GREEN = 0xFF55FF55;
	private static final int COLOR_SEP = 0xFFA0A0A0;
	private static final int COLOR_TARGET = 0xFFFFFFFF;
	private static final int COLOR_LABEL = 0xFFA0A0A0;
	private static final int COLOR_HINT_DIM = 0xFF777777;

	/** Collection HUD rows; tracker rows use the same height (single progress line + label). */
	static final float LOCAL_ROW_HEIGHT = 28f;
	static final int LOCAL_TEXT_DX = 22;
	/** Width of the tracker panel in local units (used for hit-testing). */
	static final int LOCAL_PANEL_WIDTH = 160;

	private TrackerOverlayRenderer() {
	}

	/**
	 * Progress for goals: inventory plus last known sack total when established (chat/GUI). Sack amount is not shown
	 * separately on the HUD — only this combined value vs target.
	 */
	public static long computeProgressTotal(LocalPlayer player, TrackerStore store, TrackerEntry entry) {
		long inv = InventoryCounter.countMatching(player, entry.getItemKey());
		Long sack = store.getSackStoredAmount(entry.getItemKey());
		if (sack != null) {
			return inv + sack;
		}
		return inv;
	}

	public static void render(
			GuiGraphics graphics,
			Minecraft client,
			LocalPlayer player,
			TrackerStore store,
			ModConfig cfg,
			int panelX,
			int panelY) {
		if (player == null || store.getEntries().isEmpty()) {
			return;
		}
		float es = TrackerPanelGeometry.effectiveScale(cfg, client);
		Font font = client.font;

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		try {
			pose.translate(panelX, panelY);
			pose.scale(es, es);
			float y = 0f;
			for (TrackerEntry entry : store.getEntries()) {
				long total = computeProgressTotal(player, store, entry);
				int want = entry.getTargetAmount();
				int totalColor = haveColor(total, want);

				ItemStack draw = entry.resolveDisplayIcon(player);
				ItemStack icon = draw.copy();
				icon.setCount(1);

				graphics.renderItem(icon, 0, Mth.floor(y));
				int textY = Mth.floor(y) + 4;
				String haveStr = Long.toString(total);
				String wantStr = Integer.toString(want);
				String sep = " / ";
				graphics.drawString(font, haveStr, LOCAL_TEXT_DX, textY, totalColor, true);
				int wHave = font.width(haveStr);
				graphics.drawString(font, sep, LOCAL_TEXT_DX + wHave, textY, COLOR_SEP, true);
				int wSep = font.width(sep);
				graphics.drawString(font, wantStr, LOCAL_TEXT_DX + wHave + wSep, textY, COLOR_TARGET, true);

				String label = shorten(entry.getItemKey(), 20);
				graphics.drawString(font, label, LOCAL_TEXT_DX, textY + 10, COLOR_LABEL, false);

				boolean sackUnknown = store.getSackStoredAmount(entry.getItemKey()) == null;
				boolean mightUseSack = SkyBlockSackRules.itemLikelyUsesResourceSack(entry.getItemKey());
				boolean hasSackItem = SkyBlockSackRules.playerHasAnySackItem(player);
				if (mightUseSack && hasSackItem && sackUnknown) {
					graphics.drawString(font, "\u2026", LOCAL_TEXT_DX + font.width(label) + 2, textY + 10, COLOR_HINT_DIM, false);
				}

				y += LOCAL_ROW_HEIGHT;
			}
		} finally {
			pose.popMatrix();
		}
	}

	private static int haveColor(long have, int want) {
		if (have <= 0L) {
			return COLOR_RED;
		}
		if (want <= 0) {
			return COLOR_GREEN;
		}
		if (have >= (long) want) {
			return COLOR_GREEN;
		}
		return COLOR_YELLOW;
	}

	private static String shorten(String s, int max) {
		if (s.length() <= max) {
			return s;
		}
		return s.substring(0, max - 1) + "\u2026";
	}
}
