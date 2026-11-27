// PH01 Trang chủ phụ huynh (UC02, FR-03). Thiết kế: docs/design/ui/screens/PH01.md
// TB-14a: thanh chọn con (listStudents). #70: khối nhu cầu (Đính, 22/11/2026). Các khối đề nghị, buổi sắp tới,
// thông báo ẩn tới khi Issue của chúng bật (Đính, #14, 15/11/2026).
import type { ReactNode } from "react";
import { ScrollView, StyleSheet, Text, View } from "react-native";
import { useStudents } from "@/features/students/api";
import { HomeNeeds } from "@/features/needs/components/HomeNeeds";
import { studentChoices, useSelectedStudent } from "@/features/students/selection";
import { Button } from "@/shared/ui/Button";
import { Notice } from "@/shared/ui/Notice";
import { QueryScreen } from "@/shared/ui/QueryScreen";
import { StudentSwitcher } from "@/shared/ui/StudentSwitcher";
import { colors, spacing, typography } from "@/shared/ui/theme";

type Props = {
  onAddStudent: () => void;
  onManageStudents: () => void;
  /** Temporary way in to AUTH06 until the Account tab exists (#119); goes with the tab bar. */
  onSecurity: () => void;
  /** Temporary way in to PH03 until the "Tìm gia sư" tab exists (#205); goes with the tab bar (Huy, #70 PR 2 Q4). */
  onNeeds: () => void;
  /** PH04 for a new need of the child being viewed (PH01 "Con chưa có nhu cầu"). */
  onCreateNeed: () => void;
  /** PH04 for the child's draft (PH01 "Nhu cầu nháp", "Tiếp tục nhập"). */
  onContinueNeed: (needId: string) => void;
};

type StudentItem = { id: string; displayName: string; grade: number };

export function ParentHomeScreen({ onAddStudent, onManageStudents, onSecurity, onNeeds, onCreateNeed, onContinueNeed }: Props) {
  const students = useStudents();
  return (
    // The approved "Dữ liệu cũ" frame puts the warning below the child switcher, not above it.
    <QueryScreen
      title="Tổng quan"
      query={students}
      placesStaleNotice
      // Outside the data so it stays while the list loads or fails (Codex review of #192): the
      // parent can still change the password or sign out.
      footer={
        <View style={styles.footer}>
          <Button variant="secondary" title="Tìm gia sư" onPress={onNeeds} />
          <Button variant="secondary" title="Bảo mật" onPress={onSecurity} />
        </View>
      }
    >
      {(data, staleNotice) => (
        <Home
          // Assumption (Đính's note on #192): the switcher lists the pages loaded so far, which is the
          // first page of 50 children (STUDENTS_PAGE_LIMIT). A parent with more children reaches the
          // rest from PH02, which loads further pages.
          items={data.pages.flatMap((page) => page.items)}
          staleNotice={staleNotice}
          onAddStudent={onAddStudent}
          onManageStudents={onManageStudents}
          onCreateNeed={onCreateNeed}
          onContinueNeed={onContinueNeed}
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
  onCreateNeed,
  onContinueNeed,
}: Omit<Props, "onSecurity" | "onNeeds"> & { items: StudentItem[]; staleNotice: ReactNode }) {
  const { selected, select } = useSelectedStudent(items);

  if (!selected) {
    // No frame shows stale data without a child; the warning goes first, as on other screens.
    return (
      <ScrollView contentContainerStyle={styles.content}>
        {staleNotice}
        <Text style={styles.greeting}>Chào bạn</Text>
        <Notice tone="info" message="Tạo hồ sơ cho con trước. Mọi việc tìm gia sư, lịch và lớp đều gắn với một con." />
        <Button title="Thêm hồ sơ con" onPress={onAddStudent} />
      </ScrollView>
    );
  }

  const choices = studentChoices(items);

  return (
    // Edge to edge so the switcher bar spans the screen as in Figma 18:471; the rest keeps the padding.
    <ScrollView style={styles.edgeToEdge} contentContainerStyle={styles.content}>
      <StudentSwitcher choices={choices} selectedId={selected.id} onSelect={select} onManage={onManageStudents} />
      {staleNotice ? <View style={styles.padded}>{staleNotice}</View> : null}
      <View style={styles.padded}>
        <HomeNeeds key={selected.id} student={selected} onCreateNeed={onCreateNeed} onContinueNeed={onContinueNeed} />
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: { gap: spacing.lg, paddingBottom: spacing.md },
  // Undoes the horizontal padding of QueryScreen for this scroll view only.
  edgeToEdge: { marginHorizontal: -spacing.md },
  padded: { paddingHorizontal: spacing.md },
  greeting: { ...typography.heading, color: colors.text },
  footer: { gap: spacing.sm },
});
