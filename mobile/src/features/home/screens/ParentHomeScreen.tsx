// PH01 Trang chủ phụ huynh (UC02, FR-03). Thiết kế: docs/design/ui/screens/PH01.md
// TB-14a: chỉ phần đọc được hôm nay (listStudents). Các khối nhu cầu, đề nghị, buổi sắp tới, thông báo
// ẩn tới khi Issue backend của chúng merge (Đính, #14, 15/11/2026).
import type { ReactNode } from "react";
import { ScrollView, StyleSheet, Text } from "react-native";
import { useStudents } from "@/features/students/api";
import { useSelectedStudent } from "@/features/students/selection";
import { Button } from "@/shared/ui/Button";
import { Notice } from "@/shared/ui/Notice";
import { QueryScreen } from "@/shared/ui/QueryScreen";
import { StudentSwitcher, type StudentChoice } from "@/shared/ui/StudentSwitcher";
import { colors, spacing, typography } from "@/shared/ui/theme";

type Props = {
  onAddStudent: () => void;
  onManageStudents: () => void;
  /** Temporary way in to AUTH06 until the Account tab exists (#119); goes with the tab bar. */
  onSecurity: () => void;
};

type StudentItem = { id: string; displayName: string; grade: number };

export function ParentHomeScreen({ onAddStudent, onManageStudents, onSecurity }: Props) {
  const students = useStudents();
  return (
    // The approved "Dữ liệu cũ" frame puts the warning below the child switcher, not above it.
    <QueryScreen title="Tổng quan" query={students} placesStaleNotice>
      {(data, staleNotice) => (
        <Home
          items={data.pages.flatMap((page) => page.items)}
          staleNotice={staleNotice}
          onAddStudent={onAddStudent}
          onManageStudents={onManageStudents}
          onSecurity={onSecurity}
        />
      )}
    </QueryScreen>
  );
}

function Home({
  items,
  staleNotice,
  onAddStudent,
  onManageStudents,
  onSecurity,
}: Props & { items: StudentItem[]; staleNotice: ReactNode }) {
  const { selected, select } = useSelectedStudent(items);

  if (!selected) {
    // No frame shows stale data without a child; the warning goes first, as on other screens.
    return (
      <ScrollView contentContainerStyle={styles.content}>
        {staleNotice}
        <Text style={styles.greeting}>Chào bạn</Text>
        <Notice tone="info" message="Tạo hồ sơ cho con trước. Mọi việc tìm gia sư, lịch và lớp đều gắn với một con." />
        <Button title="Thêm hồ sơ con" onPress={onAddStudent} />
        <Button variant="secondary" title="Bảo mật" onPress={onSecurity} />
      </ScrollView>
    );
  }

  const choices: StudentChoice[] = items.map((student) => ({
    id: student.id,
    name: student.displayName,
    label: `${student.displayName}, lớp ${student.grade}`,
    // The approved sheet also counts classes and offers; those APIs are not merged yet (TBD in handoff).
    detail: `Lớp ${student.grade}.`,
  }));

  return (
    <ScrollView contentContainerStyle={styles.content}>
      <StudentSwitcher choices={choices} selectedId={selected.id} onSelect={select} onManage={onManageStudents} />
      {staleNotice}
      <Button variant="secondary" title="Bảo mật" onPress={onSecurity} />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: { gap: spacing.lg, paddingBottom: spacing.md },
  greeting: { ...typography.heading, color: colors.text },
});
