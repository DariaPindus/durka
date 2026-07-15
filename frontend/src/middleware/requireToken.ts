import { timingSafeEqual } from "crypto";
import { NextFunction, Request, Response } from "express";
import { config } from "../config";

function constantTimeEquals(a: string, b: string): boolean {
  const bufA = Buffer.from(a);
  const bufB = Buffer.from(b);
  // Lengths must match for timingSafeEqual to run at all - this leaks token length on
  // mismatch, same tradeoff the backend's MessageDigest.isEqual-based check accepts.
  if (bufA.length !== bufB.length) return false;
  return timingSafeEqual(bufA, bufB);
}

/**
 * Gates the actual feed page behind the same capability token that protects the backend
 * API - without this, the token only protected frontend-to-backend calls, leaving the
 * page itself open to anyone who finds the domain. Query param only (no header option
 * here, unlike the backend): this is the human-facing, bookmarked-URL side of the token,
 * not a server-to-server call.
 */
export function requireToken(req: Request, res: Response, next: NextFunction): void {
  const provided = typeof req.query.token === "string" ? req.query.token : undefined;

  if (!provided || !constantTimeEquals(provided, config.feedApiToken)) {
    res.set("Cache-Control", "no-store");
    res.status(401).send("Not authorized.");
    return;
  }

  next();
}
