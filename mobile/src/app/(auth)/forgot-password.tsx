// AUTH04 Yêu cầu khôi phục mật khẩu
import { useRouter } from "expo-router";
import { RecoveryScreen } from "@/features/account/screens/RecoveryScreen";

export default function ForgotPasswordRoute() {
  const router = useRouter();
  return <RecoveryScreen onGoToSignIn={() => (router.canGoBack() ? router.back() : router.replace("/sign-in"))} />;
}
