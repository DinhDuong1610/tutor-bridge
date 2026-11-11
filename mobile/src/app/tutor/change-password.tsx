// AUTH06 Đổi mật khẩu
import { useRouter } from "expo-router";
import { ChangePasswordScreen } from "@/features/account/screens/ChangePasswordScreen";

export default function ChangePasswordRoute() {
  const router = useRouter();
  return <ChangePasswordScreen onCancel={() => (router.canGoBack() ? router.back() : router.replace("/"))} />;
}
