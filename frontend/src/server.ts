import express from "express";
import path from "path";
import { config } from "./config";
import { detectClient } from "./middleware/detectClient";
import { requireToken } from "./middleware/requireToken";
import { conversationRouter } from "./routes/conversation";
import { feedRouter } from "./routes/feed";
import { homeRouter } from "./routes/home";

const app = express();

app.set("view engine", "ejs");
app.set("views", path.join(process.cwd(), "views"));

// Needed for the reply form's plain HTML POST (application/x-www-form-urlencoded) - no
// fetch/AJAX, so this is what actually carries the submitted text to the route handler.
app.use(express.urlencoded({ extended: false }));

app.use(detectClient);
app.use("/", requireToken, homeRouter);
app.use("/", requireToken, feedRouter);
app.use("/", requireToken, conversationRouter);

app.use((err: unknown, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
  console.error(err);
  res.status(502).send("Feed temporarily unavailable.");
});

app.listen(config.port, () => {
  console.log(`Frontend BFF listening on http://localhost:${config.port}`);
});
