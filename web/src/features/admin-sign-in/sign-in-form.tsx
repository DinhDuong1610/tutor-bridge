"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Eye, EyeOff, LoaderCircle } from "lucide-react";
import { useEffect, useState, useTransition } from "react";
import { useForm } from "react-hook-form";
import { Button } from "@/shared/ui/button";
import { FormField } from "@/shared/ui/form-field";
import { Input } from "@/shared/ui/input";
import { Notice } from "@/shared/ui/notice";
import type { SignInStep } from "./actions";
import { COPY } from "./copy";
import { mfaForm, signInForm, type MfaForm, type SignInForm } from "./schemas";

type Actions = {
  signIn: (input: SignInForm) => Promise<SignInStep>;
  verifyCode: (input: MfaForm, next: string | null) => Promise<SignInStep>;
};

/** Why the console sent the admin here (`/admin/session-expired`, #82, #86). */
export type SignInReason = "expired" | "forbidden" | null;

/**
 * AD01: email and password, then the authenticator code (G3-D16-02). The page only learns which step it
 * is on: the challenge and the session token never reach it (AD01, ADR-0010).
 */
export function AdminSignInForm({
  actions,
  reason,
  next,
}: {
  actions: Actions;
  reason: SignInReason;
  next: string | null;
}) {
  const [step, setStep] = useState<"password" | "mfa">("password");
  const [answer, setAnswer] = useState<SignInStep | null>(null);
  const [pending, startTransition] = useTransition();

  // Codex on #253: a delay ends on its own once the time the API named has passed, so the admin who waited
  // the 15 minutes can try again without reloading. Editing a field does not end it (AD01 "Bị trì hoãn").
  useEffect(() => {
    if (answer?.state !== "delayed") {
      return;
    }
    const timer = setTimeout(() => setAnswer((now) => (now === answer ? null : now)), answer.retryAfterSeconds * 1000);
    return () => clearTimeout(timer);
  }, [answer]);

  function run(call: () => Promise<SignInStep>) {
    startTransition(async () => {
      let result: SignInStep;
      try {
        result = await call();
      } catch {
        result = { state: "failed" };
      }
      setAnswer(result);
      if (result.state === "mfa") {
        setStep("mfa");
      } else if (result.state === "challenge-ended") {
        setStep("password");
      }
    });
  }

  return (
    // Đính, 27/11/2026: the frame's muted page and borderless card, kept centred.
    <div className="flex min-h-dvh items-center bg-muted px-4">
      <section className="mx-auto flex w-full max-w-md flex-col gap-4 rounded-lg bg-background p-6">
        <p className="text-sm text-muted-foreground">{COPY.brand}</p>
        {step === "password" ? (
          <PasswordStep
            reason={answer === null ? reason : null}
            answer={answer}
            pending={pending}
            onEdit={() => {
              // Editing clears an error, but not the delay: only waiting ends it (AD01 "Bị trì hoãn").
              if (answer !== null && answer.state !== "delayed") {
                setAnswer(null);
              }
            }}
            onSubmit={(values) => run(() => actions.signIn(values))}
          />
        ) : (
          <MfaStep
            answer={answer}
            pending={pending}
            onSubmit={(values) => run(() => actions.verifyCode(values, next))}
          />
        )}
      </section>
    </div>
  );
}

