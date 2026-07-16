import { Router } from "express";
import { fetchRecentMessages } from "../services/backendClient";
import { transliterate } from "../services/translit";

export const feedRouter = Router();

feedRouter.get("/", async (req, res, next) => {
  try {
    const limit = Number(req.query.limit) || 50;
    const groups = await fetchRecentMessages(limit);

    // Opera Mini's proxy caches aggressively; this is a personal, per-token feed - never
    // safe to let the proxy serve a stale or cross-user copy.
    res.set("Cache-Control", "no-store");

    if (req.isLegacyClient) {
      // The Barbie Phone's font can't render Cyrillic at all (shows as tofu boxes) -
      // transliterate for this render path only; the modern path keeps the original text.
      const transliteratedGroups = groups.map((group) => ({
        ...group,
        authors: group.authors.map((author) => ({
          ...author,
          displayName: author.displayName ? transliterate(author.displayName) : author.displayName,
          messages: author.messages.map((message) => ({
            ...message,
            text: transliterate(message.text),
          })),
        })),
      }));
      res.render("feed-legacy", { groups: transliteratedGroups });
    } else {
      // Modern React/TS SPA lands here later; for now this route just proves the branch works.
      res.render("feed-placeholder", { groups });
    }
  } catch (err) {
    next(err);
  }
});
