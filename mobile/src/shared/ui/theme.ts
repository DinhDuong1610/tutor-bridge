/**
 * Design tokens (ADR-0009), the same roles and values as `docs/design/ui/02-nen-thiet-ke.md` and
 * Figma `01 Foundations`. Screens use these instead of raw values. State is never shown by color
 * alone; every state also has text.
 *
 * Contrast, measured 15/11/2026 (WCAG 2.1; AA asks 4.5:1 for text, 3:1 for input edges and icons):
 * text 17.79 and textMuted 7.77 on background; primary 6.67 on background; onPrimary 6.67 on
 * primary and 9.58 on primaryPressed; border 3.78 on background. `divider` is 1.41 on background:
 * decoration only, never an input edge or anything that carries meaning. Recheck when a color
 * changes and update the table in `02-nen-thiet-ke.md`.
 */
export const colors = {
  background: "#FFFFFF",
  surface: "#F4F6F8",
  text: "#14181F",
  textMuted: "#4A5361",
  border: "#7A8494",
  divider: "#D5DAE1",
  primary: "#0B5CAD",
  primaryPressed: "#08467F",
  onPrimary: "#FFFFFF",
  danger: "#B42318",
  dangerSurface: "#FDECEA",
  warning: "#8A5A00",
  warningSurface: "#FFF4DB",
  success: "#1E6B34",
  successSurface: "#E7F5EC",
  info: "#0B5CAD",
  infoSurface: "#E8F1FB",
} as const;

export const spacing = { xs: 4, sm: 8, md: 16, lg: 24, xl: 32, xxl: 48 } as const;

export const radius = { sm: 8, md: 12, full: 999 } as const;

/**
 * Be Vietnam Pro, loaded in `src/app/_layout.tsx`. Each weight is its own family, so styles set
 * `fontFamily` and not `fontWeight` (Android would otherwise fall back to the system font).
 */
export const fonts = {
  regular: "BeVietnamPro_400Regular",
  medium: "BeVietnamPro_500Medium",
  semiBold: "BeVietnamPro_600SemiBold",
} as const;

/**
 * Sizes and line heights in points, as in the approved table of `02-nen-thiet-ke.md`. Every style
 * has a line height so stacked Vietnamese marks (ệ, ẫ, ặ) are not clipped, at least 1.4 times the
 * size. `title` 24/32 (1.33) and `caption` 13/18 (1.38) are exceptions of the table that Đính kept
 * (21/11/2026, #188); `dateNumeral` holds digits only, so the rule does not apply to it. Text
 * scales with the system font size; never fix a height around text.
 */
export const typography = {
  title: { fontFamily: fonts.semiBold, fontSize: 24, lineHeight: 32 },
  heading: { fontFamily: fonts.semiBold, fontSize: 18, lineHeight: 26 },
  body: { fontFamily: fonts.regular, fontSize: 16, lineHeight: 24 },
  bodyStrong: { fontFamily: fonts.medium, fontSize: 16, lineHeight: 24 },
  label: { fontFamily: fonts.medium, fontSize: 14, lineHeight: 20 },
  caption: { fontFamily: fonts.regular, fontSize: 13, lineHeight: 18 },
  dateNumeral: { fontFamily: fonts.semiBold, fontSize: 28, lineHeight: 32, fontVariant: ["tabular-nums"] },
} as const;

/** Android guidance for touch targets. */
export const minTouchSize = 48;
