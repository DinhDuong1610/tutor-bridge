// CL02 Lịch lớp, tutor side. The role area's layout already requires a TUTOR session.
import { useLocalSearchParams, useRouter } from "expo-router";
import { ClassSessionsScreen } from "@/features/classes/screens/ClassSessionsScreen";

export default function TutorClassSessionsRoute() {
  const router = useRouter();
  const { classId } = useLocalSearchParams<{ classId: string }>();
  return <ClassSessionsScreen classId={classId} role="TUTOR" onBack={() => router.back()} />;
}
