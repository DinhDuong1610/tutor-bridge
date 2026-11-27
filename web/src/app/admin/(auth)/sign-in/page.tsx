// AD01 Đăng nhập và xác thực quản trị (#68). Open to everyone (proxy.ts); the session starts after MFA.
import { Suspense } from "react";
import { signIn, verifyCode } from "@/features/admin-sign-in/actions";
import { AdminSignInForm, type SignInReason } from "@/features/admin-sign-in/sign-in-form";

export const metadata = { title: "Đăng nhập quản trị | TutorBridge" };

/** The reasons /admin/session-expired puts on the link; anything else shows no notice. */
function reasonOf(raw: string | string[] | undefined): SignInReason {
  return raw === "expired" || raw === "forbidden" ? raw : null;
}

// searchParams is request data, so it is read inside Suspense (cacheComponents), as the console layout does.
export default function AdminSignInPage({ searchParams }: PageProps<"/admin/sign-in">) {
  return (
    <Suspense>
      <SignIn searchParams={searchParams} />
    </Suspense>
  );
}

async function SignIn({ searchParams }: Pick<PageProps<"/admin/sign-in">, "searchParams">) {
  const params = await searchParams;
  // `next` stays raw here: verifyCode passes it through safeNextPath on the server before redirecting.
  const next = typeof params.next === "string" ? params.next : null;
  return <AdminSignInForm actions={{ signIn, verifyCode }} reason={reasonOf(params.reason)} next={next} />;
}
