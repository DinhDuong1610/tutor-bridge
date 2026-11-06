import { StyleSheet, Text, TextInput, View, type TextInputProps } from "react-native";
import { colors, minTouchSize, radius, spacing, typography } from "./theme";

type Props = Omit<TextInputProps, "style" | "accessibilityLabel"> & {
  label: string;
  /** Field message from the server (`ApiError.fields`) or the form schema. */
  error?: string;
  hint?: string;
};

/** A labelled input. The error is text next to the field, not only a red border (NFR12). */
export function TextField({ label, error, hint, ...input }: Props) {
  return (
    <View style={styles.container}>
      <Text style={styles.label}>{label}</Text>
      <TextInput
        accessibilityLabel={label}
        accessibilityHint={error ?? hint}
        placeholderTextColor={colors.textMuted}
        style={[styles.input, error ? styles.inputError : null]}
        {...input}
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
