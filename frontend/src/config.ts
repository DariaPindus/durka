import "dotenv/config";

function requireEnv(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing required env var ${name} - copy .env.example to .env and fill it in`);
  }
  return value;
}

export const config = {
  port: Number(process.env.PORT ?? 3000),
  backendUrl: process.env.BACKEND_URL ?? "http://localhost:8080",
  feedApiToken: requireEnv("FEED_API_TOKEN"),
};
