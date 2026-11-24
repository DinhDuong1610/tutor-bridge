// GS02 Hồ sơ chuyên môn và học phí (UC03, FR-05, FR-06). Thiết kế: docs/design/ui/screens/GS02.md
import { zodResolver } from "@hookform/resolvers/zod";
import { useRef, useState } from "react";
import { useForm, useWatch, type Control } from "react-hook-form";
import { KeyboardAvoidingView, ScrollView, StyleSheet, Text, View } from "react-native";
import { FormTextField } from "@/shared/form/FormTextField";
import { lengthOf } from "@/shared/form/length";
import { useServerErrors } from "@/shared/form/serverErrors";
import { Button } from "@/shared/ui/Button";
import { CommandStatus } from "@/shared/ui/CommandStatus";
import { Icon } from "@/shared/ui/Icon";
import { Notice } from "@/shared/ui/Notice";
import { QueryScreen } from "@/shared/ui/QueryScreen";
import { StatusLabel } from "@/shared/ui/StatusLabel";
import { StickyActionBar } from "@/shared/ui/StickyActionBar";
import { colors, radius, spacing, typography } from "@/shared/ui/theme";
import {
  useSaveTutorProfile,
  useTutorProfile,
  type OwnVerification,
  type ProfileRevision,
  type TutorProfile,
  type TutorProfileData,
} from "../api";
import { deriveProfileMode, STATUS_LABELS } from "../derive";
import { formatThousands, SUBJECT_LABEL, tutorProfileSchema, type TutorProfileForm, type TutorProfileFormInput } from "../schemas";

type Props = {
  onBack: () => void;
  /** "Tiếp: tải minh chứng", once the newest version is a saved draft (GS03). */
  onNext: () => void;
};

const TITLE = "Hồ sơ chuyên môn";
const FIELDS = ["description", "experienceMonths", "rateVnd60"] as const;

/**
 * Loading, lost access and the end of the session come from QueryScreen (INV-06). The form saves
 * against the revision it was filled from; a stale ETag (412) is never merged on the device (INV-23):
 * the user reloads, keeps what they typed, and saves again.
 */
export function TutorProfileScreen({ onBack, onNext }: Props) {
  const query = useTutorProfile();
  return (
    <QueryScreen title={TITLE} onBack={onBack} query={query}>
      {(loaded) => <ProfileForm initial={loaded} onReload={query.refetch} onNext={onNext} />}
    </QueryScreen>
  );
}

function toFormValues(profile: TutorProfile | null): TutorProfileFormInput {
  return {
    description: profile?.description ?? "",
    experienceMonths: profile ? String(profile.experienceMonths) : "",
    rateVnd60: profile ? formatThousands(profile.rateVnd60) : "",
  };
}

