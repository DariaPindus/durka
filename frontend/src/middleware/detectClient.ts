import { NextFunction, Request, Response } from "express";

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Express {
    interface Request {
      isLegacyClient: boolean;
    }
  }
}

/**
 * Opera Mini on the Barbie Phone's OS pre-renders through Opera's proxy and never runs
 * client JS - that's the branch point for the whole app. Everything else (including plain
 * desktop browsers during development) falls through to the modern path.
 */
export function detectClient(req: Request, _res: Response, next: NextFunction): void {
  const userAgent = req.headers["user-agent"] ?? "";
  req.isLegacyClient = /Opera Mini/i.test(userAgent);
  next();
}
