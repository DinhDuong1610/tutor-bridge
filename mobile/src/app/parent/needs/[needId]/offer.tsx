// PH08 Xem lại và gửi đề nghị (#72 PR 1). Route of PH08.md; opened from PH07 with the run and the tutor.
import { router, useLocalSearchParams } from "expo-router";
import { SendOfferScreen } from "@/features/offers/screens/SendOfferScreen";

export default function PH08Route() {
  const { needId, runId, tutorId } = useLocalSearchParams<{ needId: string; runId: string; tutorId: string }>();
  // Back to the need's PH06 already on the stack, or a new one when opened from a link.
  const toMatches = () => router.dismissTo({ pathname: "/parent/needs/[needId]/matches", params: { needId } });
  return (
    <SendOfferScreen
      needId={needId}
      runId={runId}
      tutorId={tutorId}
      onBack={() => (router.canGoBack() ? router.back() : toMatches())}
      onFindAgain={toMatches}
      onEdit={() => router.push({ pathname: "/parent/needs/[needId]/edit", params: { needId } })}
      onViewOffers={() => router.replace({ pathname: "/parent/needs/[needId]/offers", params: { needId } })}
      onOtherChild={() => router.dismissTo("/parent")}
      onManageStudents={() => router.push("/parent/account/students")}
    />
  );
}
