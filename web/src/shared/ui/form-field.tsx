import type { ComponentProps } from "react";
import { Input } from "./input";

type Props = ComponentProps<typeof Input> & {
  id: string;
  label: string;
  /** Field message from the schema or the server. Written as text, not only a red border (NFR12). */
  error?: string;
  hint?: string;
};

export function FormField({ id, label, error, hint, ...input }: Props) {
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined;
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-semibold">
        {label}
      </label>
      <Input id={id} aria-invalid={error ? true : undefined} aria-describedby={describedBy} {...input} />
      {error ? (
        <p id={`${id}-error`} role="alert" className="text-sm text-destructive">
          Lỗi: {error}
        </p>
      ) : hint ? (
        <p id={`${id}-hint`} className="text-sm text-muted-foreground">
          {hint}
        </p>
      ) : null}
    </div>
  );
}
