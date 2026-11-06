import { redirect } from "next/navigation";

// Lát 1 has no overview (AD02); the console opens on the verification queue.
export default function AdminHomePage() {
  redirect("/admin/verifications");
}
