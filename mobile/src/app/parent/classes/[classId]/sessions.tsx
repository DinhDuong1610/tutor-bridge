// CL02 Lịch lớp, parent side. The role area's layout already requires a PARENT session.
import { useLocalSearchParams, useRouter } from "expo-router";
import { ClassSessionsScreen } from "@/features/classes/screens/ClassSessionsScreen";

export default function ParentClassSessionsRoute() {
  const router = useRouter();
  const { classId } = useLocalSearchParams<{ classId: string }>();
  // Opened from a link there is nothing to go back to, so the parent home stands in (Đính, #260 review,
  // 28/11/2026). TBD: the class list once listClasses exists (#97).
  const leave = () => (router.canGoBack() ? router.back() : router.replace("/parent"));
  return (
    <ClassSessionsScreen
      classId={classId}
      role="PARENT"
      onBack={leave}
      onOtherChild={leave}
      onManageStudents={() => router.push("/parent/account/students")}
    />
  );
}
