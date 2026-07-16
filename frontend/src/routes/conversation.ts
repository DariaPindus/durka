import { Router } from "express";
import { fetchConversation, sendReply } from "../services/backendClient";
import { transliterate } from "../services/translit";

export const conversationRouter = Router();

conversationRouter.get("/feed/:chatId", async (req, res, next) => {
  try {
    const chatId = Number(req.params.chatId);
    const limit = Number(req.query.limit) || 20;
    const messages = await fetchConversation(chatId, limit);
    const token = String(req.query.token ?? "");

    res.set("Cache-Control", "no-store");

    if (req.isLegacyClient) {
      const transliterated = messages.map((message) => ({ ...message, text: transliterate(message.text) }));
      res.render("conversation-legacy", { chatId, messages: transliterated, token });
    } else {
      res.render("conversation-placeholder", { chatId, messages, token });
    }
  } catch (err) {
    next(err);
  }
});

// Plain HTML form POST (no fetch/AJAX) so this works on the JS-less legacy client too -
// redirect-after-POST back to the same conversation avoids a resubmit-on-refresh prompt.
conversationRouter.post("/feed/:chatId/reply", async (req, res, next) => {
  try {
    const chatId = Number(req.params.chatId);
    const token = String(req.query.token ?? "");
    const text = String(req.body.text ?? "").trim();

    if (text.length > 0) {
      await sendReply(chatId, text);
    }

    res.redirect(`/feed/${chatId}?token=${encodeURIComponent(token)}`);
  } catch (err) {
    next(err);
  }
});
