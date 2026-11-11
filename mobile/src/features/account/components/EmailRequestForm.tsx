import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { StyleSheet, View } from "react-native";
import type { useCommand } from "@/shared/api/command";
import { useServerErrors } from "@/shared/form/serverErrors";
import { Button } from "@/shared/ui/Button";
import { CommandStatus } from "@/shared/ui/CommandStatus";
import { Notice } from "@/shared/ui/Notice";
import { spacing } from "@/shared/ui/theme";
import { emailOnlySchema, type EmailOnlyForm } from "../schemas";
import { AccountTextField } from "./fields";

type Acknowledgment = { message: string };

type Props = {
  command: ReturnType<typeof useCommand<EmailOnlyForm, Acknowledgment>>;
  submitTitle: string;
};

/**
 * Email in, generic acknowledgment out: resend verification (AUTH02) and password recovery (AUTH04).
 * The confirmation is the server's own text, which never says whether the email has an account.
 */
export function EmailRequestForm({ command, submitTitle }: Props) {
  const form = useForm<EmailOnlyForm>({ resolver: zodResolver(emailOnlySchema), defaultValues: { email: "" } });
  const otherErrors = useServerErrors(command.error, form.setError, ["email"]);

  return (
    <View style={styles.group}>
      <AccountTextField
        control={form.control}
        name="email"
        label="Email"
        autoCapitalize="none"
        autoComplete="email"
        keyboardType="email-address"
        inputMode="email"
      />
      {otherErrors.map((message) => (
        <Notice key={message} tone="danger" message={message} />
      ))}
      {command.state === "confirmed" && command.result ? (
        <Notice tone="success" message={command.result.message} />
      ) : (
        <CommandStatus command={command} confirmedMessage="" />
      )}
      <Button
        title={submitTitle}
        busy={command.state === "sending"}
        onPress={form.handleSubmit((values) => command.submit(values))}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  group: { gap: spacing.md },
});
