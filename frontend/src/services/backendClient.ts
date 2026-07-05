import { config } from "../config";

export interface MessageDto {
  id: string;
  timestamp: string;
  text: string;
}

export interface AuthorGroupDto {
  displayName: string | null;
  username: string | null;
  messages: MessageDto[];
}

export interface ChatTypeGroupDto {
  chatType: string;
  authors: AuthorGroupDto[];
}

/**
 * Calls the backend with the token as a Bearer header, not a query param - this is a
 * server-to-server call, not the bookmarked-URL case the query param exists for, and a
 * header keeps the token out of any request logs that capture URLs.
 */
export async function fetchRecentMessages(limit: number): Promise<ChatTypeGroupDto[]> {
  const url = new URL("/api/messages/recent", config.backendUrl);
  url.searchParams.set("limit", String(limit));

  const response = await fetch(url, {
    headers: { Authorization: `Bearer ${config.feedApiToken}` },
  });

  if (!response.ok) {
    throw new Error(`Backend responded ${response.status} for ${url.pathname}`);
  }

  return response.json() as Promise<ChatTypeGroupDto[]>;
}
