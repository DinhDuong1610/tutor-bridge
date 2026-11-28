// CL01 Chi tiết lớp, tutor side. The role area's layout requires a TUTOR session.
import { useLocalSearchParams, useRouter } from "expo-router";
import { ClassDetailScreen } from "@/features/classes/screens/ClassDetailScreen";

export default function TutorClassRoute() {
  const router = useRouter();
  const { classId } = useLocalSearchParams<{ classId: string }>();
  return (
    <ClassDetailScreen
      classId={classId}
      role="TUTOR"
      onBack={() => (router.canGoBack() ? router.back() : router.replace("/tutor"))}
      onSessions={(id) => router.push({ pathname: "/tutor/classes/[classId]/sessions", params: { classId: id } })}
    />
  );
}
