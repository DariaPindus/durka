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

export interface SenderSummaryDto {
  chatId: number;
  chatType: "private" | "group" | "channel";
  displayName: string | null;
  username: string | null;
  lastMessageAt: string;
}

// Field is "outgoing", not "isOutgoing" - matches the backend's actual wire format. Kotlin's
// "isOutgoing: Boolean" compiles to a getter named isOutgoing(), which Jackson's default bean
// introspection treats as the JavaBean property "outgoing" (stripping the "is" prefix) when
// serializing - the backend DTO is named to match that on purpose instead of fighting it.
export interface ConversationMessageDto {
  id: string;
  timestamp: string;
  text: string;
  outgoing: boolean;
  fromDisplayName: string | null;
}

export interface ConversationDto {
  chatId: number;
  chatType: "private" | "group" | "channel";
  title: string | null;
  canReply: boolean;
  messages: ConversationMessageDto[];
}

/**
 * Calls the backend with the token as a Bearer header, not a query param - this is a
 * server-to-server call, not the bookmarked-URL case the query param exists for, and a
 * header keeps the token out of any request logs that capture URLs.
 */
async function backendFetch(path: string, params: Record<string, string>) {
  const url = new URL(path, config.backendUrl);
  for (const [key, value] of Object.entries(params)) {
    url.searchParams.set(key, value);
  }

  const response = await fetch(url, {
    headers: { Authorization: `Bearer ${config.feedApiToken}` },
  });

  if (!response.ok) {
    throw new Error(`Backend responded ${response.status} for ${url.pathname}`);
  }

  return response;
}

export async function fetchRecentMessages(limit: number): Promise<ChatTypeGroupDto[]> {
  const response = await backendFetch("/api/messages/recent", { limit: String(limit) });
  return response.json() as Promise<ChatTypeGroupDto[]>;
}

export async function fetchRecentSenders(limit: number): Promise<SenderSummaryDto[]> {
  const response = await backendFetch("/api/messages/senders", { limit: String(limit) });
  return response.json() as Promise<SenderSummaryDto[]>;
}

export async function fetchConversation(chatId: number, limit: number): Promise<ConversationDto> {
  const response = await backendFetch(`/api/messages/senders/${chatId}`, { limit: String(limit) });
  return response.json() as Promise<ConversationDto>;
}

export interface RssFeedSummaryDto {
  feedUrl: string;
  feedTitle: string | null;
  itemCount: number;
  lastPublishedAt: string;
}

export interface RssEntrySummaryDto {
  id: number;
  title: string | null;
  publishedAt: string;
}

export interface RssEntryDetailDto {
  id: number;
  feedUrl: string;
  feedTitle: string | null;
  title: string | null;
  link: string | null;
  publishedAt: string;
  description: string | null;
}

export async function fetchRssFeeds(): Promise<RssFeedSummaryDto[]> {
  const response = await backendFetch("/api/rss/feeds", {});
  return response.json() as Promise<RssFeedSummaryDto[]>;
}

export async function fetchRssEntries(feedUrl: string, limit: number): Promise<RssEntrySummaryDto[]> {
  const response = await backendFetch("/api/rss/entries", { feedUrl, limit: String(limit) });
  return response.json() as Promise<RssEntrySummaryDto[]>;
}

export async function fetchRssEntry(id: number): Promise<RssEntryDetailDto> {
  const response = await backendFetch(`/api/rss/entries/${id}`, {});
  return response.json() as Promise<RssEntryDetailDto>;
}

export interface NoteDto {
  id: number;
  title: string;
  content: string;
  createdAt: string;
  updatedAt: string;
}

export type TaskEventType = "TASK" | "EVENT";

export interface TaskDateSummaryDto {
  date: string;
  count: number;
}

export interface TaskEventDto {
  id: number;
  type: TaskEventType;
  occursAt: string;
  description: string;
  durationMinutes: number | null;
}

async function backendPost<T>(path: string, body: unknown): Promise<T> {
  const url = new URL(path, config.backendUrl);

  const response = await fetch(url, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${config.feedApiToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    throw new Error(`Backend responded ${response.status} for ${url.pathname}`);
  }

  return response.json() as Promise<T>;
}

export async function fetchNotes(limit: number): Promise<NoteDto[]> {
  const response = await backendFetch("/api/notes/notes", { limit: String(limit) });
  return response.json() as Promise<NoteDto[]>;
}

export async function createNote(title: string, content: string): Promise<NoteDto> {
  return backendPost<NoteDto>("/api/notes/notes", { title, content });
}

export async function fetchTaskDates(limit: number): Promise<TaskDateSummaryDto[]> {
  const response = await backendFetch("/api/notes/tasks/dates", { limit: String(limit) });
  return response.json() as Promise<TaskDateSummaryDto[]>;
}

export async function fetchTasksForDate(date: string): Promise<TaskEventDto[]> {
  const response = await backendFetch("/api/notes/tasks", { date });
  return response.json() as Promise<TaskEventDto[]>;
}

export async function createTaskEvent(
  type: TaskEventType,
  occursAt: string,
  description: string,
  durationMinutes: number | null,
): Promise<TaskEventDto> {
  return backendPost<TaskEventDto>("/api/notes/tasks", { type, occursAt, description, durationMinutes });
}

export async function sendReply(chatId: number, text: string): Promise<ConversationMessageDto> {
  const url = new URL(`/api/messages/senders/${chatId}/reply`, config.backendUrl);

  const response = await fetch(url, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${config.feedApiToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ text }),
  });

  if (!response.ok) {
    throw new Error(`Backend responded ${response.status} for ${url.pathname}`);
  }

  return response.json() as Promise<ConversationMessageDto>;
}
