package dev.timerin.timerinsaddons.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import dev.timerin.timerinsaddons.collections.CollectionGoal;
import dev.timerin.timerinsaddons.collections.CollectionHudSettings;
import dev.timerin.timerinsaddons.collections.CollectionStore;
import dev.timerin.timerinsaddons.gui.TrackerHelpScreen;
import dev.timerin.timerinsaddons.skyblock.ItemKeyMatcher;
import dev.timerin.timerinsaddons.util.ScreenWidgetInjector;
import dev.timerin.timerinsaddons.tracker.TrackerEntry;
import dev.timerin.timerinsaddons.tracker.TrackerStore;

public final class TimerinsAddonsConfigScreen {
	private TimerinsAddonsConfigScreen() {
	}

	private static final class MutableRow {
		String key;
		int target;
		String displayJson;

		MutableRow(String key, int target, String displayJson) {
			this.key = key == null ? "" : key;
			this.target = target;
			this.displayJson = displayJson;
		}
	}

	private static final class MutableCollectionRow {
		String resourceId;
		String itemKey;
		int target;
		String displayLabel;
		String sackAliasesCsv;
		String displayJson;

		MutableCollectionRow(String resourceId, String itemKey, int target, String displayLabel, String sackAliasesCsv,
				String displayJson) {
			this.resourceId = resourceId == null ? "" : resourceId;
			this.itemKey = itemKey == null ? "" : itemKey;
			this.target = target;
			this.displayLabel = displayLabel == null ? "" : displayLabel;
			this.sackAliasesCsv = sackAliasesCsv == null ? "" : sackAliasesCsv;
			this.displayJson = displayJson == null ? "" : displayJson;
		}
	}

	public static Screen create(Screen parent, TrackerStore store, CollectionStore collectionStore) {
		ModConfig cfg = store.getConfig();
		List<MutableRow> rows = new ArrayList<>();
		for (TrackerEntry e : store.getEntries()) {
			String dj = e.getDisplayStackJson();
			rows.add(new MutableRow(e.getItemKey(), e.getTargetAmount(), dj == null ? "" : dj));
		}
		if (rows.isEmpty() || !rows.get(rows.size() - 1).key.isBlank()) {
			rows.add(new MutableRow("", cfg.getDefaultTargetAmount(), ""));
		}

		CollectionHudSettings ch = collectionStore.getSettings();
		final boolean[] collectionHudEnabled = { ch.isHudEnabled() };

		List<MutableCollectionRow> cRows = new ArrayList<>();
		for (CollectionGoal g : collectionStore.getGoals()) {
			String dj = g.getDisplayStackJson();
			String aliases = String.join(", ", g.getSackDisplayAliases());
			cRows.add(new MutableCollectionRow(
					g.getResourceId(),
					g.getItemKey(),
					g.getTargetAmount(),
					g.getDisplayLabel(),
					aliases,
					dj == null ? "" : dj));
		}
		if (cRows.isEmpty() || !cRows.get(cRows.size() - 1).resourceId.isBlank()) {
			cRows.add(new MutableCollectionRow("", "", cfg.getDefaultTargetAmount(), "", "", ""));
		}

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.translatable("screen.timerins_addons.config.title"))
				.setSavingRunnable(() -> {
					applyRowsFromConfig(store, rows);
					applyCollectionRowsFromConfig(collectionStore, cRows, collectionHudEnabled[0]);
					store.save();
					collectionStore.save();
				})
				.setAfterInitConsumer(screen -> {
					Minecraft mc = Minecraft.getInstance();
					int bw = 150;
					ScreenWidgetInjector.addRenderableWidget(screen,
							Button.builder(Component.translatable("screen.timerins_addons.config.help_button"),
											b -> mc.setScreen(new TrackerHelpScreen(screen)))
									.bounds(8, 8, bw, 20)
									.build());
				});

		ConfigEntryBuilder eb = builder.entryBuilder();

		ConfigCategory hud = builder.getOrCreateCategory(Component.translatable("screen.timerins_addons.config.category.hud"));
		hud.addEntry(eb.startTextDescription(Component.translatable("screen.timerins_addons.config.help_hint"))
				.build());
		hud.addEntry(eb.startEnumSelector(
				Component.translatable("option.timerins_addons.display_mode"),
				ModConfig.TrackerDisplayMode.class,
				cfg.getDisplayMode())
				.setDefaultValue(ModConfig.TrackerDisplayMode.BOTH)
				.setEnumNameProvider(e -> Component.translatable(
						"option.timerins_addons.display_mode." + e.name().toLowerCase(Locale.ROOT)))
				.setSaveConsumer(cfg::setDisplayMode)
				.build());
		hud.addEntry(eb.startFloatField(Component.translatable("option.timerins_addons.hud_scale"), cfg.getHudScale())
				.setDefaultValue(1.0F)
				.setMin(0.25F)
				.setMax(4.0F)
				.setTooltip(Component.translatable("option.timerins_addons.hud_scale.tooltip"))
				.setSaveConsumer(cfg::setHudScale)
				.build());
		hud.addEntry(eb.startIntField(Component.translatable("option.timerins_addons.default_target"), cfg.getDefaultTargetAmount())
				.setDefaultValue(1)
				.setMin(1)
				.setMax(1_000_000_000)
				.setTooltip(Component.translatable("option.timerins_addons.default_target.tooltip"))
				.setSaveConsumer(cfg::setDefaultTargetAmount)
				.build());

