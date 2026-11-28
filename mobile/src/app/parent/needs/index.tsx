// PH03 Danh sách và phiên bản nhu cầu. The role area's layout already requires a PARENT session.
import { useRouter } from "expo-router";
import { NeedsScreen } from "@/features/needs/screens/NeedsScreen";
import type { NeedAction } from "@/features/needs/summary";

export default function NeedsRoute() {
  const router = useRouter();
  const open = (action: NeedAction) => {
    switch (action.kind) {
      case "edit":
        return router.push({ pathname: "/parent/needs/[needId]/edit", params: { needId: action.needId } });
      case "matches":
        return router.push({ pathname: "/parent/needs/[needId]/matches", params: { needId: action.needId } });
      case "offers":
        return router.push({ pathname: "/parent/needs/[needId]/offers", params: { needId: action.needId } });
      case "class":
        return router.push({ pathname: "/parent/classes/[classId]", params: { classId: action.classId } });
    }
  };
  return (
    <NeedsScreen
      onAddStudent={() => router.push("/parent/account/students/new")}
      onManageStudents={() => router.push("/parent/account/students")}
      onCreate={() => router.push("/parent/needs/new")}
      onAction={open}
    />
  );
}
