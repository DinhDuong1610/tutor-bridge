// PH02 Thêm hồ sơ con.
import { useRouter } from "expo-router";
import { useCallback } from "react";
import { AddStudentScreen } from "@/features/students/screens/AddStudentScreen";

export default function AddStudentRoute() {
  const router = useRouter();
  // Back to the list, which shows "Đã lưu hồ sơ {tên}." The route carries the id, never the name.
  const onSaved = useCallback(
    (studentId: string) => router.dismissTo({ pathname: "/parent/account/students", params: { saved: studentId } }),
    [router],
  );
  return (
    <AddStudentScreen
      onBack={() => (router.canGoBack() ? router.back() : router.replace("/parent/account/students"))}
      onSaved={onSaved}
    />
  );
}
