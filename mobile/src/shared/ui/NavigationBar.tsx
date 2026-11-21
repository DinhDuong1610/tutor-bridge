import { Pressable, StyleSheet, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Icon, type IconName } from "./Icon";
import { colors, fonts, minTouchSize, radius, spacing, typography } from "./theme";

export type NavigationTab = {
  key: string;
  label: string;
  icon: IconName;
  /** Unread count for the badge (PH01: "Số chưa đọc trên 99 hiện 99+"). No badge when 0 or absent. */
  unread?: number;
};

type Props = {
  tabs: NavigationTab[];
  selectedKey: string;
  onSelect: (key: string) => void;
};

// OP-22 (Đính, 12/11/2026): only the tab labels stop growing at 1.3x, so five labels fit a 360 dp
// screen at 200% text. Every other text still scales fully.
const TAB_LABEL_MAX_SCALE = 1.3;

const badgeText = (unread: number) => (unread > 99 ? "99+" : String(unread));

/**
 * Bottom tab bar, Figma `NavigationBar` (node 8:428, approved v1) as drawn in `PH01-v1.png`: the
 * selected tab has the `infoSurface` pill behind a `primary` icon; other tabs are `textMuted`.
 * Which tabs a role has is decided by the screen that uses it (TB-14a).
 */
export function NavigationBar({ tabs, selectedKey, onSelect }: Props) {
  const insets = useSafeAreaInsets();
  return (
    <View accessibilityRole="tablist" style={[styles.bar, { paddingBottom: insets.bottom + spacing.xs }]}>
      {tabs.map((tab) => {
        const selected = tab.key === selectedKey;
        const unread = tab.unread ?? 0;
        return (
          <Pressable
            key={tab.key}
            accessibilityRole="tab"
            accessibilityState={{ selected }}
            // 05-dieu-huong-luong.md: the badge reads "3 thông báo chưa đọc" after the tab name.
            accessibilityLabel={unread > 0 ? `${tab.label}, ${badgeText(unread)} thông báo chưa đọc` : tab.label}
            onPress={() => onSelect(tab.key)}
            style={styles.tab}
          >
            <View style={[styles.indicator, selected && styles.indicatorSelected]}>
              <Icon name={tab.icon} color={selected ? colors.primary : colors.textMuted} />
              {unread > 0 ? (
                <View style={styles.badge}>
                  <Text style={styles.badgeText}>{badgeText(unread)}</Text>
                </View>
              ) : null}
            </View>
            <Text
              maxFontSizeMultiplier={TAB_LABEL_MAX_SCALE}
              style={[styles.label, selected ? styles.labelSelected : null]}
            >
              {tab.label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  bar: {
    flexDirection: "row",
    paddingTop: spacing.sm,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: colors.divider,
    backgroundColor: colors.background,
  },
  tab: { flex: 1, minHeight: minTouchSize, alignItems: "center", gap: spacing.xs },
  // Sizes from Figma `NavigationBar` (node 8:428, variants 8:148 and 8:232), checked by Đính's review
  // of #202: indicator 64×32 with the 24 icon at x=20; badge 16×16 at (36, 0) inside the indicator.
  indicator: { width: 64, height: 32, borderRadius: radius.full, alignItems: "center", justifyContent: "center" },
  indicatorSelected: { backgroundColor: colors.infoSurface },
  badge: {
    position: "absolute",
    top: 0,
    left: 36,
    minWidth: 16,
    // minHeight, not height: the digits still scale with large text (OP-22 limits only tab labels).
    minHeight: 16,
    paddingHorizontal: spacing.xs,
    borderRadius: radius.full,
    backgroundColor: colors.danger,
    alignItems: "center",
    justifyContent: "center",
  },
  // The digits sit in the 16 high badge; "99+" widens it through the padding.
  badgeText: { ...typography.caption, lineHeight: 16, color: colors.onPrimary },
  label: { ...typography.caption, color: colors.textMuted, textAlign: "center" },
  // Same size as the other labels, only heavier, so the row does not shift when the tab changes.
  labelSelected: { fontFamily: fonts.medium, color: colors.text },
});
