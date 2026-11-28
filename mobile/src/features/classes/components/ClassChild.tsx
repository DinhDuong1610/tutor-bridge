import { studentChoices, useSelectedStudent } from "@/features/students/selection";
import { StudentSwitcher } from "@/shared/ui/StudentSwitcher";

type Student = { id: string; displayName: string; grade: number };

/**
 * The class's child in the switcher of CL01 and CL02. A class belongs to one child, so choosing another keeps
 * the choice and leaves the class (Đính, #260 review, 28/11/2026). TBD: that child's class list once the
 * list screen exists (#73 PR 3). "Quản lý hồ sơ các con" opens PH02, not a way back.
 */
export function ClassChild({
  students,
  childId,
  onOtherChild,
  onManageStudents,
}: {
  students: Student[];
  childId: string;
  onOtherChild?: () => void;
  onManageStudents?: () => void;
}) {
  const { select } = useSelectedStudent(students);
  return (
    <StudentSwitcher
      choices={studentChoices(students)}
      selectedId={childId}
      onSelect={(studentId) => {
        if (studentId === childId) return;
        select(studentId);
        onOtherChild?.();
      }}
      onManage={() => onManageStudents?.()}
    />
  );
}
