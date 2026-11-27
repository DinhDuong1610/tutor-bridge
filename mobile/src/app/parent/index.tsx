// PH01 Trang chủ phụ huynh. The role area's layout already requires a PARENT session.
import { useRouter } from "expo-router";
import { ParentHomeScreen } from "@/features/home/screens/ParentHomeScreen";

export default function ParentHomeRoute() {
  const router = useRouter();
  return (
    <ParentHomeScreen
      onAddStudent={() => router.push("/parent/account/students/new")}
      onManageStudents={() => router.push("/parent/account/students")}
      onSecurity={() => router.push("/parent/security")}
      onNeeds={() => router.push("/parent/needs")}
      onCreateNeed={() => router.push("/parent/needs/new")}
      onContinueNeed={(needId) => router.push({ pathname: "/parent/needs/[needId]/edit", params: { needId } })}
    />
  );
}
