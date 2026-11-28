// PH09 Danh sách đề nghị của một nhu cầu (#72 PR 2). Route of #70 Q1 (Đính, 26/11/2026).
import { router, useLocalSearchParams } from "expo-router";
import { NeedOffersScreen } from "@/features/offers/screens/NeedOffersScreen";

export default function PH09Route() {
  const { needId, cancelled } = useLocalSearchParams<{ needId: string; cancelled?: string }>();
  return (
    <NeedOffersScreen
      needId={needId}
      cancelledOfferId={cancelled}
      onBack={() => (router.canGoBack() ? router.back() : router.replace("/parent/needs"))}
      onOpenOffer={(offerId) => router.push({ pathname: "/parent/offers/[offerId]", params: { offerId } })}
      onOpenClass={(classId) => router.push({ pathname: "/parent/classes/[classId]", params: { classId } })}
      onFindTutors={() => router.push({ pathname: "/parent/needs/[needId]/matches", params: { needId } })}
      onOtherChild={() => router.dismissTo("/parent")}
      onManageStudents={() => router.push("/parent/account/students")}
    />
  );
}
