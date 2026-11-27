// GS04 Lịch rảnh và ngoại lệ (#69). Thiết kế: docs/design/ui/screens/GS04.md
import { router } from "expo-router";
import { TutorCalendarScreen } from "@/features/tutor-calendar/screens/TutorCalendarScreen";

export default function TutorCalendarRoute() {
  return <TutorCalendarScreen onBack={() => (router.canGoBack() ? router.back() : router.replace("/tutor"))} />;
}
