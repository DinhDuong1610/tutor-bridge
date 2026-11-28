// PH06 Kết quả và so sánh gia sư (#71). Route of #70 Q1 (Đính, 26/11/2026).
import { router, useLocalSearchParams } from "expo-router";
import { MatchesScreen } from "@/features/matching/screens/MatchesScreen";

export default function PH06Route() {
  const { needId } = useLocalSearchParams<{ needId: string }>();
  // Opened from a link there is nothing to go back to, so PH03 stands in.
  const leave = () => (router.canGoBack() ? router.back() : router.replace("/parent/needs"));
  return (
    <MatchesScreen
      needId={needId}
      onBack={leave}
      onEdit={(id) => router.push({ pathname: "/parent/needs/[needId]/edit", params: { needId: id } })}
      onOpenTutor={(tutorId, runId) => router.push({ pathname: "/parent/tutors/[tutorId]", params: { tutorId, runId } })}
      onOtherChild={leave}
      onManageStudents={() => router.push("/parent/account/students")}
    />
  );
}
