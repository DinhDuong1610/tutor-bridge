// PH01 Trang chủ phụ huynh. Placeholder route until TB-14 builds the screen.
import { useRouter } from "expo-router";
import { Button } from "@/shared/ui/Button";
import { ComingSoon } from "@/shared/ui/ComingSoon";

export default function ParentHomeRoute() {
  const router = useRouter();
  return (
    <ComingSoon title="Trang chủ phụ huynh">
      {/* Temporary ways in for Demo 1 (Huy, 12/11/2026). TB-14 replaces them with PH01 and the tab bar. */}
      <Button variant="secondary" title="Hồ sơ các con" onPress={() => router.push("/parent/account/students")} />
      <Button variant="secondary" title="Bảo mật" onPress={() => router.push("/parent/security")} />
    </ComingSoon>
  );
}
