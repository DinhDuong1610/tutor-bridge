// AUTH03 Đăng nhập
import { useRouter } from "expo-router";
import { SignInScreen } from "@/features/account/screens/SignInScreen";

export default function SignInRoute() {
  const router = useRouter();
  return (
    <SignInScreen
      onGoToRegister={() => router.push("/register")}
      onForgotPassword={() => router.push("/forgot-password")}
      onResendVerification={() => router.push("/verify-email")}
    />
  );
}