		ConfigCategory track = builder.getOrCreateCategory(Component.translatable("screen.timerins_addons.config.category.tracker"));
		for (int i = 0; i < rows.size(); i++) {
			final MutableRow row = rows.get(i);
			int n = i + 1;
			track.addEntry(eb.startStrField(Component.translatable("option.timerins_addons.tracked_item_key", n), row.key)
					.setDefaultValue("")
					.setSaveConsumer(v -> row.key = v == null ? "" : v)
					.build());
			track.addEntry(eb.startIntField(Component.translatable("option.timerins_addons.tracked_item_target", n), row.target)
					.setDefaultValue(1)
					.setMin(0)
					.setMax(1_000_000_000)
					.setSaveConsumer(v -> row.target = v)
					.build());
		}

		ConfigCategory coll = builder.getOrCreateCategory(Component.translatable("screen.timerins_addons.config.category.collections"));
		coll.addEntry(eb.startTextDescription(Component.translatable("screen.timerins_addons.config.collections_intro"))
				.build());
		coll.addEntry(eb.startBooleanToggle(
				Component.translatable("option.timerins_addons.collection_hud_enabled"),
				collectionHudEnabled[0])
				.setDefaultValue(false)
				.setTooltip(Component.translatable("option.timerins_addons.collection_hud_enabled.tooltip"))
				.setSaveConsumer(v -> collectionHudEnabled[0] = v)
				.build());

		for (int i = 0; i < cRows.size(); i++) {
			final MutableCollectionRow row = cRows.get(i);
			int n = i + 1;
			coll.addEntry(eb.startStrField(Component.translatable("option.timerins_addons.collection_resource_id", n), row.resourceId)
					.setDefaultValue("")
					.setTooltip(Component.translatable("option.timerins_addons.collection_resource_id.tooltip"))
					.setSaveConsumer(v -> row.resourceId = v == null ? "" : v)
					.build());
			coll.addEntry(eb.startStrField(Component.translatable("option.timerins_addons.collection_item_key", n), row.itemKey)
					.setDefaultValue("")
					.setTooltip(Component.translatable("option.timerins_addons.collection_item_key.tooltip"))
					.setSaveConsumer(v -> row.itemKey = v == null ? "" : v)
					.build());
			coll.addEntry(eb.startIntField(Component.translatable("option.timerins_addons.collection_target", n), row.target)
					.setDefaultValue(1)
					.setMin(0)
					.setMax(1_000_000_000)
					.setSaveConsumer(v -> row.target = v)
					.build());
			coll.addEntry(eb.startStrField(Component.translatable("option.timerins_addons.collection_label", n), row.displayLabel)
					.setDefaultValue("")
					.setSaveConsumer(v -> row.displayLabel = v == null ? "" : v)
					.build());
			coll.addEntry(eb.startStrField(Component.translatable("option.timerins_addons.collection_sack_aliases", n), row.sackAliasesCsv)
					.setDefaultValue("")
					.setTooltip(Component.translatable("option.timerins_addons.collection_sack_aliases.tooltip"))
					.setSaveConsumer(v -> row.sackAliasesCsv = v == null ? "" : v)
					.build());
		}

		return builder.build();
	}

	private static List<String> parseSackAliasesCsv(String csv) {
		if (csv == null || csv.isBlank()) {
			return List.of();
		}
		List<String> out = new ArrayList<>();
		for (String part : csv.split(",")) {
			String t = part.trim();
			if (!t.isEmpty()) {
				out.add(t);
			}
		}
		return out;
	}

	private static void applyRowsFromConfig(TrackerStore store, List<MutableRow> rows) {
		List<String> peerKeys = new ArrayList<>();
		for (MutableRow r : rows) {
			if (r.key != null && !r.key.isBlank()) {
				peerKeys.add(r.key.trim());
			}
		}
		store.clear();
		for (MutableRow r : rows) {
			if (r.key == null || r.key.isBlank()) {
				continue;
			}
			Optional<String> resolved = ItemKeyMatcher.smartResolve(r.key, store, peerKeys.stream());
			String key = resolved.orElse(r.key.trim());
			TrackerEntry e = new TrackerEntry(key, r.target);
			if (r.displayJson != null && !r.displayJson.isBlank()) {
				e.setDisplayStackJson(r.displayJson);
			}
			store.putOrReplace(e);
		}
	}

	private static void applyCollectionRowsFromConfig(
			CollectionStore collectionStore,
			List<MutableCollectionRow> rows,
			boolean hudEnabled) {
		collectionStore.getSettings().setHudEnabled(hudEnabled);
		collectionStore.clearGoalsAndLocalProgressEstimates();
		for (MutableCollectionRow r : rows) {
			if (r.resourceId == null || r.resourceId.isBlank()) {
				continue;
			}
			String rid = r.resourceId.trim();
			String ikey = r.itemKey == null || r.itemKey.isBlank() ? rid : r.itemKey.trim();
			CollectionGoal g = new CollectionGoal(rid, ikey, r.target);
			if (r.displayLabel != null && !r.displayLabel.isBlank()) {
				g.setDisplayLabel(r.displayLabel.trim());
			}
			g.setSackDisplayAliases(parseSackAliasesCsv(r.sackAliasesCsv));
			if (r.displayJson != null && !r.displayJson.isBlank()) {
				g.setDisplayStackJson(r.displayJson);
			}
			collectionStore.putOrReplace(g);
		}
	}
}
