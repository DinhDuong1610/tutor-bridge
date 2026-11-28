import { useState } from "react";
import { KeyboardAvoidingView, Modal, Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Button } from "@/shared/ui/Button";
import { TextField } from "@/shared/ui/TextField";
import { TimeChip } from "@/shared/ui/TimeChip";
import { colors, minTouchSize, radius, spacing, typography } from "@/shared/ui/theme";
import { DAY_NAMES, addSlot, removeSlot, slotError, type Slot, type WeekPattern } from "../weekly";

type Props = {
  /** The pattern being edited. */
  value: WeekPattern;
  onChange: (next: WeekPattern) => void;
  /** What the server holds: a slot found here shows as in effect, any other as a draft (`03`). */
  saved: WeekPattern;
};

const label = (s: Slot) => `${s.start}–${s.end}`;
const same = (a: Slot, b: Slot) => a.start === b.start && a.end === b.end;

/**
 * Figma `WeekAvailability` (PH04, GS04): one row per weekday with its `TimeChip`s and "Thêm giờ",
 * no 7-column grid, since it does not fit 360 points (`03-thanh-phan.md`). Vietnam time, 24 hours.
 */
export function WeekAvailability({ value, onChange, saved }: Props) {
  const [adding, setAdding] = useState<number | null>(null);
  return (
    <View>
      {DAY_NAMES.map((name, day) => (
        <View key={name} style={[styles.day, day > 0 && styles.divided]}>
          <View style={styles.dayHeader}>
            <Text accessibilityRole="header" style={styles.dayName}>
              {name}
            </Text>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={`Thêm giờ, ${name}`}
              onPress={() => setAdding(day)}
              style={styles.addLink}
            >
              <Text style={styles.addText}>Thêm giờ</Text>
            </Pressable>
          </View>
          {value[day].length === 0 ? (
            <Text style={styles.none}>Không rảnh</Text>
          ) : (
            <View style={styles.chips}>
              {value[day].map((slot) => (
                <TimeChip
                  key={label(slot)}
                  label={label(slot)}
                  day={name}
                  variant={saved[day].some((s) => same(s, slot)) ? "effective" : "draft"}
                  onRemove={() => onChange(removeSlot(value, day, slot))}
                />
              ))}
            </View>
          )}
        </View>
      ))}
      <AddSlotSheet
        day={adding}
        onClose={() => setAdding(null)}
        onAdd={(day, slot) => {
          onChange(addSlot(value, day, slot));
          setAdding(null);
        }}
      />
    </View>
  );
}

type SheetProps = { day: number | null; onClose: () => void; onAdd: (day: number, slot: Slot) => void };

/** GS04 "Bảng thêm giờ"; the same sheet serves PH04 (`PH04.md`: same input as GS04). */
function AddSlotSheet({ day, onClose, onAdd }: SheetProps) {
  const [start, setStart] = useState("");
  const [end, setEnd] = useState("");
  const [error, setError] = useState<string | null>(null);
  const close = () => {
    setStart("");
    setEnd("");
    setError(null);
    onClose();
  };
  const submit = () => {
    if (day === null) return;
    const problem = slotError(start, end);
    if (problem) {
      setError(problem);
      return;
    }
    onAdd(day, { start, end });
    setStart("");
    setEnd("");
    setError(null);
  };
  return (
    <Modal visible={day !== null} transparent animationType="slide" onRequestClose={close}>
      <KeyboardAvoidingView behavior="padding" style={styles.backdrop}>
        <View style={styles.scrim} />
        <Pressable accessibilityRole="button" accessibilityLabel="Đóng bảng thêm giờ" style={styles.dismiss} onPress={close} />
        <SafeAreaView edges={["bottom"]} style={styles.sheet}>
          <ScrollView contentContainerStyle={styles.sheetContent} keyboardShouldPersistTaps="handled">
            <Text accessibilityRole="header" style={styles.sheetTitle}>
              Thêm khung giờ rảnh, {day === null ? "" : DAY_NAMES[day]}
            </Text>
            <View style={styles.fields}>
              <View style={styles.field}>
                <TextField label="Từ" value={start} onChangeText={setStart} placeholder="18:00" keyboardType="numbers-and-punctuation" />
              </View>
              <View style={styles.field}>
                <TextField
                  label="Đến"
                  value={end}
                  onChangeText={setEnd}
                  placeholder="21:00"
                  keyboardType="numbers-and-punctuation"
                  error={error ?? undefined}
                />
              </View>
            </View>
            <Text style={styles.caption}>
              Giờ Việt Nam, 24 giờ. Khung sát nhau như 18:00–19:00 và 19:00–20:00 là hợp lệ.
            </Text>
            <Button title="Thêm khung giờ" onPress={submit} />
            <Button variant="secondary" title="Quay lại" onPress={close} />
          </ScrollView>
        </SafeAreaView>
      </KeyboardAvoidingView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  day: { gap: spacing.sm, paddingVertical: spacing.md },
  divided: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.divider },
  dayHeader: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: spacing.sm },
  dayName: { ...typography.bodyStrong, color: colors.text, flexShrink: 1 },
  addLink: { minHeight: minTouchSize, justifyContent: "center", paddingHorizontal: spacing.sm },
  addText: { ...typography.label, color: colors.primary },
  none: { ...typography.caption, color: colors.textMuted },
  chips: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm },
  backdrop: { flex: 1, justifyContent: "flex-end" },
  // As StudentSwitcher: no scrim token yet, the text color dimmed.
  scrim: { position: "absolute", top: 0, right: 0, bottom: 0, left: 0, backgroundColor: colors.text, opacity: 0.4 },
  dismiss: { flex: 1 },
  sheet: { maxHeight: "80%", backgroundColor: colors.background, borderTopLeftRadius: radius.md, borderTopRightRadius: radius.md },
  sheetContent: { gap: spacing.md, padding: spacing.md },
  sheetTitle: { ...typography.heading, color: colors.text },
  fields: { flexDirection: "row", flexWrap: "wrap", gap: spacing.md },
  field: { flexGrow: 1, flexBasis: 120 },
  caption: { ...typography.caption, color: colors.textMuted },
});
