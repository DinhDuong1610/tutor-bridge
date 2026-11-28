import { useInfiniteQuery, useQueryClient, type InfiniteData } from "@tanstack/react-query";
import type { components } from "@tutorbridge/api-client";
import { useEffect } from "react";
import { api } from "@/shared/api/client";
import { useCommand } from "@/shared/api/command";
import { call } from "@/shared/api/errors";

export type Notification = components["schemas"]["Notification"];
type NotificationPage = components["schemas"]["NotificationPage"];

export const NOTIFICATIONS_KEY = ["notifications"] as const;
/** maxItems of NotificationPage; COM01 "Dữ liệu xấu nhất" shows "Đang hiện 50 thông báo mới nhất." */
export const NOTIFICATIONS_PAGE_LIMIT = 50;

/** The signed-in member's inbox, newest first. The server takes the recipient from the session (TX30). */
export function useNotifications() {
  return useInfiniteQuery({
    queryKey: NOTIFICATIONS_KEY,
    initialPageParam: null as string | null,
    queryFn: ({ pageParam }) =>
      call(
        api.GET("/me/notifications", {
          params: { query: { limit: NOTIFICATIONS_PAGE_LIMIT, ...(pageParam ? { cursor: pageParam } : {}) } },
        }),
      ),
    getNextPageParam: (page) => page.nextCursor,
  });
}

/**
 * markNotificationRead. The notification id travels in the input, so a retry after an unknown
 * outcome resends the same key for the same notification (INV-20). The list shows "Đã đọc" only
 * from the notification the server returns, never before it confirms (FR-37).
 */
export function useMarkNotificationRead() {
  const queryClient = useQueryClient();
  const command = useCommand((notificationId: string, key: string) =>
    call(
      api.POST("/notifications/{notificationId}/read", {
        params: { path: { notificationId }, header: { "Idempotency-Key": key } },
      }),
    ),
  );
  const marked = command.state === "confirmed" ? command.result : undefined;
  useEffect(() => {
    if (marked) {
      // Replace the item in the pages already loaded, so the list keeps its place and "Xem thêm".
      queryClient.setQueryData<InfiniteData<NotificationPage, string | null>>(NOTIFICATIONS_KEY, (data) =>
        data === undefined
          ? data
          : {
              ...data,
              pages: data.pages.map((page) => ({
                ...page,
                items: page.items.map((item) => (item.id === marked.id ? marked : item)),
              })),
            },
      );
    }
  }, [marked, queryClient]);
  return command;
}
