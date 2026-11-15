// PH02 Sửa hồ sơ. The role area's layout already requires a PARENT session; the server checks the owner.
import { useLocalSearchParams, useRouter } from "expo-router";
import { useCallback } from "react";
import { EditStudentScreen } from "@/features/students/screens/EditStudentScreen";

export default function EditStudentRoute() {
  const router = useRouter();
  const { studentId } = useLocalSearchParams<{ studentId: string }>();
  // Back to the list, which shows "Đã lưu hồ sơ {tên}." The route carries the id, never the name.
  const onSaved = useCallback(
    (id: string) => router.dismissTo({ pathname: "/parent/account/students", params: { saved: id } }),
    [router],
  );
  return (
    <EditStudentScreen
      studentId={studentId}
      onBack={() => (router.canGoBack() ? router.back() : router.replace("/parent/account/students"))}
      onSaved={onSaved}
    />
  );
}
