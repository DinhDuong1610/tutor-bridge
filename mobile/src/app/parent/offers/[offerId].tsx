// PH09 Chi tiết và hủy đề nghị (#72 PR 2). Route of PH09.md.
import { router, useLocalSearchParams } from "expo-router";
import { OfferDetailScreen } from "@/features/offers/screens/OfferDetailScreen";

export default function PH09DetailRoute() {
  const { offerId } = useLocalSearchParams<{ offerId: string }>();
  return (
    <OfferDetailScreen
      offerId={offerId}
      onBack={() => (router.canGoBack() ? router.back() : router.replace("/parent/needs"))}
      onFindTutors={(needId) => router.push({ pathname: "/parent/needs/[needId]/matches", params: { needId } })}
      onOpenClass={(classId) => router.push({ pathname: "/parent/classes/[classId]", params: { classId } })}
      // The list says which offer was cancelled; it replaces this screen so "back" does not return to it.
      onCancelled={(needId, cancelled) =>
        router.dismissTo({ pathname: "/parent/needs/[needId]/offers", params: { needId, cancelled } })
      }
      onOtherChild={() => router.dismissTo("/parent")}
      onManageStudents={() => router.push("/parent/account/students")}
    />
  );
}
