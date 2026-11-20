import { createContext, useContext, useState, type ReactNode } from "react";

type Selection = { selectedId: string | null; select: (studentId: string) => void };

const SelectedStudent = createContext<Selection | null>(null);

/**
 * The child the parent is looking at (StudentSwitcher, 05-dieu-huong-luong.md). Held by the parent
 * area's layout so every PH screen shows the same child. In memory only: nothing is kept on the device,
 * and signing out unmounts the area.
 */
export function SelectedStudentProvider({ children }: { children: ReactNode }) {
  const [selectedId, select] = useState<string | null>(null);
  return <SelectedStudent.Provider value={{ selectedId, select }}>{children}</SelectedStudent.Provider>;
}

/**
 * The chosen child among the parent's children as the server listed them. Nothing chosen yet, or a
 * child no longer in the list (deleted, another session), falls back to the first one.
 */
export function useSelectedStudent<T extends { id: string }>(students: readonly T[]) {
  const selection = useContext(SelectedStudent);
  if (!selection) {
    throw new Error("useSelectedStudent needs SelectedStudentProvider (app/parent/_layout.tsx)");
  }
  const selected = students.find((student) => student.id === selection.selectedId) ?? students[0];
  return { selected, select: selection.select };
}
