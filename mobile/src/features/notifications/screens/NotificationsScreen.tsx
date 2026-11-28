// COM01 Hộp thông báo (UC15, FR-41, TX30). Thiết kế: docs/design/ui/screens/COM01.md
// TBD (Huy, 25/11/2026): mở thông báo chỉ đánh dấu đã đọc. Đi tới PH09, GS05, CL01 khi #72, #73 có màn hình.
import { formatDateTime } from "@tutorbridge/shared";
import { useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { Button } from "@/shared/ui/Button";
import { CommandStatus } from "@/shared/ui/CommandStatus";
import { Notice } from "@/shared/ui/Notice";
import { QueryScreen } from "@/shared/ui/QueryScreen";
import { colors, fonts, minTouchSize, radius, spacing, typography } from "@/shared/ui/theme";
import { useMarkNotificationRead, useNotifications, type Notification } from "../api";
import { eventTitle, type InboxRole } from "../eventTitle";

const EMPTY = "Chưa có thông báo. Phản hồi đề nghị và lớp đã chốt sẽ hiện ở đây.";
const NO_ACCESS = "Bạn không còn quyền xem nội dung này.";

export function NotificationsScreen({ role }: { role: InboxRole }) {
  const notifications = useNotifications();
  const markRead = useMarkNotificationRead();
  // The notification of the last mark, so a retry after an unknown outcome goes to the same one.
  const [markedId, setMarkedId] = useState<string | null>(null);

  function open(notification: Notification) {
    if (markRead.state === "unknown") {
      if (notification.id === markedId) {
        void markRead.retry();
        return;
      }
      // Another notification is another command: it must not reuse the key kept for the retry,
      // which the server would reject as reused with a different payload (409).
      markRead.reset();
    }
    setMarkedId(notification.id);
    void markRead.submit(notification.id);
  }

  // 403 or 404: not this member's notification any more (AC-UC15-03). Same words for both, so
  // nothing tells whether it exists (04-trang-thai-cau-chu.md).
  const lostAccess =
    markRead.state === "failed" && (markRead.error?.kind === "forbidden" || markRead.error?.kind === "not-found");

  return (
    <QueryScreen
      title="Thông báo"
      query={notifications}
      empty={{ when: (data) => data.pages.every((page) => page.items.length === 0), message: EMPTY }}
    >
      {(data) => {
        const items = data.pages.flatMap((page) => page.items);
        return (
          <ScrollView contentContainerStyle={styles.list}>
            {lostAccess ? (
              <Notice tone="danger" message={NO_ACCESS} />
            ) : markRead.state === "confirmed" ? null : (
              // Sending, unknown outcome with a retry on the same key, or another failure.
              <CommandStatus command={markRead} confirmedMessage="" />
            )}
            {items.map((item) => (
              <NotificationItem
                key={item.id}
                notification={item}
                title={eventTitle(role, item.eventType)}
                disabled={markRead.state === "sending"}
                onOpen={() => open(item)}
              />
            ))}
            {notifications.hasNextPage ? (
              <>
                <Text style={styles.muted}>Đang hiện {items.length} thông báo mới nhất.</Text>
                <Button
                  variant="secondary"
                  title="Xem thêm"
                  busy={notifications.isFetchingNextPage}
                  onPress={() => void notifications.fetchNextPage()}
                />
              </>
            ) : null}
          </ScrollView>
        );
      }}
    </QueryScreen>
  );
}

type ItemProps = { notification: Notification; title: string; disabled: boolean; onOpen: () => void };

/**
 * Figma `NotificationItem`: unread has the tinted background, a dot, a heavier title and the words
 * "Chưa đọc", so the state never depends on colour alone. Only an unread item can be pressed until
 * opening leads to PH09, GS05 or CL01.
 */
function NotificationItem({ notification, title, disabled, onOpen }: ItemProps) {
  const unread = !notification.read;
  const status = `${unread ? "Chưa đọc" : "Đã đọc"}, ${formatDateTime(notification.createdAt)}`;
  const content = (
    <>
      {unread ? <View style={styles.dot} /> : null}
      <View style={styles.text}>
        <Text style={[styles.title, unread && styles.titleUnread]}>{title}</Text>
        <Text style={styles.muted}>{status}</Text>
      </View>
    </>
  );

  if (!unread) {
    return (
      <View accessible accessibilityLabel={`${title}, ${status}`} style={styles.item}>
        {content}
      </View>
    );
  }
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`${title}, ${status}`}
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={onOpen}
      style={({ pressed }) => [styles.item, styles.itemUnread, pressed && styles.pressed]}
    >
      {content}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  list: { gap: spacing.sm, paddingBottom: spacing.md },
  item: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: spacing.sm,
    minHeight: minTouchSize,
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.md,
    borderRadius: radius.sm,
  },
  itemUnread: { backgroundColor: colors.infoSurface },
  pressed: { opacity: 0.6 },
  // Centred on the first line of the title (body line height 24).
  dot: { width: spacing.sm, height: spacing.sm, marginTop: spacing.sm, borderRadius: radius.full, backgroundColor: colors.primary },
  text: { flex: 1, gap: spacing.xs },
  title: { ...typography.body, color: colors.text },
  titleUnread: { fontFamily: fonts.semiBold },
  muted: { ...typography.caption, color: colors.textMuted },
});
