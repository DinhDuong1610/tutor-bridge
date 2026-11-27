// PH04 Nhập nhu cầu học kèm (UC04, FR-04, BR02). Thiết kế: docs/design/ui/screens/PH04.md
// PR 3a of #70: the need's fields only. The child's free time (WeekAvailability, replaceStudentCalendar) is PR 3b.
import { zodResolver } from "@hookform/resolvers/zod";
import { useRef, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { KeyboardAvoidingView, ScrollView, StyleSheet, Text, View } from "react-native";
import { vietnamDate } from "@/features/calendar/weekly";
import { useStudents } from "@/features/students/api";
import { useSelectedStudent } from "@/features/students/selection";
import { formatThousands } from "@/features/tutor-profile/schemas";
import { FormTextField } from "@/shared/form/FormTextField";
import { lengthOf } from "@/shared/form/length";
import { useServerErrors } from "@/shared/form/serverErrors";
import { Button } from "@/shared/ui/Button";
import { CommandStatus } from "@/shared/ui/CommandStatus";
import { Notice } from "@/shared/ui/Notice";
import { QueryScreen, type QueryState } from "@/shared/ui/QueryScreen";
import { StickyActionBar } from "@/shared/ui/StickyActionBar";
import { colors, spacing, typography } from "@/shared/ui/theme";
import { useNeed, useSaveNeedDraft, type NeedRevision } from "../api";
import { ChoiceGroup } from "../components/ChoiceGroup";
import {
  DURATIONS,
  FREQUENCIES,
  budgetLabel,
  draftFormSchema,
  startDateHint,
  toDraftInput,
  toFormValues,
  weekdayLabel,
  type DraftForm,
  type DraftFormValues,
} from "../draftForm";
import { missingFields } from "../summary";

const TITLE = "Nhu cầu học kèm";
const FIELDS = ["goal", "durationMinutes", "frequencyPer7Days", "budgetVndSession", "cycleStartDate"] as const;

type Child = { id: string; displayName: string; grade: number };
type Common = {
  onBack: () => void;
  /** "Xem lại": PH05 for the saved need (#70 PR 4). */
  onReview: (needId: string) => void;
  /** The clock, so a test can fix "today"; the start date runs from today's Vietnam date. */
  now?: () => Date;
};

/** `/parent/needs/new`: a new draft for the child chosen on PH01 or PH03 (StudentSwitcher). */
export function NewNeedScreen({ onBack, onReview, now = () => new Date() }: Common) {
  const students = useStudents();
  const query: QueryState<Child[]> = { ...students, data: students.data?.pages.flatMap((page) => page.items) };
  return (
    <QueryScreen title={TITLE} onBack={onBack} query={query}>
      {(children) => <NewNeed students={children} onReview={onReview} now={now} />}
    </QueryScreen>
  );
}

function NewNeed({ students, onReview, now }: { students: Child[]; onReview: (needId: string) => void; now: () => Date }) {
  const { selected } = useSelectedStudent(students);
  if (!selected) {
    return <Notice tone="info" message="Chưa có hồ sơ con nào." />;
  }
  return <NeedForm child={selected} latest={null} onReload={null} onReview={onReview} now={now} />;
}

/** `/parent/needs/[needId]/edit`: the need's current draft, saved against its ETag (INV-23). */
export function EditNeedScreen({ needId, onBack, onReview, now = () => new Date() }: Common & { needId: string }) {
  const need = useNeed(needId);
  const students = useStudents();
  const errors = [need.error, students.error].filter((error) => error !== null);
  const query: QueryState<{ need: NeedRevision; students: Child[] }> = {
    isPending: errors.length === 0 && (need.isPending || students.isPending),
    isError: errors.length > 0,
    error: errors[0] ?? null,
    data: need.data && students.data ? { need: need.data, students: students.data.pages.flatMap((page) => page.items) } : undefined,
    refetch: () => Promise.all([need.refetch(), students.refetch()]),
  };
  return (
    <QueryScreen title={TITLE} onBack={onBack} query={query}>
      {(loaded) => (
        <NeedForm
          // A child past the first page of 50 is not in the list: the need still opens, without the child's name.
          child={loaded.students.find((child) => child.id === loaded.need.data.studentId) ?? { id: loaded.need.data.studentId, displayName: "", grade: 8 }}
          latest={loaded.need}
          onReload={async () => {
            const result = await need.refetch();
            return { isSuccess: result.isSuccess, data: result.data };
          }}
          onReview={onReview}
          now={now}
        />
      )}
    </QueryScreen>
  );
}

function NeedForm({
  child,
  latest,
  onReload,
  onReview,
  now,
}: {
  child: Child;
  /** The query's newest answer, every render; `null` for a new draft. */
  latest: NeedRevision | null;
  onReload: (() => Promise<{ isSuccess: boolean; data?: NeedRevision }>) | null;
  onReview: (needId: string) => void;
  now: () => Date;
}) {
  const today = vietnamDate(now());
  const [schema] = useState(() => draftFormSchema(today));
  // The revision the form was filled from, as GS02: a background refetch must not move the ETag under what the
  // parent is typing. It moves to the server's answer after a save, or to the newest one on "Tải lại".
  const [base, setBase] = useState<NeedRevision | null>(latest);
  const [seenEtag, setSeenEtag] = useState<string | null>(latest?.etag ?? null);
  const loadedValues = toFormValues(base?.data.draft ?? {});
  const form = useForm<DraftFormValues, unknown, DraftForm>({
    resolver: zodResolver(schema),
    values: loadedValues,
    resetOptions: { keepDirtyValues: true },
  });
  const save = useSaveNeedDraft();
  const otherErrors = useServerErrors(save.error, form.setError, FIELDS);
  const values = useWatch({ control: form.control });
  const hasChanges = FIELDS.some((name) => (values[name] ?? "").trim() !== loadedValues[name]);
  const hasFieldErrors = Object.keys(form.formState.errors).length > 0;
  const stale = save.state === "failed" && save.error?.kind === "stale";

  const newestEtag = latest?.etag ?? null;
  if (newestEtag !== seenEtag) {
    setSeenEtag(newestEtag);
    const safe = !hasChanges && save.state !== "sending" && save.state !== "unknown";
    if (latest && newestEtag !== (base?.etag ?? null) && safe) setBase(latest);
  }

  // What the fields hold now, if they are valid: "Xem lại" needs a complete draft (PH04 "Hành vi").
  const parsed = schema.safeParse(values);
  const missingNow = parsed.success ? missingFields(toDraftInput(parsed.data)) : null;
  const missingSaved = base ? missingFields(base.data.draft) : [];
  const busy = save.state === "sending";
  const blocked = save.state === "unknown" || stale;
  // Set by the button that started the save: "Xem lại" goes on to PH05 once it is confirmed.
  const reviewAfterSave = useRef(false);

  const applySaved = (saved: NeedRevision | undefined, review: boolean) => {
    if (!saved) return;
    setBase(saved);
    setSeenEtag(saved.etag);
    form.reset(toFormValues(saved.data.draft));
    if (review) onReview(saved.data.id);
  };
  const submit = (review: boolean) =>
    form.handleSubmit(async (input) => {
      reviewAfterSave.current = review;
      const draft = toDraftInput(input);
      const result = await save.submit(
        base
          ? { kind: "update", needId: base.data.id, etag: base.etag, draft }
          : { kind: "create", studentId: child.id, draft },
      );
      applySaved(result, review);
    })();
  // A retry after an unknown outcome ends like the press that started it.
  const command = { ...save, retry: async () => applySaved(await save.retry(), reviewAfterSave.current) };
  const reload = async () => {
    if (!onReload) return;
    const result = await onReload();
    if (result.isSuccess && result.data) {
      setBase(result.data);
      setSeenEtag(result.data.etag);
      save.reset();
    }
  };

  const goal = (values.goal ?? "").trim();
  const start = parsed.success ? parsed.data.cycleStartDate : undefined;

  return (
    <KeyboardAvoidingView style={styles.fill} behavior="padding">
      <ScrollView contentContainerStyle={styles.form} keyboardShouldPersistTaps="handled">
        {stale ? (
          <Notice
            tone="warning"
            message="Dữ liệu đã thay đổi từ lúc bạn mở. Tải lại để xem bản mới; phần bạn đã nhập hợp lệ được giữ lại."
            action={<Button variant="secondary" title="Tải lại" onPress={() => void reload()} />}
          />
        ) : null}
        {base && !hasChanges && !stale && missingSaved.length > 0 ? (
          <Notice
            tone="warning"
            message={`Đã lưu nháp. Cần thêm: ${missingSaved.join(", ")}. Chưa thể tìm gia sư từ bản nháp.`}
          />
        ) : null}
        {save.state === "confirmed" && !hasChanges && missingSaved.length === 0 ? <Notice tone="success" message="Đã lưu nháp." /> : null}
        <View style={styles.child}>
          <Text style={styles.label}>Cho con</Text>
          {child.displayName ? <Text style={styles.childName}>{`${child.displayName}, lớp ${child.grade}`}</Text> : null}
          <Text style={styles.caption}>Toán 8, đại số, học trực tuyến. Thí điểm hiện chỉ có phạm vi này.</Text>
        </View>
        <FormTextField
          control={form.control}
          name="goal"
          label="Mục tiêu học kèm"
          hint={`${formatThousands(lengthOf(goal))}/2.000 ký tự, tối thiểu 20.`}
          multiline
        />
        <ChoiceGroup
          control={form.control}
          name="durationMinutes"
          label="Thời lượng mỗi buổi"
          options={DURATIONS.map((value) => ({ value, label: `${value} phút` }))}
        />
        <ChoiceGroup
          control={form.control}
          name="frequencyPer7Days"
          label="Số buổi mỗi tuần"
          hint="Đủ số buổi trong mỗi tuần của chu kỳ 28 ngày, tối đa 12 buổi."
          options={FREQUENCIES.map((value) => ({ value, label: `${value} buổi` }))}
        />
        <FormTextField
          control={form.control}
          name="budgetVndSession"
          label={budgetLabel(values.durationMinutes ?? "")}
          hint={
            values.durationMinutes
              ? `Chỉ gợi ý gia sư có giá một buổi ${values.durationMinutes} phút không vượt mức này. Đổi thời lượng thì ngân sách tính theo thời lượng mới.`
              : "Đổi thời lượng thì ngân sách tính theo thời lượng mới."
          }
          keyboardType="number-pad"
        />
        <FormTextField
          control={form.control}
          name="cycleStartDate"
          label="Ngày bắt đầu chu kỳ"
          // TBD #70 Q5: typed as dd/mm/yyyy, the weekday shown once the date is valid; no approved picker frame.
          placeholder="dd/mm/yyyy"
          hint={start ? `${weekdayLabel(start)}. ${startDateHint(today)}` : startDateHint(today)}
          keyboardType="numbers-and-punctuation"
        />
      </ScrollView>
      <StickyActionBar>
        {stale ? null : (
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
        <Button
          title="Xem lại"
          disabled={busy || blocked || missingNow === null || missingNow.length > 0}
          onPress={() => (base && !hasChanges ? onReview(base.data.id) : void submit(true))}
        />
        <Button variant="secondary" title="Lưu nháp" busy={busy} disabled={blocked} onPress={() => void submit(false)} />
      </StickyActionBar>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  form: { gap: spacing.lg, paddingBottom: spacing.lg },
  child: { gap: spacing.xs },
  label: { ...typography.label, color: colors.text },
  childName: { ...typography.bodyStrong, color: colors.text },
  caption: { ...typography.caption, color: colors.textMuted },
});
