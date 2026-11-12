import { StyleSheet, Text, TextInput, View, type TextInputProps } from "react-native";
import { colors, minTouchSize, radius, spacing, typography } from "./theme";

/**
 * The input's accessible name and hint come from `label`, `error` and `hint` only. React Native
 * prefers `aria-*` over `accessibility*`, so those are left out too: a screen must not be able to
 * hide the field's error from a screen reader (NFR12).
 */
export type TextFieldInputProps = Omit<
  TextInputProps,
  "style" | "accessibilityLabel" | "accessibilityHint" | "aria-label" | "aria-labelledby" | "accessibilityLabelledBy"
>;

type Props = TextFieldInputProps & {
  label: string;
  /** Field message from the server (`ApiError.fields`) or the form schema. */
  error?: string;
  hint?: string;
};

// TypeScript does not check hyphenated JSX attributes, so `aria-label` can still be passed: drop
// these at runtime as well.
const NAME_AND_HINT = ["accessibilityLabel", "accessibilityHint", "aria-label", "aria-labelledby", "accessibilityLabelledBy"];

function withoutNameAndHint(input: TextFieldInputProps): TextFieldInputProps {
  const rest: Record<string, unknown> = { ...input };
  for (const key of NAME_AND_HINT) delete rest[key];
  return rest as TextFieldInputProps;
}

/** A labelled input. The error is text next to the field, not only a red border (NFR12). */
export function TextField({ label, error, hint, ...input }: Props) {
  return (
    <View style={styles.container}>
      <Text style={styles.label}>{label}</Text>
      <TextInput
        placeholderTextColor={colors.textMuted}
        {...withoutNameAndHint(input)}
        accessibilityLabel={label}
        accessibilityHint={error ?? hint}
        style={[styles.input, error ? styles.inputError : null]}
      />
      {error ? (
        <Text accessibilityRole="alert" style={styles.error}>
          Lỗi: {error}
        </Text>
      ) : hint ? (
        <Text style={styles.hint}>{hint}</Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: spacing.xs },
  label: { ...typography.label, color: colors.text },
  input: {
    ...typography.body,
    minHeight: minTouchSize,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.sm,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    color: colors.text,
    backgroundColor: colors.background,
  },
  inputError: { borderColor: colors.danger, borderWidth: 2 },
  error: { ...typography.caption, color: colors.danger },
  hint: { ...typography.caption, color: colors.textMuted },
});
