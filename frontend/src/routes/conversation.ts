import { Router } from "express";
import { fetchConversation, sendReply } from "../services/backendClient";
import { resolveUaTags, transliterate } from "../services/translit";

export const conversationRouter = Router();

conversationRouter.get("/telegram/feed/:chatId", async (req, res, next) => {
  try {
    const chatId = Number(req.params.chatId);
    const limit = Number(req.query.limit) || 25;
    const conversation = await fetchConversation(chatId, limit);
    const token = String(req.query.token ?? "");

    res.set("Cache-Control", "no-store");

    const viewModel = {
      chatId,
      chatType: conversation.chatType,
      title: conversation.title,
      canReply: conversation.canReply,
      token,
    };

    if (req.isLegacyClient) {
      const messages = conversation.messages.map((message) => ({
        ...message,
        text: transliterate(message.text),
        fromDisplayName: message.fromDisplayName ? transliterate(message.fromDisplayName) : message.fromDisplayName,
      }));
      res.render("conversation-legacy", { ...viewModel, title: conversation.title ? transliterate(conversation.title) : conversation.title, messages });
    } else {
      res.render("conversation-placeholder", { ...viewModel, messages: conversation.messages });
    }
  } catch (err) {
    next(err);
  }
});

// Plain HTML form POST (no fetch/AJAX) so this works on the JS-less legacy client too -
// redirect-after-POST back to the same conversation avoids a resubmit-on-refresh prompt.
conversationRouter.post("/telegram/feed/:chatId/reply", async (req, res, next) => {
  try {
    const chatId = Number(req.params.chatId);
    const token = String(req.query.token ?? "");
    const text = resolveUaTags(String(req.body.text ?? "").trim());

    if (text.length > 0) {
      await sendReply(chatId, text);
    }

    res.redirect(`/telegram/feed/${chatId}?token=${encodeURIComponent(token)}`);
  } catch (err) {
    next(err);
  }
});
