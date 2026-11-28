import { studentChoices, useSelectedStudent } from "@/features/students/selection";
import { StudentSwitcher } from "@/shared/ui/StudentSwitcher";

type Child = { id: string; displayName: string; grade: number };

/** The need's child in the switcher. Choosing another child leaves the need; "Quản lý" opens PH02. */
export function ChildOfNeed({
  students,
  childId,
  onOtherChild,
  onManageStudents,
}: {
  students: Child[];
  childId: string;
  onOtherChild: () => void;
  onManageStudents: () => void;
}) {
  const { select } = useSelectedStudent(students);
  return (
    <StudentSwitcher
      choices={studentChoices(students)}
      selectedId={childId}
      onSelect={(studentId) => {
        if (studentId === childId) return;
        select(studentId);
        onOtherChild();
      }}
      onManage={onManageStudents}
    />
  );
}
