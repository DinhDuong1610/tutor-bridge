// PH02 Hồ sơ các con: danh sách (UC02, FR-03). Thiết kế: docs/design/ui/screens/PH02.md
import type { components } from "@tutorbridge/api-client";
import { ScrollView, StyleSheet, Text, View } from "react-native";
import { Button } from "@/shared/ui/Button";
import { Notice } from "@/shared/ui/Notice";
import { QueryScreen } from "@/shared/ui/QueryScreen";
import { StickyActionBar } from "@/shared/ui/StickyActionBar";
import { colors, radius, spacing, typography } from "@/shared/ui/theme";
import { useStudents } from "../api";

type Student = components["schemas"]["Student"];

type Props = {
  onBack: () => void;
  onAdd: () => void;
  /** Id of the profile just saved. Its name is read from the list, never passed in the route. */
  savedId?: string;
};

const LEAD = "Mỗi thao tác tìm gia sư, lịch và lớp đều gắn với một con. Chỉ bạn quản lý các hồ sơ này.";

export function StudentsScreen({ onBack, onAdd, savedId }: Props) {
  const students = useStudents();

  return (
    // No `empty` on QueryScreen: the empty state keeps the lead and has its own action bar.
    <QueryScreen title="Hồ sơ các con" onBack={onBack} query={students}>
      {(data) => {
        const items = data.pages.flatMap((page) => page.items);
        const saved = savedId ? items.find((item) => item.id === savedId) : undefined;
        const header = (
          <>
            {saved ? <Notice tone="success" message={`Đã lưu hồ sơ ${saved.displayName}.`} /> : null}
            <Text style={styles.lead}>{LEAD}</Text>
          </>
        );

        if (items.length === 0) {
          return (
            <View style={styles.fill}>
              <ScrollView contentContainerStyle={styles.content}>
                {header}
                <Notice tone="info" message="Chưa có hồ sơ con nào. Tạo hồ sơ cho con để bắt đầu tìm gia sư." />
              </ScrollView>
              <StickyActionBar>
                <Button title="Thêm hồ sơ con" onPress={onAdd} />
              </StickyActionBar>
            </View>
          );
        }

        return (
          <ScrollView contentContainerStyle={styles.list}>
            {header}
            {items.map((student) => (
              <StudentCard key={student.id} student={student} />
            ))}
            {students.hasNextPage ? (
              <Button
                variant="secondary"
                title="Xem thêm"
                busy={students.isFetchingNextPage}
                onPress={() => void students.fetchNextPage()}
              />
            ) : null}
            <Button variant="secondary" title="Thêm hồ sơ con" onPress={onAdd} />
          </ScrollView>
        );
      }}
    </QueryScreen>
  );
}

/** Name up to 2 lines and goal up to 3 lines, then cut; the full text is in the edit screen (PH02). */
function StudentCard({ student }: { student: Student }) {
  const goal = student.shortGoal?.trim() ?? "";
  return (
    <View style={styles.card}>
      <Text style={styles.name} numberOfLines={2}>
        {student.displayName}
      </Text>
      <Text style={styles.muted}>Lớp {student.grade}</Text>
      <Text style={styles.body} numberOfLines={3}>
        {goal === "" ? "Chưa ghi mục tiêu." : `Mục tiêu: ${goal}`}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  // Lets the action bar reach the screen edges inside QueryScreen's padding.
  fill: { flex: 1, marginHorizontal: -spacing.md, marginBottom: -spacing.md },
  content: { gap: spacing.md, paddingHorizontal: spacing.md, paddingBottom: spacing.md },
  list: { gap: spacing.md, paddingBottom: spacing.md },
  lead: { ...typography.body, color: colors.textMuted },
  card: { gap: spacing.xs, padding: spacing.md, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, backgroundColor: colors.background },
  name: { ...typography.heading, color: colors.text },
  muted: { ...typography.caption, color: colors.textMuted },
  body: { ...typography.body, color: colors.text },
});
