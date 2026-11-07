// AUTH06 Bảo mật và quản lý phiên. The role area's layout already requires a session of this role.
import { useRouter } from "expo-router";
import { SessionsScreen } from "@/features/account/screens/SessionsScreen";

export default function SecurityRoute() {
  const router = useRouter();
  return <SessionsScreen onChangePassword={() => router.push("/tutor/change-password")} />;
}
