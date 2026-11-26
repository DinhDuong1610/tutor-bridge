import type { UseQueryResult } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { ActivityIndicator, StyleSheet, Text, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { ApiError } from "@/shared/api/errors";
import { Button } from "./Button";
import { Notice } from "./Notice";
import { colors, spacing, typography } from "./theme";
import { TopAppBar } from "./TopAppBar";

/**
 * The part of a query result this frame reads. A `UseQueryResult` fits it; a screen that reads
 * several queries can combine them into one (GS01, `useQueries` with `combine`).
 */
export type QueryState<T> = Pick<UseQueryResult<T>, "isPending" | "isError" | "error"> & {
  data: T | undefined;
  refetch: () => unknown;
};

type Props<T> = {
  /** Shown as the screen heading. Put the screen code (PH02...) in a comment at the top of the screen file. */
  title: string;
  /** Shows a back button next to the title. */
  onBack?: () => void;
  query: QueryState<T>;
  /** Shown when `isEmpty(data)` is true, for example "Chưa có hồ sơ con nào." */
  empty?: { when: (data: T) => boolean; message: string; action?: ReactNode };
  /**
   * Set when the screen places the stale-data warning itself, for example PH01 below the child
   * switcher as in the approved frame. `children` then gets the warning (or null) as its second
   * argument. The empty state still shows the warning on top.
   */
  placesStaleNotice?: boolean;
  /**
   * Shown below the body in every state, including loading and a failed first read. For ways out
   * that must not depend on the data, such as PH01's temporary "Bảo mật" until the Account tab exists.
   */
  footer?: ReactNode;
  children: (data: T, staleNotice: ReactNode) => ReactNode;
};

/**
 * Frame for a screen that reads data. It covers the required states (docs/design/03-man-hinh.md,
 * FR-37): loading, empty, system error with retry, lost access, not found, and stale data after a
 * failed refresh. A 401 is handled by the session (M3), so nothing is shown for it here.
 */
export function QueryScreen<T>({ title, onBack, query, empty, placesStaleNotice, footer, children }: Props<T>) {
  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.container}>
        <TopAppBar title={title} onBack={onBack} />
        <Body query={query} empty={empty} placesStaleNotice={placesStaleNotice}>
          {children}
        </Body>
        {footer}
      </View>
    </SafeAreaView>
  );
}

function Body<T>({ query, empty, placesStaleNotice, children }: Omit<Props<T>, "title" | "onBack" | "footer">) {
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
  const staleNotice = query.isError ? (
    <Notice
      tone="warning"
      message="Không cập nhật được. Đang hiển thị dữ liệu đã tải trước đó."
      action={<Button variant="secondary" title="Thử lại" onPress={() => void query.refetch()} />}
    />
  ) : null;
  const isEmpty = empty !== undefined && empty.when(data);
  return (
    <>
      {placesStaleNotice && !isEmpty ? null : staleNotice}
      {isEmpty ? <Notice tone="info" message={empty.message} action={empty.action} /> : children(data, staleNotice)}
    </>
  );
}

function LoadError({ error, retry }: { error: Error | null; retry: () => void }) {
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
  center: { alignItems: "center", gap: spacing.sm, paddingVertical: spacing.xl },
  muted: { ...typography.body, color: colors.textMuted },
});
