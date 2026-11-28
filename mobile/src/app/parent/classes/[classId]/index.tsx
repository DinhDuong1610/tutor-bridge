// CL01 Chi tiết lớp, parent side; PH03 "Xem lớp" opens it (#70 Q1). The role area's layout requires a PARENT session.
import { useLocalSearchParams, useRouter } from "expo-router";
import { ClassDetailScreen } from "@/features/classes/screens/ClassDetailScreen";

export default function ParentClassRoute() {
  const router = useRouter();
  const { classId } = useLocalSearchParams<{ classId: string }>();
  // Opened from a link there is nothing to go back to, so the parent home stands in, as on CL02.
  // TBD: the class list once it exists (#73 PR 3).
  const leave = () => (router.canGoBack() ? router.back() : router.replace("/parent"));
  return (
    <ClassDetailScreen
      classId={classId}
      role="PARENT"
      onBack={leave}
      onSessions={(id) => router.push({ pathname: "/parent/classes/[classId]/sessions", params: { classId: id } })}
      onOtherChild={leave}
      onManageStudents={() => router.push("/parent/account/students")}
    />
  );
}
