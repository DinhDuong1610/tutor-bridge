// CL02 Lịch lớp, parent side. The role area's layout already requires a PARENT session.
import { useLocalSearchParams, useRouter } from "expo-router";
import { ClassSessionsScreen } from "@/features/classes/screens/ClassSessionsScreen";

export default function ParentClassSessionsRoute() {
  const router = useRouter();
  const { classId } = useLocalSearchParams<{ classId: string }>();
  return <ClassSessionsScreen classId={classId} role="PARENT" onBack={() => router.back()} onOtherChild={() => router.back()} />;
}
