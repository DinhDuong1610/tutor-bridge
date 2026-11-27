import { StyleSheet, Text, View } from "react-native";
import { useStudentCalendar } from "@/features/calendar/api";
import { Button } from "@/shared/ui/Button";
import { QueryBody } from "@/shared/ui/QueryScreen";
import { colors, spacing, typography } from "@/shared/ui/theme";
import { useNeeds } from "../api";
import { hasFreeTime } from "../freeTime";
import { homeDraftCard, type Need } from "../summary";
import { NeedCardView } from "./NeedCardView";

type Props = {
  student: { id: string; displayName: string };
  /** PH04 for a new need of this child. */
  onCreateNeed: () => void;
  /** PH04 for this draft. */
  onContinueNeed: (needId: string) => void;
};

/**
 * PH01's need block (Đính, 22/11/2026: made in #70): the frame "Con chưa có nhu cầu" when the child has no need
 * yet, or the draft under "Việc đang chờ" (PH01 "Nhu cầu nháp"). A confirmed or closed need shows nothing here;
 * offers, sessions and notifications stay hidden until their Issue turns them on (Đính, #14, 15/11/2026).
 * Keyed by the child, so changing child reloads the block for that child (AC-UC02-01).
 */
export function HomeNeeds({ student, onCreateNeed, onContinueNeed }: Props) {
  const needs = useNeeds(student.id);
  const calendar = useStudentCalendar(student.id);
  const freeTime = calendar.data === undefined ? undefined : hasFreeTime(calendar.data?.data.windows ?? []);
  return (
    <QueryBody query={needs}>
      {(data) => {
        const loaded: Need[] = data.pages.flatMap((page) => page.items);
        if (loaded.length === 0) {
          return (
            <View style={styles.block}>
              <Text accessibilityRole="header" style={styles.heading}>
                {`${student.displayName} chưa có nhu cầu học kèm.`}
              </Text>
              <Text style={styles.body}>
                Nhập mục tiêu, ngân sách mỗi buổi và giờ rảnh của con. Hệ thống lọc gia sư đáp ứng đủ điều kiện để bạn chọn.
              </Text>
              <Button title="Tạo nhu cầu học kèm" onPress={onCreateNeed} />
            </View>
          );
        }
        // A child has at most one need that is not closed (#16 Q4).
        const draft = loaded.find((need) => need.status === "DRAFT");
        if (!draft) return null;
        return (
          <View style={styles.block}>
            <Text accessibilityRole="header" style={styles.heading}>
              Việc đang chờ
            </Text>
            <NeedCardView card={homeDraftCard(draft, freeTime)} onAction={(action) => action.kind === "edit" && onContinueNeed(action.needId)} />
          </View>
        );
      }}
    </QueryBody>
  );
}

const styles = StyleSheet.create({
  block: { gap: spacing.md },
  heading: { ...typography.heading, color: colors.text },
  body: { ...typography.body, color: colors.textMuted },
});
