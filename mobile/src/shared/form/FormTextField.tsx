import { Controller, type Control, type FieldValues, type Path } from "react-hook-form";
import { TextField } from "@/shared/ui/TextField";

type Props<T extends FieldValues> = {
  control: Control<T>;
  name: Path<T>;
  label: string;
  hint?: string;
  multiline?: boolean;
};

/** A TextField bound to react-hook-form. Shows the schema or server message for its field. */
export function FormTextField<T extends FieldValues>({ control, name, label, hint, multiline }: Props<T>) {
  return (
    <Controller
      control={control}
      name={name}
      render={({ field, fieldState }) => (
        <TextField
          label={label}
          hint={hint}
          multiline={multiline}
          value={typeof field.value === "string" ? field.value : ""}
          onChangeText={field.onChange}
          onBlur={field.onBlur}
          error={fieldState.error?.message}
        />
      )}
    />
  );
}