function ProfileForm({
  initial,
  onReload,
  onNext,
}: {
  initial: TutorProfileData;
  onReload: () => Promise<{ isSuccess: boolean; data?: TutorProfileData }>;
  onNext: () => void;
}) {
  // The revision the form was filled from, as in PH02: a background refetch must not replace it, or the
  // new ETag would let old values overwrite a newer save (Codex review of #138). It moves only to the
  // server's answer after a save, or to the latest revision on "Tải lại".
  const [base, setBase] = useState<ProfileRevision | null>(initial.profile);
  const [verification, setVerification] = useState<OwnVerification | null>(initial.verification);
  const mode = deriveProfileMode({ profile: base, verification });
  const form = useForm<TutorProfileFormInput, unknown, TutorProfileForm>({
    resolver: zodResolver(tutorProfileSchema),
    defaultValues: toFormValues(base?.data ?? null),
  });
  const save = useSaveTutorProfile();
  const otherErrors = useServerErrors(save.error, form.setError, FIELDS);
  const hasFieldErrors = Object.keys(form.formState.errors).length > 0;
  const stale = save.state === "failed" && save.error?.kind === "stale";
  const values = useWatch({ control: form.control });
  const loadedValues = toFormValues(base?.data ?? null);
  const hasChanges = FIELDS.some((name) => (values[name] ?? "").trim() !== loadedValues[name]);
  // Set by the button that started the save: "Tiếp" goes on to GS03 once it is confirmed.
  const nextAfterSave = useRef(false);
  // The changed fields keep what the user typed; untouched ones take the server's newer value, so
  // saving again cannot send back a value another device has replaced (Huy's review of #138).
  const reload = async () => {
    const result = await onReload();
    if (result.isSuccess && result.data) {
      const typed = form.getValues();
      const latest = toFormValues(result.data.profile?.data ?? null);
      const next = { ...latest };
      for (const name of FIELDS) if (typed[name].trim() !== loadedValues[name]) next[name] = typed[name];
      setBase(result.data.profile);
      setVerification(result.data.verification);
      form.reset(next);
      save.reset();
    }
  };

  if (mode.kind === "waiting") {
    return <WaitingProfile profile={mode.profile} stillPublic={mode.stillPublic} />;
  }

  // Only the server's answer moves the base and its ETag, also when it comes from the safe retry after an
  // unknown outcome. "Tiếp" goes on to GS03 once the save is confirmed.
  const applySaved = (saved: ProfileRevision | undefined) => {
    if (!saved) return;
    setBase(saved);
    form.reset(toFormValues(saved.data));
    if (nextAfterSave.current) onNext();
  };
  const submit = (next: boolean) =>
    form.handleSubmit(async (input) => {
      nextAfterSave.current = next;
      applySaved(await save.submit({ etag: mode.etag, values: input }));
    })();
  const command = { ...save, retry: async () => applySaved(await save.retry()) };
  // GS03 submits the newest draft, so "Tiếp" saves first unless that draft is already what is shown.
  const onPressNext = () => (!hasChanges && mode.profile?.status === "DRAFT" ? onNext() : submit(true));
  const busy = save.state === "sending";
  const blocked = save.state === "unknown" || stale;

  return (
    <KeyboardAvoidingView style={styles.fill} behavior="padding">
      <ScrollView contentContainerStyle={styles.form} keyboardShouldPersistTaps="handled">
        {save.state === "confirmed" && !hasChanges ? <Notice tone="success" message="Đã lưu nháp hồ sơ." /> : null}
        {mode.publicVersion ? <PublicVersion profile={mode.publicVersion} scope={mode.publicScope} /> : null}
        {mode.stillPublicVersion !== null ? (
          <Text style={styles.body}>{`Phụ huynh vẫn thấy bản hồ sơ đã duyệt trước của bạn (phiên bản ${mode.stillPublicVersion}).`}</Text>
        ) : null}
        {mode.editingPublic ? (
          <>
            <Text accessibilityRole="header" style={styles.heading}>
              Bản đang sửa
            </Text>
            <Notice
              tone="warning"
              message="Bạn đang sửa phần chuyên môn. Sau khi gửi lại, phần này phải được xác minh lại; nhãn xác minh cũ không dùng cho nội dung mới."
            />
          </>
        ) : null}
        {mode.verdict ? <Notice tone={mode.verdict.tone} message={mode.verdict.message} /> : null}
        {mode.label ? <StatusRow {...mode.label} /> : null}
        <Subject />
        <ProfileFields control={form.control} />
      </ScrollView>
      <StickyActionBar>
        {stale ? (
          <Notice
            tone="warning"
            message="Dữ liệu đã thay đổi từ lúc bạn mở. Tải lại để xem bản mới; phần bạn đã nhập hợp lệ được giữ lại."
            action={<Button variant="secondary" title="Tải lại" onPress={() => void reload()} />}
          />
        ) : (
          <>
            {otherErrors.map((message) => (
              <Notice key={message} tone="danger" message={message} />
            ))}
            {hasFieldErrors && save.state !== "unknown" ? (
              <Notice tone="danger" message="Vui lòng sửa các ô được đánh dấu rồi gửi lại." />
            ) : save.state === "confirmed" ? null : (
              <CommandStatus command={command} confirmedMessage="" />
            )}
          </>
        )}
        <Button title="Lưu nháp" busy={busy} disabled={blocked} onPress={() => void submit(false)} />
        <Button variant="secondary" title="Tiếp: tải minh chứng" disabled={busy || blocked} onPress={() => void onPressNext()} />
      </StickyActionBar>
    </KeyboardAvoidingView>
  );
}

function ProfileFields({ control }: { control: Control<TutorProfileFormInput, unknown, TutorProfileForm> }) {
  const description = useWatch({ control, name: "description" }) ?? "";
  return (
    <>
      <FormTextField
        control={control}
        name="description"
        label="Mô tả cách dạy"
        hint={`${formatThousands(lengthOf(description.trim()))}/3.000 ký tự, tối thiểu 50.`}
        multiline
      />
      <FormTextField
        control={control}
        name="experienceMonths"
        label="Kinh nghiệm liên quan (tháng)"
        hint="Từ 0 đến 600 tháng. 0 là hợp lệ nếu bạn mới bắt đầu. Phụ huynh thấy đây là thông tin tự khai."
        keyboardType="number-pad"
      />
      <FormTextField
        control={control}
        name="rateVnd60"
        label="Học phí cho 60 phút (VND)"
        hint="Phụ huynh thấy giá theo buổi, tính từ giá này. Đổi giá chỉ áp dụng cho đề nghị mới; lớp đã chốt giữ giá cũ."
        keyboardType="number-pad"
      />
    </>
  );
}

