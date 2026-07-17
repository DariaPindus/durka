import { Router } from "express";
import { fetchRssEntries, fetchRssEntry, fetchRssFeeds } from "../services/backendClient";
import { stripHtml } from "../services/html";
import { transliterate } from "../services/translit";

export const rssRouter = Router();

// Same reasoning as the Telegram feed: this is a personal, per-token view, never safe for
// Opera Mini's proxy to cache and reuse across requests/users.
const NO_STORE = "no-store";

rssRouter.get("/rss", async (req, res, next) => {
  try {
    const feeds = await fetchRssFeeds();
    res.set("Cache-Control", NO_STORE);
    const token = String(req.query.token ?? "");

    if (req.isLegacyClient) {
      const transliterated = feeds.map((feed) => ({
        ...feed,
        feedTitle: feed.feedTitle ? transliterate(feed.feedTitle) : feed.feedTitle,
      }));
      res.render("rss-feeds-legacy", { feeds: transliterated, token });
    } else {
      res.render("rss-feeds-placeholder", { feeds, token });
    }
  } catch (err) {
    next(err);
  }
});

rssRouter.get("/rss/feed", async (req, res, next) => {
  try {
    const feedUrl = String(req.query.url ?? "");
    const limit = Number(req.query.limit) || 10;
    const entries = await fetchRssEntries(feedUrl, limit);
    res.set("Cache-Control", NO_STORE);
    const token = String(req.query.token ?? "");

    if (req.isLegacyClient) {
      const transliterated = entries.map((entry) => ({
        ...entry,
        title: entry.title ? transliterate(entry.title) : entry.title,
      }));
      res.render("rss-entries-legacy", { entries: transliterated, feedUrl, token });
    } else {
      res.render("rss-entries-placeholder", { entries, feedUrl, token });
    }
  } catch (err) {
    next(err);
  }
});

rssRouter.get("/rss/entry/:id", async (req, res, next) => {
  try {
    const id = Number(req.params.id);
    const entry = await fetchRssEntry(id);
    const plainDescription = entry.description ? stripHtml(entry.description) : null;
    res.set("Cache-Control", NO_STORE);
    const token = String(req.query.token ?? "");

    if (req.isLegacyClient) {
      res.render("rss-entry-legacy", {
        entry: {
          ...entry,
          title: entry.title ? transliterate(entry.title) : entry.title,
          feedTitle: entry.feedTitle ? transliterate(entry.feedTitle) : entry.feedTitle,
          description: plainDescription ? transliterate(plainDescription) : plainDescription,
        },
        token,
      });
    } else {
      res.render("rss-entry-placeholder", { entry: { ...entry, description: plainDescription }, token });
    }
  } catch (err) {
    next(err);
  }
});
