import { Router } from "express";
import { fetchRecentSenders } from "../services/backendClient";
import { transliterate } from "../services/translit";

export const feedRouter = Router();

feedRouter.get("/telegram", async (req, res, next) => {
  try {
    const limit = Number(req.query.limit) || 10;
    const senders = await fetchRecentSenders(limit);

    // Opera Mini's proxy caches aggressively; this is a personal, per-token feed - never
    // safe to let the proxy serve a stale or cross-user copy.
    res.set("Cache-Control", "no-store");

    const token = String(req.query.token ?? "");

    if (req.isLegacyClient) {
      const transliterated = senders.map((sender) => ({
        ...sender,
        displayName: sender.displayName ? transliterate(sender.displayName) : sender.displayName,
      }));
      res.render("senders-legacy", { senders: transliterated, token });
    } else {
      // Modern React/TS SPA lands here later; for now this route just proves the branch works.
      res.render("senders-placeholder", { senders, token });
    }
  } catch (err) {
    next(err);
  }
});
