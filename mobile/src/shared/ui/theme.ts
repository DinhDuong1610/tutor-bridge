/**
 * Design tokens (ADR-0009). Screens use these instead of raw values, so a later visual design
 * changes one file. State is never shown by color alone; every state also has text.
 *
 * Contrast, measured 06/11/2026 (WCAG 2.1): text colors are at least 5.4:1 on their surface
 * (AA asks 4.5:1); `border` is 3.8:1 on `background` (1.4.11 asks 3:1 for input edges).
 * Recheck when a color changes.
 */
export const colors = {
  background: "#FFFFFF",
  surface: "#F4F6F8",
  text: "#14181F",
  textMuted: "#4A5361",
  border: "#7A8494",
  primary: "#0B5CAD",
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

export const spacing = { xs: 4, sm: 8, md: 16, lg: 24, xl: 32 } as const;

export const radius = { sm: 6, md: 10 } as const;

/** Sizes in points. Text scales with the system font size; never fix a height around text. */
export const typography = {
  title: { fontSize: 22, fontWeight: "600" },
  heading: { fontSize: 18, fontWeight: "600" },
  body: { fontSize: 16, fontWeight: "400" },
  label: { fontSize: 14, fontWeight: "600" },
  caption: { fontSize: 13, fontWeight: "400" },
} as const;

/** Android guidance for touch targets. */
export const minTouchSize = 48;
