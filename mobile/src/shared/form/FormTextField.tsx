import { Controller, type Control, type FieldValues, type Path } from "react-hook-form";
import { TextField, type TextFieldInputProps } from "@/shared/ui/TextField";

/**
 * TextInput options a form may set. The value and its events belong to react-hook-form; the
 * accessible name and hint belong to TextField (label, error, hint).
 */
type InputProps = Omit<TextFieldInputProps, "value" | "defaultValue" | "onChangeText" | "onChange" | "onBlur">;

type Props<T extends FieldValues> = InputProps & {
  control: Control<T>;
  name: Path<T>;
  label: string;
  hint?: string;
};

/**
 * A TextField bound to react-hook-form. Shows the schema or server message for its field. Other
 * TextInput options (`secureTextEntry`, `autoComplete`, `keyboardType`, `autoCapitalize`,
 * `multiline`...) pass through to the input.
 */
export function FormTextField<T extends FieldValues>({ control, name, label, hint, ...input }: Props<T>) {
  return (
    <Controller
      control={control}
      name={name}
      render={({ field, fieldState }) => (
        <TextField
          {...input}
          label={label}
          hint={hint}
          value={typeof field.value === "string" ? field.value : ""}
          onChangeText={field.onChange}
          onBlur={field.onBlur}
          error={fieldState.error?.message}
        />
      )}
    />
  );
}
