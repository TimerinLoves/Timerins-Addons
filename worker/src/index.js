const HYPIXEL_API_BASE = "https://api.hypixel.net";
const ALLOWED_PATHS = new Set([
  "/v2/skyblock/profiles",
  "/v2/skyblock/profile"
]);

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method !== "GET") {
      return json({ success: false, cause: "Method not allowed" }, 405);
    }

    if (url.pathname === "/" || url.pathname === "/health") {
      return json({ ok: true, service: "timerins-addons-worker" }, 200);
    }

    if (!ALLOWED_PATHS.has(url.pathname)) {
      return json({ success: false, cause: "Unsupported path" }, 404);
    }

    const apiKey = (env.HYPIXEL_API_KEY || "").trim();
    if (!apiKey) {
      return json({ success: false, cause: "Worker secret HYPIXEL_API_KEY is missing" }, 500);
    }

    const upstreamUrl = new URL(HYPIXEL_API_BASE + url.pathname);
    for (const [k, v] of url.searchParams.entries()) {
      if (k.toLowerCase() === "key") {
        continue;
      }
      upstreamUrl.searchParams.append(k, v);
    }
    upstreamUrl.searchParams.set("key", apiKey);

    const upstreamResp = await fetch(upstreamUrl.toString(), {
      method: "GET",
      headers: {
        "Accept": "application/json",
        "User-Agent": "timerins-addons-worker/1.0"
      }
    });

    const body = await upstreamResp.text();
    return new Response(body, {
      status: upstreamResp.status,
      headers: {
        "Content-Type": "application/json; charset=utf-8",
        "Cache-Control": "no-store"
      }
    });
  }
};

function json(obj, status) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" }
  });
}
