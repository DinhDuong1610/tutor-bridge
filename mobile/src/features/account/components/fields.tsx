import { useState } from "react";
import { Controller, type Control, type FieldValues, type Path } from "react-hook-form";
import { Pressable, StyleSheet, Text, View, type TextInputProps } from "react-native";
import { FormTextField } from "@/shared/form/FormTextField";
import { colors, minTouchSize, spacing, typography } from "@/shared/ui/theme";

type InputProps = Pick<TextInputProps, "secureTextEntry" | "autoComplete" | "autoCapitalize" | "keyboardType" | "inputMode">;

/**
 * FormTextField with the input options an account form needs: email keyboard, hidden password,
 * autofill hints. The value is never logged. A hidden password gets a "Hiện ..." button instead of
 * a second "type it again" field (handoff TB-10, Q9).
 */
export function AccountTextField<T extends FieldValues>({
  control,
  name,
  label,
  hint,
  secureTextEntry,
  ...input
}: { control: Control<T>; name: Path<T>; label: string; hint?: string } & InputProps) {
  const [revealed, setRevealed] = useState(false);
  return (
    <View style={styles.group}>
      <FormTextField
        {...input}
        control={control}
        name={name}
        label={label}
        hint={hint}
        secureTextEntry={secureTextEntry === true && !revealed}
      />
      {secureTextEntry ? (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`${revealed ? "Ẩn" : "Hiện"} ${label.toLowerCase()}`}
          onPress={() => setRevealed((value) => !value)}
          style={styles.reveal}
        >
          <Text style={styles.revealText}>{revealed ? "Ẩn" : "Hiện"} {label.toLowerCase()}</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

type Option<V extends string> = { value: V; label: string };

/** One choice from a short list. Selection is shown by a mark and announced, not only by color. */
export function RadioField<T extends FieldValues, V extends string>({
  control,
  name,
  label,
  options,
}: { control: Control<T>; name: Path<T>; label: string; options: readonly Option<V>[] }) {
  return (
    <Controller
      control={control}
      name={name}
      render={({ field, fieldState }) => (
        <View accessibilityRole="radiogroup" accessibilityLabel={label} style={styles.group}>
          <Text style={styles.label}>{label}</Text>
          {options.map((option) => (
            <Choice
              key={option.value}
              role="radio"
              label={option.label}
              selected={field.value === option.value}
              onPress={() => field.onChange(option.value)}
            />
          ))}
          <FieldError message={fieldState.error?.message} />
        </View>
      )}
    />
  );
}

/** A yes or no agreement, such as accepting the terms. */
export function CheckboxField<T extends FieldValues>({
  control,
  name,
  label,
}: { control: Control<T>; name: Path<T>; label: string }) {
  return (
    <Controller
      control={control}
      name={name}
      render={({ field, fieldState }) => (
        <View style={styles.group}>
          <Choice role="checkbox" label={label} selected={field.value === true} onPress={() => field.onChange(field.value !== true)} />
          <FieldError message={fieldState.error?.message} />
        </View>
      )}
    />
  );
}

function Choice({
  role,
  label,
  selected,
  onPress,
}: { role: "radio" | "checkbox"; label: string; selected: boolean; onPress: () => void }) {
  const mark = role === "radio" ? (selected ? "●" : "○") : selected ? "☑" : "☐";
  return (
    <Pressable
      accessibilityRole={role}
      accessibilityLabel={label}
      accessibilityState={{ checked: selected }}
      onPress={onPress}
      style={styles.choice}
    >
      <Text style={styles.mark}>{mark}</Text>
      <Text style={styles.choiceText}>{label}</Text>
    </Pressable>
  );
}

function FieldError({ message }: { message?: string }) {
  return message ? (
    <Text accessibilityRole="alert" style={styles.error}>
      Lỗi: {message}
    </Text>
  ) : null;
}

const styles = StyleSheet.create({
  group: { gap: spacing.xs },
  reveal: { minHeight: minTouchSize, justifyContent: "center", alignSelf: "flex-start" },
  revealText: { ...typography.label, color: colors.primary },
  label: { ...typography.label, color: colors.text },
  choice: { minHeight: minTouchSize, flexDirection: "row", alignItems: "center", gap: spacing.sm },
  mark: { ...typography.body, color: colors.primary },
  choiceText: { ...typography.body, color: colors.text, flexShrink: 1 },
  error: { ...typography.caption, color: colors.danger },
});
