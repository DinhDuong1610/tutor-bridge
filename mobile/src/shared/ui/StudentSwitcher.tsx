import { useState } from "react";
import { Modal, Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Button } from "./Button";
import { Icon } from "./Icon";
import { colors, minTouchSize, radius, spacing, typography } from "./theme";

export type StudentChoice = {
  id: string;
  /** "{tên}, lớp {lớp}" on the bar. */
  label: string;
  name: string;
  /** Second line of the choice in the sheet. */
  detail: string;
};

type Props = {
  choices: readonly StudentChoice[];
  selectedId: string;
  onSelect: (studentId: string) => void;
  /** "Quản lý hồ sơ các con" (PH02). */
  onManage: () => void;
};

/**
 * Figma `StudentSwitcher` (PH01 "Đổi con"): the child being viewed, and a sheet to choose another.
 * Text wraps instead of being cut, for long names and 200% text (PH01 worst data and 200% frames).
 * The chevron is the `expand_more` icon (#95 part 1b); the bar's accessible name already says
 * "Đổi con", so the icon is hidden from screen readers.
 */
export function StudentSwitcher({ choices, selectedId, onSelect, onManage }: Props) {
  const [open, setOpen] = useState(false);
  const selected = choices.find((choice) => choice.id === selectedId) ?? choices[0];

  return (
    <>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`Đổi con. Đang xem ${selected.label}`}
        onPress={() => setOpen(true)}
        style={({ pressed }) => [styles.bar, pressed && styles.pressed]}
      >
        <View style={styles.current}>
          <Text style={styles.caption}>Đang xem</Text>
          <Text style={styles.name}>{selected.label}</Text>
        </View>
        <View style={styles.changeRow}>
          <Text style={styles.change}>Đổi con</Text>
          <Icon name="expand_more" color={colors.primary} />
        </View>
      </Pressable>

      <Modal visible={open} transparent animationType="slide" onRequestClose={() => setOpen(false)}>
        <View style={styles.backdrop}>
          <View style={styles.scrim} />
          <Pressable accessibilityRole="button" accessibilityLabel="Đóng bảng chọn con" style={styles.dismiss} onPress={() => setOpen(false)} />
          <SafeAreaView edges={["bottom"]} style={styles.sheet}>
            <ScrollView contentContainerStyle={styles.sheetContent}>
              <Text accessibilityRole="header" style={styles.sheetTitle}>
                Chọn con
              </Text>
              <View accessibilityRole="radiogroup" style={styles.choices}>
                {choices.map((choice) => {
                  const checked = choice.id === selected.id;
                  return (
                    <Pressable
                      key={choice.id}
                      accessibilityRole="radio"
                      accessibilityState={{ checked }}
                      accessibilityLabel={`${choice.name}. ${choice.detail}`}
                      onPress={() => {
                        onSelect(choice.id);
                        setOpen(false);
                      }}
                      style={({ pressed }) => [styles.choice, checked && styles.choiceChecked, pressed && styles.pressed]}
                    >
                      <View style={[styles.radio, checked && styles.radioChecked]}>
                        {checked ? <View style={styles.radioDot} /> : null}
                      </View>
                      <View style={styles.choiceText}>
                        <Text style={styles.choiceName}>{choice.name}</Text>
                        <Text style={styles.caption}>{choice.detail}</Text>
                      </View>
                    </Pressable>
                  );
                })}
              </View>
              <Text style={styles.caption}>Chọn con khác sẽ tải lại toàn bộ trang theo con đó.</Text>
              <Button
                variant="secondary"
                title="Quản lý hồ sơ các con"
                onPress={() => {
                  setOpen(false);
                  onManage();
                }}
              />
            </ScrollView>
          </SafeAreaView>
        </View>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  bar: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
    minHeight: minTouchSize,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    borderRadius: radius.sm,
    backgroundColor: colors.surface,
  },
  pressed: { opacity: 0.85 },
  current: { flex: 1 },
  caption: { ...typography.caption, color: colors.textMuted },
  name: { ...typography.bodyStrong, color: colors.text },
  changeRow: { flexDirection: "row", alignItems: "center", gap: spacing.xs },
  change: { ...typography.label, color: colors.primary },
  backdrop: { flex: 1, justifyContent: "flex-end" },
  // No scrim token in 02-nen-thiet-ke.md yet: the text color dimmed, so no new color value appears.
  scrim: { position: "absolute", top: 0, right: 0, bottom: 0, left: 0, backgroundColor: colors.text, opacity: 0.4 },
  dismiss: { flex: 1 },
  sheet: { maxHeight: "80%", backgroundColor: colors.background, borderTopLeftRadius: radius.md, borderTopRightRadius: radius.md },
  sheetContent: { gap: spacing.md, padding: spacing.md },
  sheetTitle: { ...typography.heading, color: colors.text },
  choices: { gap: spacing.sm },
  choice: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
    minHeight: minTouchSize,
    padding: spacing.md,
    borderWidth: 1,
    borderColor: colors.divider,
    borderRadius: radius.sm,
  },
  choiceChecked: { borderColor: colors.primary, backgroundColor: colors.infoSurface },
  radio: { width: 20, height: 20, borderRadius: radius.full, borderWidth: 2, borderColor: colors.border, alignItems: "center", justifyContent: "center" },
  radioChecked: { borderColor: colors.primary },
  radioDot: { width: 10, height: 10, borderRadius: radius.full, backgroundColor: colors.primary },
  choiceText: { flex: 1, gap: spacing.xs },
  choiceName: { ...typography.bodyStrong, color: colors.text },
});
