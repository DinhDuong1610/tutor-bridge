import { BeVietnamPro_400Regular } from "@expo-google-fonts/be-vietnam-pro/400Regular";
import { BeVietnamPro_500Medium } from "@expo-google-fonts/be-vietnam-pro/500Medium";
import { BeVietnamPro_600SemiBold } from "@expo-google-fonts/be-vietnam-pro/600SemiBold";
import { useFonts } from "expo-font";
import { Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { QueryProvider } from "@/shared/api/QueryProvider";
import { SessionProvider } from "@/shared/session/SessionProvider";
import { fonts } from "@/shared/ui/theme";

// Only the three approved weights are bundled (Đính, #95, 14/11/2026), loaded at runtime so Expo
// Go still works. Each key is its own family, named as in `theme.ts`.
const FONT_FILES = {
  [fonts.regular]: BeVietnamPro_400Regular,
  [fonts.medium]: BeVietnamPro_500Medium,
  [fonts.semiBold]: BeVietnamPro_600SemiBold,
};

// Root navigator. Theme values are tokens in `@/shared/ui/theme`, so there is no theme provider.
export default function RootLayout() {
  const [fontsLoaded, fontError] = useFonts(FONT_FILES);
  // Wait for the fonts so text does not jump; if they fail, show the app in the system font.
  if (!fontsLoaded && !fontError) return null;
  return (
    <SessionProvider>
      <QueryProvider>
        <Stack screenOptions={{ headerShown: false }} />
        <StatusBar style="auto" />
      </QueryProvider>
    </SessionProvider>
  );
}
