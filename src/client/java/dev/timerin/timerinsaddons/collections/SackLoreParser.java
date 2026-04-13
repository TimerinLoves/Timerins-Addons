package dev.timerin.timerinsaddons.collections;

import java.util.List;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Reads SkyBlock sack row lore like {@code §7Stored: §e28,183§7/60.5k} (stored count before the slash).
 */
public final class SackLoreParser {
	/**
	 * SkyHanni {@code numPattern} accepts comma-separated and compact {@code k}/{@code m}/{@code b} amounts for stored
	 * counts (e.g. {@code §7Stored: §e1.2k§7/60.5k}).
	 */
	private static final Pattern STORED_LINE = Pattern.compile("(?i)stored:\\s*(?:§[0-9a-f])*([0-9][0-9.,]*[kKmMbB]?|[0-9.,]+)");

	private SackLoreParser() {
	}

	public static OptionalLong parseStoredAmount(ItemStack stack, Player player) {
		if (stack.isEmpty() || player == null) {
			return OptionalLong.empty();
		}
		Item.TooltipContext ctx = Item.TooltipContext.of(player.level());
		List<Component> lines = stack.getTooltipLines(ctx, player, TooltipFlag.Default.NORMAL);
		for (Component line : lines) {
			String plain = line.getString();
			Matcher m = STORED_LINE.matcher(plain);
			if (m.find()) {
				OptionalLong parsed = parseNumericToken(m.group(1));
				if (parsed.isPresent()) {
					return parsed;
				}
			}
		}
		return OptionalLong.empty();
	}

	/** Parses SkyBlock-style numbers: plain, comma-separated, or suffixed with k/m/b (matches SkyHanni {@code formatInt}). */
	static OptionalLong parseNumericToken(String raw) {
		if (raw == null || raw.isEmpty()) {
			return OptionalLong.empty();
		}
		String t = raw.replace(",", "").trim();
		if (t.isEmpty()) {
			return OptionalLong.empty();
		}
		int mult = 1;
		char last = t.charAt(t.length() - 1);
		if (last == 'k' || last == 'K') {
			mult = 1_000;
			t = t.substring(0, t.length() - 1).trim();
		} else if (last == 'm' || last == 'M') {
			mult = 1_000_000;
			t = t.substring(0, t.length() - 1).trim();
		} else if (last == 'b' || last == 'B') {
			mult = 1_000_000_000;
			t = t.substring(0, t.length() - 1).trim();
		}
		if (t.isEmpty()) {
			return OptionalLong.empty();
		}
		try {
			double v = Double.parseDouble(t);
			double scaled = v * (double) mult;
			if (Double.isNaN(scaled) || Double.isInfinite(scaled)) {
				return OptionalLong.empty();
			}
			return OptionalLong.of((long) Math.round(scaled));
		} catch (NumberFormatException e) {
			return OptionalLong.empty();
		}
	}
}
