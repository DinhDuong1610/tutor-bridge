// AUTH05 Đặt lại mật khẩu. The email link opens tutorbridge://reset-password?token=...
import { useLocalSearchParams, useRouter } from "expo-router";
import { ResetPasswordScreen } from "@/features/account/screens/ResetPasswordScreen";

export default function ResetPasswordRoute() {
  const router = useRouter();
  const { token } = useLocalSearchParams<{ token?: string }>();
  return (
    <ResetPasswordScreen
      token={token}
      onGoToSignIn={() => router.replace("/sign-in")}
      onRequestNewLink={() => router.replace("/forgot-password")}
    />
  );
}
