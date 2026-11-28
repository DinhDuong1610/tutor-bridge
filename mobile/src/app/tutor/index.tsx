// GS01 Trang chủ gia sư. The role area's layout already requires a TUTOR session.
import { useRouter } from "expo-router";
import { TutorHomeScreen } from "@/features/tutor-home/screens/TutorHomeScreen";

// GS02, GS03 and GS04 are placeholders until their Issues build them (TB-14 Q6).
const ROUTES = { profile: "/tutor/profile", evidence: "/tutor/evidence", calendar: "/tutor/calendar" } as const;

export default function TutorHomeRoute() {
  const router = useRouter();
  return (
    <TutorHomeScreen
      onOpen={(target) => router.push(ROUTES[target])}
      onSecurity={() => router.push("/tutor/security")}
      onNotifications={() => router.push("/tutor/notifications")}
    />
  );
}
