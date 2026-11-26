// GS02 Hồ sơ chuyên môn và học phí (TB-14). Thiết kế: docs/design/ui/screens/GS02.md
import { router } from "expo-router";
import { TutorProfileScreen } from "@/features/tutor-profile/screens/TutorProfileScreen";

export default function TutorProfileRoute() {
  return (
    <TutorProfileScreen
      onBack={() => (router.canGoBack() ? router.back() : router.replace("/tutor"))}
      onNext={() => router.push("/tutor/evidence")}
    />
  );
}
