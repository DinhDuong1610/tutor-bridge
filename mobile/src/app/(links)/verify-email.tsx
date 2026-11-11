// AUTH02 Xác minh email. The email link opens tutorbridge://verify-email?token=...
import { useLocalSearchParams, useRouter } from "expo-router";
import { VerifyEmailScreen } from "@/features/account/screens/VerifyEmailScreen";

export default function VerifyEmailRoute() {
  const router = useRouter();
  const { token } = useLocalSearchParams<{ token?: string }>();
  return <VerifyEmailScreen token={token} onGoToSignIn={() => router.replace("/sign-in")} />;
}