function StatusRow({ label, tone, icon }: (typeof STATUS_LABELS)[keyof typeof STATUS_LABELS]) {
  return (
    <View style={styles.statusRow}>
      <Text style={styles.muted}>Trạng thái</Text>
      <StatusLabel label={label} tone={tone} icon={icon} />
    </View>
  );
}

function Subject() {
  return (
    <View style={styles.group}>
      <Text style={styles.label}>Môn và khối</Text>
      <Text style={styles.body}>{SUBJECT_LABEL}</Text>
      <Text style={styles.caption}>Thí điểm hiện chỉ nhận môn này.</Text>
    </View>
  );
}

const price = (profile: TutorProfile) => `Học phí ${formatThousands(profile.rateVnd60)} ₫ / 60 phút.`;

/** Frame "Đã duyệt, đang sửa": the approved version parents see, above the form. */
function PublicVersion({ profile, scope }: { profile: TutorProfile; scope: string | null }) {
  return (
    <View style={styles.card}>
      <Text accessibilityRole="header" style={styles.heading}>{`Bản đang công khai (phiên bản ${profile.version})`}</Text>
      {scope ? (
        <View style={styles.verified}>
          <Icon name="verified_fill" color={colors.success} size={16} />
          <Text style={styles.verifiedText}>{`Đã xác minh: ${scope}`}</Text>
        </View>
      ) : null}
      <Text style={styles.body} numberOfLines={3}>
        {profile.description}
      </Text>
      <Text style={styles.muted}>{`Kinh nghiệm ${profile.experienceMonths} tháng (tự khai). ${price(profile)}`}</Text>
    </View>
  );
}

/** Frame "Đang chờ xác minh": read-only, no edit button (OP-24). */
function WaitingProfile({ profile, stillPublic }: { profile: TutorProfile; stillPublic: boolean }) {
  return (
    <ScrollView contentContainerStyle={styles.readOnly}>
      <StatusRow {...STATUS_LABELS.PENDING} />
      <Text style={styles.body}>
        {stillPublic
          ? // OP-24: parents keep seeing the approved version, so "Phụ huynh chưa thấy hồ sơ" would be wrong (Đính, Q8 on #14).
            "Hồ sơ đã gửi. Bản đã gửi được giữ nguyên để người xác minh xem. Phụ huynh vẫn thấy bản hồ sơ đã duyệt trước của bạn."
          : "Hồ sơ đã gửi. Bản đã gửi được giữ nguyên để người xác minh xem. Phụ huynh chưa thấy hồ sơ cho tới khi được xác minh."}
      </Text>
      <Subject />
      <View style={styles.group}>
        <Text style={styles.label}>Mô tả cách dạy</Text>
        <Text style={styles.body}>{profile.description}</Text>
      </View>
      <Text style={styles.muted}>{`Kinh nghiệm ${profile.experienceMonths} tháng. ${price(profile)}`}</Text>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  // Lets the form and the action bar reach the screen edges inside QueryScreen's padding.
  fill: { flex: 1, marginHorizontal: -spacing.md, marginBottom: -spacing.md },
  form: { gap: spacing.md, paddingHorizontal: spacing.md, paddingBottom: spacing.md },
  readOnly: { gap: spacing.md, paddingBottom: spacing.md },
  group: { gap: spacing.xs },
  statusRow: { flexDirection: "row", flexWrap: "wrap", alignItems: "center", gap: spacing.sm },
  card: { gap: spacing.sm, padding: spacing.md, borderRadius: radius.md, backgroundColor: colors.surface },
  verified: { flexDirection: "row", alignItems: "center", gap: spacing.xs },
  verifiedText: { ...typography.label, color: colors.success, flexShrink: 1 },
  heading: { ...typography.heading, color: colors.text },
  label: { ...typography.label, color: colors.text },
  body: { ...typography.body, color: colors.text },
  muted: { ...typography.body, color: colors.textMuted },
  caption: { ...typography.caption, color: colors.textMuted },
});
