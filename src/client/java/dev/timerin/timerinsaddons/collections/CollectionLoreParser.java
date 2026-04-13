package dev.timerin.timerinsaddons.collections;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Parses collection progress from SkyBlock collection menu item tooltips (best-effort; format may change).
 */
public final class CollectionLoreParser {
	private static final Pattern SLASH_TOTALS = Pattern.compile("([0-9][0-9,]*)\\s*/\\s*([0-9][0-9,]*)");
	private static final Pattern COLLECTED = Pattern.compile("(?i)collected[^0-9]*([0-9][0-9,]*)");

	private CollectionLoreParser() {
	}

	public record Parsed(int current, int target) {
	}

	public static Optional<Parsed> parse(ItemStack stack, Player player) {
		if (stack.isEmpty() || player == null || player.level() == null) {
			return Optional.empty();
		}
		Item.TooltipContext ctx = Item.TooltipContext.of(player.level());
		List<Component> lines = stack.getTooltipLines(ctx, player, TooltipFlag.Default.NORMAL);
		return parseLines(lines);
	}

	static Optional<Parsed> parseLines(List<Component> lines) {
		StringBuilder sb = new StringBuilder();
		for (Component line : lines) {
			sb.append(line.getString()).append(' ');
		}
		String flat = sb.toString();
		Matcher m = SLASH_TOTALS.matcher(flat);
		if (m.find()) {
			int cur = parseIntSafe(m.group(1));
			int tgt = parseIntSafe(m.group(2));
			if (tgt > 0) {
				return Optional.of(new Parsed(cur, tgt));
			}
		}
		Matcher c = COLLECTED.matcher(flat.toLowerCase(Locale.ROOT));
		if (c.find()) {
			return Optional.of(new Parsed(parseIntSafe(c.group(1)), -1));
		}
		return Optional.empty();
	}

	private static int parseIntSafe(String s) {
		String digits = s.replace(",", "").trim();
		try {
			return Integer.parseInt(digits);
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
