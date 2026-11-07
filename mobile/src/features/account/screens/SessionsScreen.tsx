// AUTH06 Bảo mật và quản lý phiên: xem phiên, thu hồi phiên, đăng xuất (UC01, FR-02, FR-39).
// Đổi mật khẩu ở ChangePasswordScreen.
import { formatDateTime } from "@tutorbridge/shared";
import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { ScrollView, StyleSheet, Text, View } from "react-native";
import type { components } from "@tutorbridge/api-client";
import { useSession } from "@/shared/session/SessionProvider";
import { Button } from "@/shared/ui/Button";
import { CommandStatus } from "@/shared/ui/CommandStatus";
import { Notice } from "@/shared/ui/Notice";
import { QueryScreen } from "@/shared/ui/QueryScreen";
import { colors, radius, spacing, typography } from "@/shared/ui/theme";
import { OWN_SESSIONS_KEY, useLogout, useOwnSessions, useRevokeSession } from "../api";

type SessionMetadata = components["schemas"]["SessionMetadata"];

export function SessionsScreen({ onChangePassword }: { onChangePassword: () => void }) {
  const sessions = useOwnSessions();

  return (
    // No `empty` on QueryScreen: it would replace the content and hide "Đổi mật khẩu".
    <QueryScreen title="Bảo mật và phiên đăng nhập" query={sessions}>
      {(data) => (
        <ScrollView contentContainerStyle={styles.list}>
          <Button variant="secondary" title="Đổi mật khẩu" onPress={onChangePassword} />
          {data.pages.every((page) => page.items.length === 0) ? (
            <Notice tone="info" message="Không có phiên đăng nhập nào." />
          ) : null}
          {data.pages
            .flatMap((page) => page.items)
            .map((item) => (item.current ? <CurrentSession key={item.id} session={item} /> : <OtherSession key={item.id} session={item} />))}
          {sessions.hasNextPage ? (
            <Button
              variant="secondary"
              title="Xem thêm phiên"
              busy={sessions.isFetchingNextPage}
              onPress={() => void sessions.fetchNextPage()}
            />
          ) : null}
        </ScrollView>
      )}
    </QueryScreen>
  );
}

function Details({ session }: { session: SessionMetadata }) {
  return (
    <>
      <Text style={styles.device}>{session.deviceLabel}</Text>
      <Text style={styles.muted}>Hoạt động gần nhất: {formatDateTime(session.lastSeenAt)}</Text>
      <Text style={styles.muted}>Hết hạn: {formatDateTime(session.expiresAt)}</Text>
    </>
  );
}

/** This device. Signing out calls the API first, then forgets the session here. */
function CurrentSession({ session }: { session: SessionMetadata }) {
  const auth = useSession();
  const logout = useLogout();

  const { signOut } = auth;
  // Forget the session here only after the server confirms, so a lost request is not reported as done.
  useEffect(() => {
    if (logout.state === "confirmed") void signOut();
  }, [logout.state, signOut]);

  return (
    <View style={styles.card} accessibilityLabel={`${session.deviceLabel}, phiên hiện tại`}>
      <Text style={styles.badge}>Máy này (phiên hiện tại)</Text>
      <Details session={session} />
      <CommandStatus command={logout} confirmedMessage="Đã đăng xuất." />
      {logout.state === "unknown" ? (
        // Anyone must be able to leave a shared device (handoff TB-10, Q12). Truthful: only this device.
        <Notice
          tone="info"
          message="Nếu không kết nối được, bạn vẫn có thể đăng xuất khỏi máy này. Phiên trên máy chủ sẽ tự hết hạn, hoặc thu hồi nó từ máy khác."
          action={<Button variant="secondary" title="Đăng xuất khỏi máy này" onPress={() => void signOut()} />}
        />
      ) : null}
      <Button title="Đăng xuất" busy={logout.state === "sending"} onPress={() => void logout.submit(undefined)} />
    </View>
  );
}

/** Another device. Revoking asks for confirmation first (AUTH06), then refreshes the list. */
function OtherSession({ session }: { session: SessionMetadata }) {
  const queryClient = useQueryClient();
  const revoke = useRevokeSession();
  const [confirming, setConfirming] = useState(false);

  const refresh = () => queryClient.invalidateQueries({ queryKey: OWN_SESSIONS_KEY });

  async function confirmRevoke() {
    setConfirming(false);
    await revoke.submit(session.id);
    await refresh();
  }

  async function retryRevoke() {
    await revoke.retry();
    await refresh();
  }

  return (
    <View style={styles.card} accessibilityLabel={session.deviceLabel}>
      {session.revoked ? <Text style={styles.badge}>Đã thu hồi</Text> : null}
      <Details session={session} />
      <CommandStatus command={{ ...revoke, retry: retryRevoke }} confirmedMessage="Đã thu hồi phiên." />
      {session.revoked || revoke.state === "confirmed" ? null : confirming ? (
        <Notice
          tone="warning"
          message={`Thu hồi phiên trên "${session.deviceLabel}"? Máy đó sẽ phải đăng nhập lại.`}
          action={
            <View style={styles.actions}>
              <Button title="Xác nhận thu hồi" onPress={() => void confirmRevoke()} />
              <Button variant="secondary" title="Hủy" onPress={() => setConfirming(false)} />
            </View>
          }
        />
      ) : (
        <Button
          variant="secondary"
          title="Thu hồi phiên"
          busy={revoke.state === "sending"}
          onPress={() => setConfirming(true)}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  list: { gap: spacing.md, paddingBottom: spacing.lg },
  card: { gap: spacing.xs, padding: spacing.md, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, backgroundColor: colors.surface },
  actions: { gap: spacing.sm },
  badge: { ...typography.label, color: colors.text },
  device: { ...typography.body, color: colors.text },
  muted: { ...typography.caption, color: colors.textMuted },
});
