// PH07 Chi tiết gia sư công khai (#71 PR 2). Route of PH07.md; opened from a PH06 card with its run and need.
import { router, useLocalSearchParams } from "expo-router";
import { TutorDetailScreen } from "@/features/matching/screens/TutorDetailScreen";

export default function PH07Route() {
  const { tutorId, runId, needId } = useLocalSearchParams<{ tutorId: string; runId: string; needId: string }>();
  // Opened from a link there is nothing to go back to, so the need's PH06 stands in.
  const leave = () =>
    router.canGoBack() ? router.back() : router.replace({ pathname: "/parent/needs/[needId]/matches", params: { needId } });
  return (
    <TutorDetailScreen
      tutorId={tutorId}
      runId={runId}
      needId={needId}
      onBack={leave}
      onOtherChild={() => (router.canGoBack() ? router.back() : router.replace("/parent"))}
      onManageStudents={() => router.push("/parent/account/students")}
    />
  );
}
