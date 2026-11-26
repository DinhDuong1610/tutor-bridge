// GS03 Minh chứng và kết quả xác minh (TB-14). Thiết kế: docs/design/ui/screens/GS03.md
import { router } from "expo-router";
import { pickEvidenceFile } from "@/features/tutor-evidence/pickFile";
import { TutorEvidenceScreen } from "@/features/tutor-evidence/screens/TutorEvidenceScreen";

export default function TutorEvidenceRoute() {
  return (
    <TutorEvidenceScreen
      onBack={() => (router.canGoBack() ? router.back() : router.replace("/tutor"))}
      onEditProfile={() => router.push("/tutor/profile")}
      pickFile={pickEvidenceFile}
    />
  );
}
