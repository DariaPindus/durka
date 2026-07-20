import { Router } from "express";
import {
  createTaskEvent,
  fetchTaskDates,
  fetchTasksForDate,
  TaskEventType,
} from "../services/backendClient";
import { transliterate } from "../services/translit";

export const eventsRouter = Router();

// Plain YYYY-MM-DD / HH:MM text fields, not <input type="date">/"time"> - Opera Mini on a
// feature phone doesn't reliably support those HTML5 input types (they silently degrade to
// plain text anyway on older builds), so validating our own simple format here is more
// predictable than trusting that degradation.
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;
const TIME_PATTERN = /^\d{2}:\d{2}$/;

// Route order below is intentional: /events/new must be registered before /events/:date, or
// Express would match "new" as a :date param.

eventsRouter.get("/events", async (req, res, next) => {
  try {
    const limit = Number(req.query.limit) || 20;
    const dates = await fetchTaskDates(limit);

    res.set("Cache-Control", "no-store");
    const token = String(req.query.token ?? "");

    const view = req.isLegacyClient ? "events-dates-legacy" : "events-dates-placeholder";
    res.render(view, { dates, token });
  } catch (err) {
    next(err);
  }
});

eventsRouter.get("/events/new", (req, res) => {
  const token = String(req.query.token ?? "");
  res.set("Cache-Control", "no-store");
  const view = req.isLegacyClient ? "event-new-legacy" : "event-new-placeholder";
  res.render(view, {
    token,
    error: null,
    type: "TASK" as TaskEventType,
    date: "",
    time: "",
    description: "",
    durationMinutes: "",
  });
});

// Plain HTML form POST (no fetch/AJAX). Duration is always shown (not conditionally, per the
// "always show it, labeled optional" choice) since a JS-less client can't react to the type
// dropdown without a page reload.
eventsRouter.post("/events/new", async (req, res, next) => {
  const token = String(req.query.token ?? "");
  const type = (req.body.type === "EVENT" ? "EVENT" : "TASK") as TaskEventType;
  const date = String(req.body.date ?? "").trim();
  const time = String(req.body.time ?? "").trim();
  const description = String(req.body.description ?? "").trim();
  const durationMinutesRaw = String(req.body.durationMinutes ?? "").trim();

  const view = req.isLegacyClient ? "event-new-legacy" : "event-new-placeholder";
  const rerender = (error: string) =>
    res.render(view, { token, error, type, date, time, description, durationMinutes: durationMinutesRaw });

  if (description.length === 0) {
    rerender("Description is required.");
    return;
  }
  if (!DATE_PATTERN.test(date)) {
    rerender("Date must be in YYYY-MM-DD format.");
    return;
  }
  if (!TIME_PATTERN.test(time)) {
    rerender("Time must be in HH:MM format (24-hour).");
    return;
  }

  let durationMinutes: number | null = null;
  if (durationMinutesRaw.length > 0) {
    const parsed = Number(durationMinutesRaw);
    if (!Number.isInteger(parsed) || parsed < 0) {
      rerender("Duration must be a whole number of minutes.");
      return;
    }
    durationMinutes = parsed;
  }

  const occursAt = `${date}T${time}:00Z`;

  try {
    await createTaskEvent(type, occursAt, description, durationMinutes);
    res.redirect(`/events?token=${encodeURIComponent(token)}`);
  } catch (err) {
    next(err);
  }
});

eventsRouter.get("/events/:date", async (req, res, next) => {
  try {
    const date = req.params.date;
    const entries = await fetchTasksForDate(date);

    res.set("Cache-Control", "no-store");
    const token = String(req.query.token ?? "");

    if (req.isLegacyClient) {
      const transliterated = entries.map((entry) => ({ ...entry, description: transliterate(entry.description) }));
      res.render("events-day-legacy", { date, entries: transliterated, token });
    } else {
      res.render("events-day-placeholder", { date, entries, token });
    }
  } catch (err) {
    next(err);
  }
});
