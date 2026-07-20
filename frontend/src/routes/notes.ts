import { Router } from "express";
import { createNote, fetchNotes } from "../services/backendClient";
import { transliterate } from "../services/translit";

export const notesRouter = Router();

notesRouter.get("/notes", async (req, res, next) => {
  try {
    const limit = Number(req.query.limit) || 20;
    const notes = await fetchNotes(limit);

    // Personal, per-token data - never safe for Opera Mini's proxy to cache/reuse.
    res.set("Cache-Control", "no-store");

    const token = String(req.query.token ?? "");

    if (req.isLegacyClient) {
      const transliterated = notes.map((note) => ({
        ...note,
        title: transliterate(note.title),
        content: transliterate(note.content),
      }));
      res.render("notes-list-legacy", { notes: transliterated, token });
    } else {
      res.render("notes-list-placeholder", { notes, token });
    }
  } catch (err) {
    next(err);
  }
});

notesRouter.get("/notes/new", (req, res) => {
  const token = String(req.query.token ?? "");
  res.set("Cache-Control", "no-store");
  const view = req.isLegacyClient ? "note-new-legacy" : "note-new-placeholder";
  res.render(view, { token, error: null, title: "", content: "" });
});

// Plain HTML form POST (no fetch/AJAX) so this works on the JS-less legacy client too -
// redirect-after-POST on success avoids a resubmit-on-refresh prompt.
notesRouter.post("/notes/new", async (req, res, next) => {
  try {
    const token = String(req.query.token ?? "");
    const title = String(req.body.title ?? "").trim();
    const content = String(req.body.content ?? "").trim();

    if (title.length === 0) {
      const view = req.isLegacyClient ? "note-new-legacy" : "note-new-placeholder";
      res.render(view, { token, error: "Title is required.", title, content });
      return;
    }

    await createNote(title, content);
    res.redirect(`/notes?token=${encodeURIComponent(token)}`);
  } catch (err) {
    next(err);
  }
});
