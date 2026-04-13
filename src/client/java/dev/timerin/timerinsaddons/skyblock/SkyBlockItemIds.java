package dev.timerin.timerinsaddons.skyblock;

import java.util.Locale;
import java.util.Optional;

import dev.timerin.timerinsaddons.integration.FirmamentItemStackIds;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;

/**
 * Resolves a stable item key for SkyBlock items (ExtraAttributes.id) with a vanilla fallback.
 */
public final class SkyBlockItemIds {
	private static final String EXTRA_ATTRIBUTES = "ExtraAttributes";
	private static final String ID = "id";

	private SkyBlockItemIds() {
	}

	public static Optional<String> resolveKey(ItemStack stack) {
		if (stack.isEmpty()) {
			return Optional.empty();
		}
		Optional<String> firm = FirmamentItemStackIds.tryRawSkyBlockId(stack);
		if (firm.isPresent()) {
			return firm;
		}
		Optional<String> skyblockId = getSkyBlockId(stack);
		if (skyblockId.isPresent()) {
			return skyblockId;
		}
		return Optional.of(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
	}

	public static Optional<String> getSkyBlockId(ItemStack stack) {
		if (stack.isEmpty()) {
			return Optional.empty();
		}
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			return Optional.empty();
		}
		CompoundTag root = customData.copyTag();
		Optional<CompoundTag> extraOpt = root.getCompound(EXTRA_ATTRIBUTES);
		if (extraOpt.isEmpty()) {
			return Optional.empty();
		}
		CompoundTag extra = extraOpt.get();
		String id = extra.getStringOr(ID, "");
		if (id.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(id);
	}

	/**
	 * Fallback icon when we have no representative stack: vanilla item if {@code itemKey} is a real registry id, else paper.
	 */
	public static ItemStack createDisplayStack(String itemKey, ItemStack representative) {
		if (!representative.isEmpty()) {
			return representative;
		}
		Optional<ItemStack> neu = NeuInternalItemIcons.tryStackForKey(itemKey);
		if (neu.isPresent()) {
			return neu.get();
		}
		Optional<ItemStack> fromId = tryVanillaStackForId(itemKey);
		return fromId.orElseGet(() -> new ItemStack(Items.PAPER));
	}

	/**
	 * True if {@code itemKey} resolves to a known vanilla item id (registry), not SkyBlock-internal strings.
	 */
	public static boolean isKnownVanillaItemId(String itemKey) {
		return tryVanillaStackForId(itemKey).isPresent();
	}

	private static Optional<ItemStack> tryVanillaStackForId(String itemKey) {
		if (itemKey == null || itemKey.isBlank()) {
			return Optional.empty();
		}
		String raw = itemKey.trim();
		try {
			Identifier id;
			if (raw.indexOf(':') >= 0) {
				id = Identifier.parse(raw);
			} else {
				id = Identifier.fromNamespaceAndPath("minecraft", raw.toLowerCase(Locale.ROOT));
			}
			Optional<Holder.Reference<Item>> holder = BuiltInRegistries.ITEM.get(id);
			return holder.map(Holder::value)
					.filter(i -> i != Items.AIR)
					.map(ItemStack::new);
		} catch (Exception e) {
			return Optional.empty();
		}
	}
}