function PasswordStep({
  reason,
  answer,
  pending,
  onEdit,
  onSubmit,
}: {
  reason: SignInReason;
  answer: SignInStep | null;
  pending: boolean;
  onEdit: () => void;
  onSubmit: (values: SignInForm) => void;
}) {
  const form = useForm<SignInForm>({ resolver: zodResolver(signInForm), defaultValues: { email: "", password: "" } });
  const [shown, setShown] = useState(false);
  const fields = answer?.state === "invalid" ? answer.fields : {};
  const delayed = answer?.state === "delayed";
  const answerNotice = pending ? <Notice tone="info" message={COPY.sending} /> : <PasswordAnswer answer={answer} />;
  // Đính, 27/11/2026: the note belongs to the default frame only, so it goes once anything else is said.
  const quiet = !pending && reason === null && (answer === null || answer.state === "invalid" || answer.state === "mfa");

  return (
    <form noValidate onChange={onEdit} onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-4">
      <h1 className="text-2xl font-semibold">{COPY.signInTitle}</h1>
      {reason === "expired" ? <Notice tone="info" message={COPY.expired} /> : null}
      {reason === "forbidden" ? <Notice tone="danger" message={COPY.forbidden} /> : null}
      <FormField
        id="email"
        label={COPY.email}
        type="email"
        autoComplete="username"
        error={form.formState.errors.email?.message ?? fields.email}
        {...form.register("email")}
      />
      <div className="flex flex-col gap-1.5">
        <label htmlFor="password" className="text-sm font-semibold">
          {COPY.password}
        </label>
        <div className="relative">
          <Input
            id="password"
            type={shown ? "text" : "password"}
            autoComplete="current-password"
            className="pr-11"
            aria-invalid={form.formState.errors.password || fields.password ? true : undefined}
            aria-describedby={form.formState.errors.password || fields.password ? "password-error" : undefined}
            {...form.register("password")}
          />
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="absolute top-0 right-0"
            aria-label={shown ? COPY.hidePassword : COPY.showPassword}
            aria-pressed={shown}
            onClick={() => setShown((value) => !value)}
          >
            {shown ? <EyeOff aria-hidden /> : <Eye aria-hidden />}
          </Button>
        </div>
        {form.formState.errors.password?.message || fields.password ? (
          <p id="password-error" role="alert" className="text-sm text-destructive">
            Lỗi: {form.formState.errors.password?.message ?? fields.password}
          </p>
        ) : null}
      </div>
      {answerNotice}
      <Button type="submit" disabled={pending || delayed} aria-busy={pending || undefined}>
        {pending ? <LoaderCircle aria-hidden className="animate-spin motion-reduce:animate-none" /> : null}
        {COPY.continue}
      </Button>
      {quiet ? <p className="text-sm text-muted-foreground">{COPY.note}</p> : null}
    </form>
  );
}

function PasswordAnswer({ answer }: { answer: SignInStep | null }) {
  switch (answer?.state) {
    case "wrong-credentials":
      return <Notice tone="danger" message={COPY.wrongCredentials} />;
    case "challenge-ended":
      return <Notice tone="danger" message={COPY.wrongCode} />;
    case "delayed":
      return <Notice tone="warning" message={COPY.delayed} />;
    case "locked":
      return <Notice tone="danger" message={COPY.locked} />;
    case "failed":
      return <Notice tone="danger" message={COPY.failed} />;
    default:
      return null;
  }
}

function MfaStep({
  answer,
  pending,
  onSubmit,
}: {
  answer: SignInStep | null;
  pending: boolean;
  onSubmit: (values: MfaForm) => void;
}) {
  const form = useForm<MfaForm>({ resolver: zodResolver(mfaForm), defaultValues: { code: "" } });
  const wrong = answer?.state === "wrong-code" ? COPY.wrongCode : undefined;

  return (
    <form noValidate onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-4">
      <h1 className="text-2xl font-semibold">{COPY.mfaTitle}</h1>
      <p>{COPY.mfaIntro}</p>
      <FormField
        id="code"
        label={COPY.code}
        inputMode="numeric"
        autoComplete="one-time-code"
        maxLength={8}
        autoFocus
        error={form.formState.errors.code?.message ?? wrong}
        {...form.register("code")}
      />
      {pending ? (
        <Notice tone="info" message={COPY.sending} />
      ) : answer?.state === "delayed" ? (
        <Notice tone="warning" message={COPY.delayed} />
      ) : answer?.state === "locked" ? (
        <Notice tone="danger" message={COPY.locked} />
      ) : answer?.state === "failed" ? (
        <Notice tone="danger" message={COPY.failed} />
      ) : null}
      <Button type="submit" disabled={pending || answer?.state === "delayed"} aria-busy={pending || undefined}>
        {pending ? <LoaderCircle aria-hidden className="animate-spin motion-reduce:animate-none" /> : null}
        {COPY.confirm}
      </Button>
      <p className="text-sm text-muted-foreground">{COPY.mfaNote}</p>
    </form>
  );
}
