// PH04 Nhập nhu cầu học kèm (UC04, UC07, FR-04, FR-19, BR02, BR04). Thiết kế: docs/design/ui/screens/PH04.md
// #70 PR 3a (the need's fields) and PR 3b (the child's free time). "Giờ ưu tiên" has no input yet (#70 Q6, TBD Đính).
import { zodResolver } from "@hookform/resolvers/zod";
import { useRef, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { KeyboardAvoidingView, ScrollView, StyleSheet, Text, View } from "react-native";
import { useReplaceStudentCalendar, useStudentCalendar, type CalendarRevision } from "@/features/calendar/api";
import { WeekAvailability } from "@/features/calendar/components/WeekAvailability";
import { readWeek, spreadWeek, vietnamDate, type WeekPattern } from "@/features/calendar/weekly";
import { useStudents } from "@/features/students/api";
import { useSelectedStudent } from "@/features/students/selection";
import { formatThousands } from "@/features/tutor-profile/schemas";
import { FormTextField } from "@/shared/form/FormTextField";
import { lengthOf } from "@/shared/form/length";
import { useServerErrors } from "@/shared/form/serverErrors";
import { Button } from "@/shared/ui/Button";
import { CommandStatus } from "@/shared/ui/CommandStatus";
import { Notice } from "@/shared/ui/Notice";
import { QueryBody, QueryScreen, type QueryState } from "@/shared/ui/QueryScreen";
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
import { FREE_TIME, hasFreeTime, sameWeek, shortfall } from "../freeTime";
import { missingFields } from "../summary";

const TITLE = "Nhu cầu học kèm";
const FIELDS = ["goal", "durationMinutes", "frequencyPer7Days", "budgetVndSession", "cycleStartDate"] as const;
const STALE = "Dữ liệu đã thay đổi từ lúc bạn mở. Tải lại để xem bản mới; phần bạn đã nhập hợp lệ được giữ lại.";

type Child = { id: string; displayName: string; grade: number };
type Common = {
  onBack: () => void;
  /** "Xem lại": PH05 for the saved need (#70 PR 4). */
  onReview: (needId: string) => void;
  /** The clock, so a test can fix "today"; the start date and the free time run from today's Vietnam date. */
  now?: () => Date;
};
type FormProps = {
  child: Child;
  /** The query's newest answer, every render; `null` for a new draft. */
  latest: NeedRevision | null;
  onReload: (() => Promise<{ isSuccess: boolean; data?: NeedRevision }>) | null;
  onReview: (needId: string) => void;
  now: () => Date;
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
  return <WithCalendar child={selected} latest={null} onReload={null} onReview={onReview} now={now} />;
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
        <WithCalendar
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

/** The child's calendar loads with the form: its free time is part of the draft PH04 saves (#16 Q1). */
function WithCalendar(props: FormProps) {
  const calendar = useStudentCalendar(props.child.id);
  return (
    <QueryBody query={calendar}>
      {(loaded) => (
        <NeedForm
          {...props}
          calendar={loaded}
          onReloadCalendar={async () => {
            const result = await calendar.refetch();
            return { isSuccess: result.isSuccess, data: result.data };
          }}
        />
      )}
    </QueryBody>
  );
}

function NeedForm({
  child,
  latest,
  onReload,
  onReview,
  now,
  calendar,
  onReloadCalendar,
}: FormProps & {
  /** The child's calendar as loaded; `null` before the first save. */
  calendar: CalendarRevision | null;
  onReloadCalendar: () => Promise<{ isSuccess: boolean; data?: CalendarRevision | null }>;
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

  // The child's free time: the weekly pattern read from the child's calendar, edited, and saved over 56 days
  // (OP-26). Saving replaces the whole calendar, as the approved warning says (#70 Q2); the stored PREFERRED
  // pattern is kept, since PH04 has no input for it yet (#70 Q6).
  const [calendarBase, setCalendarBase] = useState<CalendarRevision | null>(calendar);
  const stored = readWeek(calendarBase?.data.windows ?? [], today);
  const [week, setWeek] = useState<WeekPattern>(stored.available);
  const weekChanged = !sameWeek(week, stored.available);
  const saveCalendar = useReplaceStudentCalendar();
  const calendarStale = saveCalendar.state === "failed" && saveCalendar.error?.kind === "stale";
  const windows = spreadWeek(week, stored.preferred, today);

  // What the fields hold now, if they are valid: "Xem lại" needs a complete draft and enough free time.
  const parsed = schema.safeParse(values);
  const draftNow = parsed.success ? toDraftInput(parsed.data) : null;
  const missingNow = draftNow ? [...missingFields(draftNow), ...(hasFreeTime(windows) ? [] : [FREE_TIME])] : null;
  const notEnough = draftNow ? shortfall(windows, draftNow, now()) : null;
  const missingSaved = base
    ? [...missingFields(base.data.draft), ...(hasFreeTime(calendarBase?.data.windows ?? []) ? [] : [FREE_TIME])]
    : [];
  const busy = save.state === "sending" || saveCalendar.state === "sending";
  const blocked = save.state === "unknown" || stale || saveCalendar.state === "unknown" || calendarStale;
  const unsaved = !base || hasChanges || weekChanged;
  // Set by the button that started the save: "Xem lại" goes on to PH05 once both saves are confirmed.
  const reviewAfterSave = useRef(false);

  const applyNeed = (saved: NeedRevision) => {
    setBase(saved);
    setSeenEtag(saved.etag);
    form.reset(toFormValues(saved.data.draft));
  };
  // Huy, 28/11/2026: the need first, the child's calendar only once the need is confirmed; a failed calendar
  // save says the need is saved and retries the calendar alone.
  const saveFreeTime = async (need: NeedRevision) => {
    if (weekChanged) {
      const saved = await saveCalendar.submit({ studentId: child.id, etag: calendarBase?.etag ?? null, windows });
      if (!saved) return;
      setCalendarBase(saved);
      setWeek(readWeek(saved.data.windows, today).available);
    }
    if (reviewAfterSave.current) onReview(need.data.id);
  };
  const submit = (review: boolean) =>
    form.handleSubmit(async (input) => {
      reviewAfterSave.current = review;
      saveCalendar.reset();
      let need = base;
      if (!base || hasChanges) {
        const draft = toDraftInput(input);
        const saved = await save.submit(
          base ? { kind: "update", needId: base.data.id, etag: base.etag, draft } : { kind: "create", studentId: child.id, draft },
        );
        if (!saved) return;
        applyNeed(saved);
        need = saved;
      }
      if (need) await saveFreeTime(need);
    })();
  // A retry after an unknown outcome ends like the press that started it.
  const needCommand = {
    ...save,
    retry: async () => {
      const saved = await save.retry();
      if (!saved) return;
      applyNeed(saved);
      await saveFreeTime(saved);
    },
  };
  const calendarCommand = {
    ...saveCalendar,
    retry: async () => {
      const saved = await saveCalendar.retry();
      if (!saved) return;
      setCalendarBase(saved);
      setWeek(readWeek(saved.data.windows, today).available);
      if (reviewAfterSave.current && base) onReview(base.data.id);
    },
  };
  const reload = async () => {
    if (!onReload) return;
    const result = await onReload();
    if (result.isSuccess && result.data) {
      setBase(result.data);
      setSeenEtag(result.data.etag);
      save.reset();
    }
  };
  // The pattern being edited is kept: only the calendar's base and ETag move to the newest version.
  const reloadCalendar = async () => {
    const result = await onReloadCalendar();
    if (result.isSuccess && result.data !== undefined) {
      setCalendarBase(result.data);
      saveCalendar.reset();
    }
  };

  const goal = (values.goal ?? "").trim();
  const start = parsed.success ? parsed.data.cycleStartDate : undefined;
  const calendarFailed = saveCalendar.state === "failed" || saveCalendar.state === "unknown";

  return (
    <KeyboardAvoidingView style={styles.fill} behavior="padding">
      <ScrollView contentContainerStyle={styles.form} keyboardShouldPersistTaps="handled">
        {stale ? (
          <Notice tone="warning" message={STALE} action={<Button variant="secondary" title="Tải lại" onPress={() => void reload()} />} />
        ) : null}
        {calendarStale ? (
          <Notice tone="warning" message={STALE} action={<Button variant="secondary" title="Tải lại" onPress={() => void reloadCalendar()} />} />
        ) : null}
        {base && !unsaved && !stale && missingSaved.length > 0 ? (
          <Notice
            tone="warning"
            message={`Đã lưu nháp. Cần thêm: ${missingSaved.join(", ")}. Chưa thể tìm gia sư từ bản nháp.`}
          />
        ) : null}
        {save.state === "confirmed" && !unsaved && !calendarFailed && missingSaved.length === 0 ? (
          <Notice tone="success" message="Đã lưu nháp." />
        ) : null}
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
        <View style={styles.section}>
          <Text accessibilityRole="header" style={styles.heading}>
            Giờ con rảnh mỗi tuần
          </Text>
          <Text style={styles.caption}>Giờ Việt Nam. Chỉ gợi ý gia sư rảnh trùng giờ này cho đủ cả chu kỳ.</Text>
          {stored.irregular ? (
            <Notice
              tone="warning"
              message="Lịch rảnh của con có ngày khác mẫu tuần. Lưu ở đây sẽ thay toàn bộ lịch bằng mẫu tuần dưới đây."
            />
          ) : null}
          <WeekAvailability value={week} onChange={setWeek} saved={stored.available} />
          {notEnough ? <Notice tone="warning" message={notEnough} /> : null}
        </View>
        <View style={styles.section}>
          <Text accessibilityRole="header" style={styles.heading}>
            Giờ ưu tiên (không bắt buộc)
          </Text>
          <Text style={styles.caption}>Gia sư rảnh vào giờ ưu tiên được xếp trước. Không chọn cũng được.</Text>
        </View>
      </ScrollView>
      <StickyActionBar>
        {stale || calendarStale ? null : (
          <>
            {otherErrors.map((message) => (
              <Notice key={message} tone="danger" message={message} />
            ))}
            {hasFieldErrors && save.state !== "unknown" ? (
              <Notice tone="danger" message="Vui lòng sửa các ô được đánh dấu rồi gửi lại." />
            ) : save.state === "confirmed" ? null : (
              <CommandStatus command={needCommand} confirmedMessage="" />
            )}
            {calendarFailed ? (
              // TBD #70: no approved copy for a need saved without its free time (Huy's order, 28/11/2026).
              <Notice tone="warning" message="Đã lưu nhu cầu, chưa lưu giờ rảnh của con." />
            ) : null}
            {saveCalendar.state === "confirmed" || saveCalendar.state === "idle" ? null : (
              <CommandStatus command={calendarCommand} confirmedMessage="" />
            )}
          </>
        )}
        <Button
          title="Xem lại"
          disabled={busy || blocked || missingNow === null || missingNow.length > 0 || notEnough !== null}
          onPress={() => (base && !unsaved ? onReview(base.data.id) : void submit(true))}
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
  section: { gap: spacing.sm },
  label: { ...typography.label, color: colors.text },
  heading: { ...typography.heading, color: colors.text },
  childName: { ...typography.bodyStrong, color: colors.text },
  caption: { ...typography.caption, color: colors.textMuted },
});
