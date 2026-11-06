## TutorBridge rules come first

Follow `../AGENTS.md` and `../docs/context/conventions.md`. They override the generic Expo guidance below.

- Libraries are fixed by `../docs/decisions/adr/0009-nen-giao-dien.md`: Expo Router, TanStack Query, `StyleSheet` with theme tokens. Do not add other UI, style, state or navigation libraries without Đính (CR-012).
- Folder structure and the steps to add a screen are in `../docs/context/conventions.md` ("Cấu trúc mobile", "Thêm một màn hình"). Routes live in `src/app/` (M1 done); role areas are the folders `parent/` and `tutor/`, not route groups. Foundation modules M1–M5 (#49–#53) provide routing, data, session, UI and forms.
- The "Navigation & Routing" section below says routes live in `src/app/`; that matches this project. Use `@/` imports and test routes with `renderRouter` from `expo-router/testing-library`. Read the session with `useSession` and guard role areas with `RequireRole` (`@/shared/session`). Build forms with react-hook-form, zod, `FormTextField` and `useServerErrors` (`@/shared/form`); test screens with `mockApi` and `renderScreen` (`@/shared/test`), following `src/shared/test/example.test.tsx`. Call the API through `call` and `useCommand` in `@/shared/api` (see conventions, "Gọi API trên mobile").
- Use `@tutorbridge/api-client` for every API call. Only `EXPO_PUBLIC_` variables, never secrets.

## Generic Expo guidance

This is an Expo/React Native mobile application. Prioritize mobile-first patterns, performance, and cross-platform compatibility.

## Expo has changed — do not trust your training data

Expo ships breaking changes every SDK release. APIs you remember are likely renamed, moved, or removed. Before writing any code that touches an Expo, EAS, or React Native API:

1. Read the major version of the `expo` package in `package.json`.
2. Fetch the matching versioned docs: `https://docs.expo.dev/versions/v<major>.0.0/`
3. For anything else, fetch https://docs.expo.dev/llms.txt — an index of all Expo docs with corrections to common LLM misconceptions. Follow its links to the specific page you need; never answer from memory.

## Commands

Use `bunx` instead of `npx` if the project uses bun (`bun.lock` present).

```bash
npx expo install <package>  # ALWAYS use instead of npm/yarn/pnpm/bun add — resolves SDK-compatible versions
npx expo start              # start the dev server
npx expo lint               # lint
npx tsc --noEmit            # typecheck
npx expo-doctor             # diagnose dependency and config issues
npx expo install --fix      # fix incompatible package versions
```

Run lint and typecheck before declaring any task done.

## Navigation & Routing

- Use **Expo Router** for all navigation. Routes live in `src/app/` — every file there is a screen, `_layout.tsx` files define navigators. Keep non-route code (components, hooks, utils) outside `src/app/`.
- Import `Link`, `router`, and `useLocalSearchParams` from `expo-router`.
- Docs: https://docs.expo.dev/router/introduction.md

## Building with EAS

Use EAS to build, sign, and submit the app in the cloud (`eas build`, `eas submit`) and to ship over-the-air updates (`eas update`) — no local Xcode or Android Studio required. Run EAS CLI as `bunx eas-cli <command>` in Bun projects, or `npx eas-cli@latest <command>` otherwise; substitute that for bare `eas` in docs examples.
Docs: https://docs.expo.dev/eas/index.md

## Rules

- If `ios/` and `android/` directories do not exist, they are generated (Continuous Native Generation). Never create or edit them by hand — configure native behavior in `app.json` and config plugins.
- Expo Go only includes its bundled native modules. After adding a library with native code, the app needs a development build: `npx expo run:ios|android` locally, or `eas build --profile development`.
- Prefer recommended Expo modules over third-party libraries, and check your available skills before adding dependencies. Docs: https://docs.expo.dev/versions/latest/index.md
