import { Router } from "express";

export const homeRouter = Router();

// Each entry here is a future "app source" landing page (RSS, etc. join Telegram later) -
// just a name + path, not fetched from anywhere, since the list of sources is fixed at
// deploy time, not data.
const SOURCES = [{ name: "Telegram", path: "/telegram" }];

homeRouter.get("/", (req, res) => {
  const token = String(req.query.token ?? "");

  res.set("Cache-Control", "no-store");

  if (req.isLegacyClient) {
    res.render("home-legacy", { sources: SOURCES, token });
  } else {
    res.render("home-placeholder", { sources: SOURCES, token });
  }
});
