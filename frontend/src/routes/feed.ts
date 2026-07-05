import { Router } from "express";
import { fetchRecentMessages } from "../services/backendClient";

export const feedRouter = Router();

feedRouter.get("/", async (req, res, next) => {
  try {
    const limit = Number(req.query.limit) || 50;
    const groups = await fetchRecentMessages(limit);

    // Opera Mini's proxy caches aggressively; this is a personal, per-token feed - never
    // safe to let the proxy serve a stale or cross-user copy.
    res.set("Cache-Control", "no-store");

    if (req.isLegacyClient) {
      res.render("feed-legacy", { groups });
    } else {
      // Modern React/TS SPA lands here later; for now this route just proves the branch works.
      res.render("feed-placeholder", { groups });
    }
  } catch (err) {
    next(err);
  }
});
