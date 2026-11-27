// PH04 Nhập nhu cầu học kèm, new need (#70 PR 3a). Route of #70 Q1 (Đính, 26/11/2026).
import { router } from "expo-router";
import { NewNeedScreen } from "@/features/needs/screens/NeedFormScreen";

export default function PH04NewRoute() {
  return (
    <NewNeedScreen
      onBack={() => (router.canGoBack() ? router.back() : router.replace("/parent/needs"))}
      // Once saved, the draft has an id: back goes to PH03, not to an empty new form.
      onReview={(needId) => router.replace({ pathname: "/parent/needs/[needId]/review", params: { needId } })}
    />
  );
}
