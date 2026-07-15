import express from "express";
import path from "path";
import { config } from "./config";
import { detectClient } from "./middleware/detectClient";
import { requireToken } from "./middleware/requireToken";
import { feedRouter } from "./routes/feed";

const app = express();

app.set("view engine", "ejs");
app.set("views", path.join(process.cwd(), "views"));

app.use(detectClient);
app.use("/", requireToken, feedRouter);

app.use((err: unknown, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
  console.error(err);
  res.status(502).send("Feed temporarily unavailable.");
});

app.listen(config.port, () => {
  console.log(`Frontend BFF listening on http://localhost:${config.port}`);
});
