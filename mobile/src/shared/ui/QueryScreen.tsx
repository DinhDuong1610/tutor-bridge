import type { UseQueryResult } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { ActivityIndicator, StyleSheet, Text, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { ApiError } from "@/shared/api/errors";
import { Button } from "./Button";
import { Notice } from "./Notice";
import { colors, spacing, typography } from "./theme";

type Props<T> = {
  /** Shown as the screen heading. Put the screen code (PH02...) in a comment at the top of the screen file. */
  title: string;
  query: UseQueryResult<T>;
  /** Shown when `isEmpty(data)` is true, for example "Chưa có hồ sơ con nào." */
  empty?: { when: (data: T) => boolean; message: string; action?: ReactNode };
  children: (data: T) => ReactNode;
};

/**
 * Frame for a screen that reads data. It covers the required states (docs/design/03-man-hinh.md,
 * FR-37): loading, empty, system error with retry, lost access, not found, and stale data after a
 * failed refresh. A 401 is handled by the session (M3), so nothing is shown for it here.
 */
export function QueryScreen<T>({ title, query, empty, children }: Props<T>) {
  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.container}>
        <Text accessibilityRole="header" style={styles.title}>
          {title}
        </Text>
        <Body query={query} empty={empty}>
          {children}
        </Body>
      </View>
    </SafeAreaView>
  );
}

function Body<T>({ query, empty, children }: Omit<Props<T>, "title">) {
  if (query.isPending) {
    return (
      <View style={styles.center} accessibilityLiveRegion="polite">
        <ActivityIndicator color={colors.primary} />
        <Text style={styles.muted}>Đang tải…</Text>
      </View>
    );
  }
  if (query.isError && query.data === undefined) {
    return <LoadError error={query.error} retry={() => void query.refetch()} />;
  }
  const data = query.data as T;
  return (
    <>
      {query.isError ? (
        <Notice
          tone="warning"
          message="Không cập nhật được. Đang hiển thị dữ liệu đã tải trước đó."
          action={<Button variant="secondary" title="Thử lại" onPress={() => void query.refetch()} />}
        />
      ) : null}
      {empty && empty.when(data) ? <Notice tone="info" message={empty.message} action={empty.action} /> : children(data)}
    </>
  );
}

function LoadError({ error, retry }: { error: Error; retry: () => void }) {
  if (error instanceof ApiError) {
    switch (error.kind) {
      case "session-expired":
        return null;
      case "forbidden":
        return <Notice tone="danger" message="Bạn không còn quyền xem nội dung này." />;
      case "not-found":
        return <Notice tone="danger" message="Không tìm thấy nội dung này. Có thể nó đã bị xóa hoặc bạn không có quyền xem." />;
      case "rate-limited":
        return <Notice tone="warning" message="Bạn thao tác quá nhanh. Vui lòng đợi một lúc rồi thử lại." action={<Button variant="secondary" title="Thử lại" onPress={retry} />} />;
      default:
        break;
    }
  }
  return (
    <Notice
      tone="danger"
      message="Không tải được dữ liệu. Kiểm tra kết nối rồi thử lại."
      action={<Button variant="secondary" title="Thử lại" onPress={retry} />}
    />
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  container: { flex: 1, padding: spacing.md, gap: spacing.md },
  title: { ...typography.title, color: colors.text },
  center: { alignItems: "center", gap: spacing.sm, paddingVertical: spacing.xl },
  muted: { ...typography.body, color: colors.textMuted },
});
