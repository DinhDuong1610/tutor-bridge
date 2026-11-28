// GS01 Trang chủ gia sư (UC03, UC07; FR-05). Thiết kế: docs/design/ui/screens/GS01.md, v2 (Đính, 17/11/2026).
// TB-14b: chỉ phần có API đã merge (hồ sơ, xác minh, lịch rảnh). Khối đề nghị, buổi sắp tới, thông báo
// và thanh tab ẩn tới khi Issue của chúng merge (Đính, #14, 15/11/2026, câu 1 và 2).
import { ScrollView, StyleSheet, Text, View } from "react-native";
import { Button } from "@/shared/ui/Button";
import { Icon } from "@/shared/ui/Icon";
import { QueryScreen } from "@/shared/ui/QueryScreen";
import { colors, spacing, typography } from "@/shared/ui/theme";
import { useTutorHome } from "../api";
import { TaskCard } from "../components/TaskCard";
import { deriveTutorHome, type TaskTarget, type Visibility } from "../derive";

type Props = {
  onOpen: (target: TaskTarget) => void;
  /** Temporary way in to AUTH06 until the Account tab exists (#205), as on PH01. */
  onSecurity: () => void;
  /** Temporary way in to COM01 until the tab bar exists (#205), as on PH01. */
  onNotifications: () => void;
  /** For tests; the screen uses the device clock otherwise. */
  now?: () => Date;
};

export function TutorHomeScreen({ onOpen, onSecurity, onNotifications, now = () => new Date() }: Props) {
  const home = useTutorHome();
  return (
    <QueryScreen
      title="Tổng quan"
      query={home}
      footer={
        <View style={styles.footer}>
          <Button variant="secondary" title="Thông báo" onPress={onNotifications} />
          <Button variant="secondary" title="Bảo mật" onPress={onSecurity} />
        </View>
      }
    >
      {(data) => {
        const { tasks, visibility, visibilityFirst } = deriveTutorHome(data, now());
        const listing = <Listing visibility={visibility} />;
        return (
          <ScrollView contentContainerStyle={styles.content}>
            {/* No API returns the tutor's own name yet (TB-14 Q2), so the greeting is the one PH01 uses. */}
            <Text style={styles.greeting}>Chào bạn</Text>
            {visibilityFirst ? listing : null}
            {tasks.length > 0 ? (
              <View style={styles.section}>
                <Text accessibilityRole="header" style={styles.heading}>
                  Việc cần làm
                </Text>
                {tasks.map((task) => (
                  <TaskCard key={`${task.kind}-${task.title}`} task={task} onOpen={onOpen} />
                ))}
              </View>
            ) : null}
            {visibilityFirst ? null : listing}
          </ScrollView>
        );
      }}
    </QueryScreen>
  );
}

/** Block "Hồ sơ khi phụ huynh tìm": always says why parents cannot find the tutor yet (GS01, Phải thấy). */
function Listing({ visibility }: { visibility: Visibility }) {
  return (
    <View style={styles.section}>
      <Text accessibilityRole="header" style={styles.heading}>
        Hồ sơ khi phụ huynh tìm
      </Text>
      {visibility.kind === "verified" ? (
        <View style={styles.verified}>
          <Icon name="check_circle" color={colors.success} size={16} />
          <Text style={styles.verifiedText}>{`Đã xác minh: ${visibility.scope}`}</Text>
        </View>
      ) : null}
      {/* "Chưa xuất hiện" reads as a missing step, so it is muted; the other two state facts. */}
      <Text style={visibility.kind === "hidden" ? styles.muted : styles.text}>{visibility.message}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  content: { gap: spacing.lg, paddingBottom: spacing.md },
  footer: { gap: spacing.sm },
  greeting: { ...typography.heading, color: colors.text },
  section: { gap: spacing.sm },
  heading: { ...typography.bodyStrong, color: colors.text },
  verified: { flexDirection: "row", alignItems: "center", gap: spacing.xs },
  verifiedText: { ...typography.label, color: colors.success, flexShrink: 1 },
  text: { ...typography.body, color: colors.text },
  muted: { ...typography.body, color: colors.textMuted },
});
