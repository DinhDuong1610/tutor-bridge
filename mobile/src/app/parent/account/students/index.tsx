// PH02 Hồ sơ các con. The role area's layout already requires a PARENT session.
import { useLocalSearchParams, useRouter } from "expo-router";
import { StudentsScreen } from "@/features/students/screens/StudentsScreen";

export default function StudentsRoute() {
  const router = useRouter();
  const { saved } = useLocalSearchParams<{ saved?: string }>();
  return (
    <StudentsScreen
      savedId={typeof saved === "string" ? saved : undefined}
      onBack={() => (router.canGoBack() ? router.back() : router.replace("/parent"))}
      onAdd={() => router.push("/parent/account/students/new")}
      onEdit={(studentId) => router.push({ pathname: "/parent/account/students/[studentId]", params: { studentId } })}
    />
  );
}
