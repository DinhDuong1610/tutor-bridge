// AUTH01 Đăng ký
import { useRouter } from "expo-router";
import { RegisterScreen } from "@/features/account/screens/RegisterScreen";

export default function RegisterRoute() {
  const router = useRouter();
  return (
    <RegisterScreen
      termsVersion={process.env.EXPO_PUBLIC_TERMS_VERSION}
      onGoToSignIn={() => (router.canGoBack() ? router.back() : router.replace("/sign-in"))}
      onResendVerification={() => router.push("/verify-email")}
    />
  );
}
