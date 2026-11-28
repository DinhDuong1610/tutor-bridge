// PH05 Xem lại và xác nhận nhu cầu (#70 PR 4). Route của PH04.md, PH05.md.
import { router, useLocalSearchParams } from "expo-router";
import { NeedReviewScreen } from "@/features/needs/screens/NeedReviewScreen";

export default function PH05Route() {
  const { needId } = useLocalSearchParams<{ needId: string }>();
  return (
    <NeedReviewScreen
      needId={needId}
      onBack={() => (router.canGoBack() ? router.back() : router.replace("/parent/needs"))}
      onEdit={(id) => router.push({ pathname: "/parent/needs/[needId]/edit", params: { needId: id } })}
      onFindTutors={(id) => router.push({ pathname: "/parent/needs/[needId]/matches", params: { needId: id } })}
    />
  );
}
