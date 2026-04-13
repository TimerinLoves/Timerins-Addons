package dev.timerin.timerinsaddons.hypixel;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;

import dev.timerin.timerinsaddons.collections.CollectionStore;

/**
 * SkyBlock profile collection totals via Hypixel Public API (developer key in {@link CollectionStore} settings).
 * Automatic polls are throttled to {@link #MIN_INTERVAL_MS}; manual refresh uses {@link #forceAllowNextFetch()}.
 */
public final class HypixelProfileService {
	private static final Logger LOGGER = LoggerFactory.getLogger("timerins_addons");
	/** Hypixel disabled unversioned SkyBlock profile routes (Sept 2024); v2 is required. */
	private static final String URL_SKYBLOCK_PROFILES = "https://api.hypixel.net/v2/skyblock/profiles?uuid=";
	private static final String URL_SKYBLOCK_PROFILE = "https://api.hypixel.net/v2/skyblock/profile?profile=";
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(12))
			.build();

	/** Minimum time between automatic profile fetches (client poll). */
	static final long MIN_INTERVAL_MS = 5_000L;

	private static volatile long lastFetchStartMs;

	/** Set on the last {@link #refreshCollectionsBlocking} failure; cleared on success or at start of a new attempt. */
	private static volatile String lastBlockingFetchError;

	private HypixelProfileService() {
	}

	public static String getLastBlockingFetchError() {
		return lastBlockingFetchError;
	}

	public static void refreshCollectionsAsync(Minecraft client, CollectionStore store) {
		String key = store.getSettings().getHypixelApiKey();
		if (key.isEmpty() || client.player == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastFetchStartMs < MIN_INTERVAL_MS) {
			return;
		}
		lastFetchStartMs = now;
		UUID uuid = client.player.getUUID();
		String uuidStr = uuid.toString();

		HttpRequest profilesReq = HttpRequest.newBuilder()
				.uri(URI.create(URL_SKYBLOCK_PROFILES
						+ URLEncoder.encode(uuidStr, StandardCharsets.UTF_8)
						+ "&key=" + URLEncoder.encode(key, StandardCharsets.UTF_8)))
				.timeout(Duration.ofSeconds(15))
				.GET()
				.build();

		HTTP.sendAsync(profilesReq, HttpResponse.BodyHandlers.ofString())
				.thenAccept(profilesResp -> {
					try {
						JsonObject chosen = parseChosenProfile(profilesResp.body(), null);
						if (chosen == null || !chosen.has("profile_id")) {
							return;
						}
						String profileId = chosen.get("profile_id").getAsString();
						HttpRequest profileReq = HttpRequest.newBuilder()
								.uri(URI.create(URL_SKYBLOCK_PROFILE
										+ URLEncoder.encode(profileId, StandardCharsets.UTF_8)
										+ "&key=" + URLEncoder.encode(key, StandardCharsets.UTF_8)))
								.timeout(Duration.ofSeconds(15))
								.GET()
								.build();
						HTTP.sendAsync(profileReq, HttpResponse.BodyHandlers.ofString())
								.thenAccept(profileResp -> applyProfileResponse(client, store, uuidStr, profileResp.body(), null))
								.exceptionally(ex -> {
									LOGGER.debug("Hypixel profile request failed", ex);
									return null;
								});
					} catch (Exception ex) {
						LOGGER.debug("Hypixel profiles parse failed", ex);
					}
				})
				.exceptionally(ex -> {
					LOGGER.debug("Hypixel profiles request failed", ex);
					return null;
				});
	}

	/** For commands: runs on client thread; bypasses the normal throttle. */
	public static boolean refreshCollectionsBlocking(Minecraft client, CollectionStore store) {
		lastBlockingFetchError = null;
		String key = store.getSettings().getHypixelApiKey();
		if (key.isEmpty() || client.player == null) {
			lastBlockingFetchError = "API key empty or not in world";
			return false;
		}
		String uuidStr = client.player.getUUID().toString();
		Consumer<String> fail = msg -> lastBlockingFetchError = msg;
		try {
			HttpRequest profilesReq = HttpRequest.newBuilder()
					.uri(URI.create(URL_SKYBLOCK_PROFILES
							+ URLEncoder.encode(uuidStr, StandardCharsets.UTF_8)
							+ "&key=" + URLEncoder.encode(key, StandardCharsets.UTF_8)))
					.timeout(Duration.ofSeconds(15))
					.GET()
					.build();
			HttpResponse<String> profilesResp = HTTP.send(profilesReq, HttpResponse.BodyHandlers.ofString());
			int psc = profilesResp.statusCode();
			if (psc < 200 || psc >= 300) {
				lastBlockingFetchError = "profiles HTTP " + psc;
				return false;
			}
			JsonObject chosen = parseChosenProfile(profilesResp.body(), fail);
			if (chosen == null || !chosen.has("profile_id")) {
				if (lastBlockingFetchError == null) {
					lastBlockingFetchError = "No usable SkyBlock profile in API response";
				}
				return false;
			}
			String profileId = chosen.get("profile_id").getAsString();
			HttpRequest profileReq = HttpRequest.newBuilder()
					.uri(URI.create(URL_SKYBLOCK_PROFILE
							+ URLEncoder.encode(profileId, StandardCharsets.UTF_8)
							+ "&key=" + URLEncoder.encode(key, StandardCharsets.UTF_8)))
					.timeout(Duration.ofSeconds(15))
					.GET()
					.build();
			HttpResponse<String> profileResp = HTTP.send(profileReq, HttpResponse.BodyHandlers.ofString());
			int prc = profileResp.statusCode();
			if (prc < 200 || prc >= 300) {
				lastBlockingFetchError = "profile HTTP " + prc;
				return false;
			}
			boolean ok = applyProfileResponse(client, store, uuidStr, profileResp.body(), fail);
			if (ok) {
				lastFetchStartMs = System.currentTimeMillis();
				lastBlockingFetchError = null;
			}
			return ok;
		} catch (Exception ex) {
			LOGGER.debug("Hypixel blocking fetch failed", ex);
			lastBlockingFetchError = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
			return false;
		}
	}

	private static String hypixelCause(JsonObject root) {
		if (root.has("cause") && root.get("cause").isJsonPrimitive()) {
			return root.get("cause").getAsString();
		}
		return null;
	}

	private static boolean hypixelSuccess(JsonObject root) {
		return root.has("success") && root.get("success").isJsonPrimitive() && root.get("success").getAsBoolean();
	}

	/**
	 * @param onFailure if non-null, receives a short user-facing reason when this returns null.
	 */
	private static JsonObject parseChosenProfile(String body, Consumer<String> onFailure) {
		try {
			JsonObject root = JsonParser.parseString(body).getAsJsonObject();
			if (!hypixelSuccess(root)) {
				if (onFailure != null) {
					String cause = hypixelCause(root);
					onFailure.accept(cause != null ? cause : "Hypixel API (profiles) returned success=false");
				}
				return null;
			}
			JsonArray profiles = root.getAsJsonArray("profiles");
			if (profiles == null || profiles.isEmpty()) {
				if (onFailure != null) {
					onFailure.accept("No SkyBlock profiles for this Minecraft account");
				}
				return null;
			}
			JsonObject chosen = null;
			for (JsonElement el : profiles) {
				if (!el.isJsonObject()) {
					continue;
				}
				JsonObject p = el.getAsJsonObject();
				if (p.has("selected") && p.get("selected").getAsBoolean()) {
					chosen = p;
					break;
				}
			}
			if (chosen == null) {
				chosen = profiles.get(0).getAsJsonObject();
			}
			return chosen;
		} catch (Exception ex) {
			LOGGER.debug("Hypixel profiles parse failed", ex);
			if (onFailure != null) {
				onFailure.accept("Could not read Hypixel profiles JSON");
			}
			return null;
		}
	}

	private static JsonObject findMemberForPlayer(JsonObject members, String uuidDashed, Consumer<String> onFailure) {
		String undashed = uuidDashed.replace("-", "");
		JsonObject m = members.getAsJsonObject(uuidDashed);
		if (m == null) {
			m = members.getAsJsonObject(undashed);
		}
		if (m == null) {
			for (String k : members.keySet()) {
				if (k.equalsIgnoreCase(uuidDashed) || k.equalsIgnoreCase(undashed)) {
					m = members.getAsJsonObject(k);
					break;
				}
			}
		}
		if (m == null && members.size() == 1) {
			String only = members.keySet().iterator().next();
			m = members.getAsJsonObject(only);
		}
		if (m == null && onFailure != null) {
			onFailure.accept("Profile has no member data for your UUID (co-op keys may differ)");
		}
		return m;
	}

	private static boolean applyProfileResponse(Minecraft client, CollectionStore store, String uuidStr, String profileBody,
			Consumer<String> onFailure) {
		try {
			JsonObject proot = JsonParser.parseString(profileBody).getAsJsonObject();
			if (!hypixelSuccess(proot)) {
				if (onFailure != null) {
					String cause = hypixelCause(proot);
					onFailure.accept(cause != null ? cause : "Hypixel API (profile) returned success=false");
				}
				return false;
			}
			JsonObject profile = proot.getAsJsonObject("profile");
			if (profile == null) {
				if (onFailure != null) {
					onFailure.accept("Missing \"profile\" in API response");
				}
				return false;
			}
			JsonObject members = profile.getAsJsonObject("members");
			if (members == null) {
				if (onFailure != null) {
					onFailure.accept("Missing \"members\" in profile");
				}
				return false;
			}
			JsonObject member = findMemberForPlayer(members, uuidStr, onFailure);
			if (member == null) {
				return false;
			}
			JsonObject collection;
			if (!member.has("collection") || member.get("collection").isJsonNull()) {
				collection = new JsonObject();
			} else if (!member.get("collection").isJsonObject()) {
				if (onFailure != null) {
					onFailure.accept("Member \"collection\" is not an object");
				}
				return false;
			} else {
				collection = member.getAsJsonObject("collection");
			}
			Map<String, Long> amounts = new HashMap<>();
			for (Map.Entry<String, JsonElement> e : collection.entrySet()) {
				JsonElement v = e.getValue();
				if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
					amounts.put(e.getKey(), v.getAsLong());
				}
			}
			Runnable apply = () -> store.setApiCollectionAmounts(amounts);
			if (client.isSameThread()) {
				apply.run();
			} else {
				client.execute(apply);
			}
			return true;
		} catch (Exception ex) {
			LOGGER.debug("Hypixel profile parse failed", ex);
			if (onFailure != null) {
				onFailure.accept("Could not parse profile JSON");
			}
			return false;
		}
	}

	public static void forceAllowNextFetch() {
		lastFetchStartMs = 0;
	}
}
