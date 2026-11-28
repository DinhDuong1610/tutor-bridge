// PH04 Nhập nhu cầu học kèm, editing a need (#70 PR 3a). Route of #70 Q1 (Đính, 26/11/2026).
import { router, useLocalSearchParams } from "expo-router";
import { EditNeedScreen } from "@/features/needs/screens/NeedFormScreen";

export default function PH04EditRoute() {
  const { needId } = useLocalSearchParams<{ needId: string }>();
  return (
    <EditNeedScreen
      needId={needId}
      onBack={() => (router.canGoBack() ? router.back() : router.replace("/parent/needs"))}
      onReview={(id) => router.push({ pathname: "/parent/needs/[needId]/review", params: { needId: id } })}
    />
  );
}
