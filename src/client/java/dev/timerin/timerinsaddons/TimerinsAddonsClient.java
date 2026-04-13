package dev.timerin.timerinsaddons;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.mojang.brigadier.arguments.StringArgumentType;

import dev.timerin.timerinsaddons.collections.CollectionDeltaTracker;
import dev.timerin.timerinsaddons.collections.CollectionGoal;
import dev.timerin.timerinsaddons.collections.CollectionStore;
import dev.timerin.timerinsaddons.collections.SackChatContribution;
import dev.timerin.timerinsaddons.collections.SackGuiScanner;
import dev.timerin.timerinsaddons.config.TimerinsAddonsConfigScreen;
import dev.timerin.timerinsaddons.hud.ItemTrackerHud;
import dev.timerin.timerinsaddons.hud.ItemTrackerInventoryOverlay;
import dev.timerin.timerinsaddons.hud.TrackerPanelInteraction;
import dev.timerin.timerinsaddons.hypixel.HypixelProfileService;
import dev.timerin.timerinsaddons.input.ModKeyBindings;
import dev.timerin.timerinsaddons.integration.FirmamentIntegration;
import dev.timerin.timerinsaddons.skyblock.ItemKeyMatcher;
import dev.timerin.timerinsaddons.skyblock.NeuInternalItemIcons;
import dev.timerin.timerinsaddons.tracker.TrackerEntry;
import dev.timerin.timerinsaddons.tracker.TrackerStore;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class TimerinsAddonsClient implements ClientModInitializer {
	/** 20 TPS × 5s — only then request a Hypixel profile refresh (in addition to service throttle). */
	private static final int COLLECTION_API_POLL_TICKS = 100;

	private final TrackerStore trackerStore = new TrackerStore();
	private final CollectionStore collectionStore = new CollectionStore();
	private int collectionPublicApiPollTicks;

	@Override
	public void onInitializeClient() {
		FirmamentIntegration.logIfPresent();
		trackerStore.load();
		collectionStore.load();
		ModKeyBindings.register();
		ItemTrackerHud.register(trackerStore, collectionStore);
		ItemTrackerInventoryOverlay.register(trackerStore, collectionStore);
		TrackerPanelInteraction.register(trackerStore, collectionStore);
		SackChatContribution.register(collectionStore, trackerStore);
		// Game/system messages only — CHAT can duplicate the same line for some server payloads and double-count sacks.
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> SackChatContribution.onRawChatMessage(message));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			SackGuiScanner.tick(client, collectionStore, trackerStore);
			CollectionDeltaTracker.tick(client, collectionStore);
			while (ModKeyBindings.OPEN_TRACKER_CONFIG.consumeClick()) {
				Screen parent = client.screen;
				client.execute(() -> client.setScreen(TimerinsAddonsConfigScreen.create(parent, trackerStore, collectionStore)));
			}
			if (client.player != null
					&& collectionStore.getSettings().isHudEnabled()
					&& !collectionStore.getGoals().isEmpty()
					&& !collectionStore.getSettings().getHypixelApiKey().isEmpty()) {
				collectionPublicApiPollTicks++;
				if (collectionPublicApiPollTicks >= COLLECTION_API_POLL_TICKS) {
					collectionPublicApiPollTicks = 0;
					HypixelProfileService.refreshCollectionsAsync(client, collectionStore);
				}
			} else {
				collectionPublicApiPollTicks = 0;
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(literal("timerin")
					.then(literal("config").executes(ctx -> {
						Minecraft c = Minecraft.getInstance();
						c.execute(() -> c.setScreen(TimerinsAddonsConfigScreen.create(c.screen, trackerStore, collectionStore)));
						return 1;
					}))
					.then(literal("add")
							.then(literal("item")
									.then(argument("item_spec", StringArgumentType.greedyString()).executes(ctx -> {
										String spec = StringArgumentType.getString(ctx, "item_spec").trim();
										Optional<GreedyIdAmount> parsed = parseGreedyIdAndAmount(spec);
										if (parsed.isEmpty()) {
											ctx.getSource().sendFeedback(
													Component.translatable("message.timerins_addons.add_item_parse_failed"));
											return 0;
										}
										String raw = parsed.get().id();
										int amount = parsed.get().amount();
										Stream<String> neu = NeuInternalItemIcons.internalNameVariants(raw).stream();
										String key = ItemKeyMatcher.smartResolve(raw, trackerStore, neu).orElse(raw);
										trackerStore.putOrReplace(new TrackerEntry(key, amount));
										trackerStore.save();
										ctx.getSource().sendFeedback(
												Component.translatable("message.timerins_addons.command.added", key, amount));
										return 1;
									})))
							.then(literal("collection")
									.then(argument("collection_spec", StringArgumentType.greedyString()).executes(ctx -> {
										String spec = StringArgumentType.getString(ctx, "collection_spec").trim();
										Optional<GreedyIdAmount> parsed = parseGreedyIdAndAmount(spec);
										if (parsed.isEmpty()) {
											ctx.getSource().sendFeedback(
													Component.translatable(
															"message.timerins_addons.collection_command_parse_failed"));
											return 0;
										}
										return runCollectionAdd(
												ctx.getSource(),
												collectionStore,
												parsed.get().id(),
												parsed.get().amount());
									}))))
					.then(literal("remove")
							.then(literal("item")
									.then(argument("itemId", StringArgumentType.greedyString()).executes(ctx -> {
										String raw = StringArgumentType.getString(ctx, "itemId").trim();
										int n = removeTrackedEntries(trackerStore, raw);
										trackerStore.save();
										if (n <= 0) {
											ctx.getSource().sendFeedback(
													Component.translatable("message.timerins_addons.command.remove_none"));
											return 0;
										}
										ctx.getSource().sendFeedback(
												Component.translatable("message.timerins_addons.command.removed_item", n, raw));
										return 1;
									})))
							.then(literal("collection")
									.then(argument("resourceId", StringArgumentType.greedyString()).executes(ctx -> {
										String raw = StringArgumentType.getString(ctx, "resourceId").trim();
										int n = removeCollectionEntries(collectionStore, raw);
										collectionStore.save();
										if (n <= 0) {
											ctx.getSource().sendFeedback(
													Component.translatable("message.timerins_addons.command.remove_none"));
											return 0;
										}
										ctx.getSource().sendFeedback(
												Component.translatable("message.timerins_addons.command.removed_collection", n, raw));
										return 1;
									}))))
					.then(literal("clear")
							.executes(ctx -> {
								clearAll(trackerStore, collectionStore, ctx.getSource());
								return 1;
							})
							.then(literal("all").executes(ctx -> {
								clearAll(trackerStore, collectionStore, ctx.getSource());
								return 1;
							}))
							.then(literal("item").executes(ctx -> {
								trackerStore.clear();
								trackerStore.save();
								ctx.getSource().sendFeedback(
										Component.translatable("message.timerins_addons.command.cleared_item"));
								return 1;
							}))
							.then(literal("collection").executes(ctx -> {
								collectionStore.clearGoalsAndLocalProgressEstimates();
								collectionStore.save();
								ctx.getSource().sendFeedback(
										Component.translatable("message.timerins_addons.command.cleared_collection"));
								return 1;
							}))));
		});
	}

	/**
	 * Greedy tail after {@code add item} / {@code add collection}: {@code <id …> <target>} with a space before the final
	 * number (Brigadier {@code string()} cannot express ids like {@code raw_fish:1} in one word).
	 */
	private record GreedyIdAmount(String id, int amount) {
	}

	private static Optional<GreedyIdAmount> parseGreedyIdAndAmount(String spec) {
		if (spec == null || spec.isBlank()) {
			return Optional.empty();
		}
		String t = spec.trim();
		int lastSpace = t.lastIndexOf(' ');
		if (lastSpace <= 0) {
			return Optional.empty();
		}
		String idPart = t.substring(0, lastSpace).trim();
		String numPart = t.substring(lastSpace + 1).trim();
		if (idPart.isEmpty() || numPart.isEmpty()) {
			return Optional.empty();
		}
		try {
			int amount = Integer.parseInt(numPart);
			if (amount < 1) {
				return Optional.empty();
			}
			return Optional.of(new GreedyIdAmount(idPart, amount));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	private static void clearAll(TrackerStore trackerStore, CollectionStore collectionStore, FabricClientCommandSource source) {
		trackerStore.clear();
		collectionStore.clearGoalsAndLocalProgressEstimates();
		trackerStore.save();
		collectionStore.save();
		source.sendFeedback(Component.translatable("message.timerins_addons.command.cleared_all"));
	}

	private static int runCollectionAdd(
			FabricClientCommandSource source,
			CollectionStore collectionStore,
			String rawId,
			int amount) {
		if (rawId == null || rawId.isBlank()) {
			source.sendFeedback(Component.translatable("message.timerins_addons.collection_command_parse_failed"));
			return 0;
		}
		if (collectionStore.getSettings().getHypixelApiKey().isEmpty()) {
			source.sendFeedback(Component.translatable("message.timerins_addons.collection_need_api_key"));
			return 0;
		}
		Minecraft mc = Minecraft.getInstance();
		HypixelProfileService.forceAllowNextFetch();
		if (!HypixelProfileService.refreshCollectionsBlocking(mc, collectionStore)) {
			String err = HypixelProfileService.getLastBlockingFetchError();
			source.sendFeedback(err != null && !err.isEmpty()
					? Component.translatable("message.timerins_addons.collection_fetch_failed_with_reason", err)
					: Component.translatable("message.timerins_addons.collection_fetch_failed"));
			return 0;
		}
		String canonical = collectionStore.resolveCanonicalCollectionId(rawId);
		String rid = canonical != null ? canonical : rawId;
		CollectionGoal goal = new CollectionGoal(rid, rid, amount);
		collectionStore.putOrReplace(goal);
		collectionStore.save();
		int curI = goal.resolveCurrent(mc.player, collectionStore);
		source.sendFeedback(Component.translatable("message.timerins_addons.collection_added", rid, curI, amount));
		return 1;
	}

	private static int removeTrackedEntries(TrackerStore store, String raw) {
		if (raw == null || raw.isBlank()) {
			return 0;
		}
		String trimmed = raw.trim();
		Set<String> toRemove = new LinkedHashSet<>();
		ItemKeyMatcher.smartResolve(trimmed, store, NeuInternalItemIcons.internalNameVariants(trimmed).stream())
				.ifPresent(toRemove::add);
		for (TrackerEntry e : store.getEntries()) {
			if (ItemKeyMatcher.looseKeysMatch(e.getItemKey(), trimmed)) {
				toRemove.add(e.getItemKey());
			}
		}
		for (String k : toRemove) {
			store.remove(k);
		}
		return toRemove.size();
	}

	private static int removeCollectionEntries(CollectionStore store, String raw) {
		if (raw == null || raw.isBlank()) {
			return 0;
		}
		String trimmed = raw.trim();
		List<String> toRemove = new ArrayList<>();
		for (CollectionGoal g : new ArrayList<>(store.getGoals())) {
			String rid = g.getResourceId().trim();
			if (rid.isEmpty()) {
				continue;
			}
			String ikey = g.getItemKey().trim();
			boolean match = store.resourceIdMatchesHypixelCollectionKey(rid, trimmed)
					|| ItemKeyMatcher.looseKeysMatch(rid, trimmed)
					|| (!ikey.isEmpty() && ItemKeyMatcher.looseKeysMatch(ikey, trimmed));
			if (match) {
				toRemove.add(rid);
			}
		}
		for (String id : toRemove) {
			store.remove(id);
		}
		return toRemove.size();
	}
}
