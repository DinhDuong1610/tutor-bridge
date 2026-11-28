// GS04 Lịch rảnh và ngoại lệ (UC07, FR-08, BR04, OP-26). Thiết kế: docs/design/ui/screens/GS04.md
// PR 1 of #69: no "đã có buổi" chip (#69 Q1) and no "Ngoại lệ" section (#69 Q2) yet, both TBD Đính.
import { useState } from "react";
import { KeyboardAvoidingView, ScrollView, StyleSheet, Text, View } from "react-native";
import { WeekAvailability } from "@/features/calendar/components/WeekAvailability";
import { readWeek, vietnamDate, type WeekPattern } from "@/features/calendar/weekly";
import { Button } from "@/shared/ui/Button";
import { CommandStatus } from "@/shared/ui/CommandStatus";
import { Notice } from "@/shared/ui/Notice";
import { QueryScreen } from "@/shared/ui/QueryScreen";
import { StickyActionBar } from "@/shared/ui/StickyActionBar";
import { TimeChip } from "@/shared/ui/TimeChip";
import { colors, spacing, typography } from "@/shared/ui/theme";
import { useReplaceTutorCalendar, useTutorCalendar, type CalendarRevision } from "../api";
import { applyRange, draftCount, freeCount, limitNotice, sameWeek, weeklySummary, windowsToSave } from "../plan";

type Props = {
  onBack: () => void;
  /** The clock, so a test can fix "today"; the 56 days of OP-26 start on today's date in Vietnam. */
  now?: () => Date;
};

type Step = "edit" | "review";

/**
 * Loading, lost access and the end of the session come from QueryScreen. The screen edits the weekly pattern
 * read from the stored calendar, reviews it, and replaces the calendar against the ETag it was read with; a
 * stale ETag (412) is never merged on the device (INV-23): "Tải lại" reads the newest calendar and keeps the
 * pattern being edited.
 */
export function TutorCalendarScreen({ onBack, now = () => new Date() }: Props) {
  const query = useTutorCalendar();
  const [step, setStep] = useState<Step>("edit");
  const review = step === "review";
  return (
    <QueryScreen
      title={review ? "Xem lại lịch rảnh" : "Lịch rảnh"}
      onBack={review ? () => setStep("edit") : onBack}
      query={query}
    >
      {(loaded) => <CalendarEditor latest={loaded} onReload={query.refetch} step={step} onStep={setStep} now={now} />}
    </QueryScreen>
  );
}

function CalendarEditor({
  latest,
  onReload,
  step,
  onStep,
  now,
}: {
  /** The query's newest answer, every render; `null` before the first save. */
  latest: CalendarRevision | null;
  onReload: () => Promise<{ isSuccess: boolean; data?: CalendarRevision | null }>;
  step: Step;
  onStep: (step: Step) => void;
  now: () => Date;
}) {
  // Read on every render, so a screen left open past midnight moves its 56 days with the date.
  const today = vietnamDate(now());
  // The revision the screen was filled from, as GS02: a background refetch must not move the ETag under what
  // the tutor is editing. It moves to the server's answer after a save, or to the newest one on "Tải lại".
  const [base, setBase] = useState<CalendarRevision | null>(latest);
  const [seenEtag, setSeenEtag] = useState<string | null>(latest?.etag ?? null);
  const stored = base?.data.windows ?? [];
  const read = readWeek(stored, today);
  const [value, setValue] = useState<WeekPattern>(read.available);
  const save = useReplaceTutorCalendar();

  const hasChanges = !sameWeek(value, read.available);
  const newestEtag = latest?.etag ?? null;
  if (newestEtag !== seenEtag) {
    setSeenEtag(newestEtag);
    if (latest && !hasChanges && save.state !== "sending" && save.state !== "unknown") {
      setBase(latest);
      setValue(readWeek(latest.data.windows, today).available);
    }
  }

  const windows = windowsToSave(value, read.preferred, stored, today);
  const limit = limitNotice(windows.length);
  const drafts = draftCount(value, read.available);
  const stale = save.state === "failed" && save.error?.kind === "stale";
  const keptBlocked = windows.some((w) => w.kind === "BLOCKED");

  // Only the server's answer moves the base and its ETag, also when it comes from the retry after an unknown
  // outcome; the screen then shows the saved calendar.
  const applySaved = (saved: CalendarRevision | undefined) => {
    if (!saved) return;
    setBase(saved);
    setSeenEtag(saved.etag);
    setValue(readWeek(saved.data.windows, today).available);
    onStep("edit");
  };
  // Spread again from the date at the press: the server takes windows from the start of today only, so the
  // windows of a render before midnight would be refused. A retry still resends the first body (INV-20).
  const confirm = async () => {
    const day = vietnamDate(now());
    const sent = windowsToSave(value, readWeek(stored, day).preferred, stored, day);
    applySaved(await save.submit({ etag: base?.etag ?? null, windows: sent }));
  };
  const command = { ...save, retry: async () => applySaved(await save.retry()) };
  // The pattern being edited is kept: only the base and its ETag move to the newest calendar (`04`, bản cũ).
  const reload = async () => {
    const result = await onReload();
    if (result.isSuccess && result.data !== undefined) {
      setBase(result.data);
      setSeenEtag(result.data?.etag ?? null);
      save.reset();
      onStep("edit");
    }
  };
  const discard = () => {
    setValue(read.available);
    save.reset();
  };

  const staleNotice = stale ? (
    <Notice
      tone="warning"
      message="Dữ liệu đã thay đổi từ lúc bạn mở. Tải lại để xem bản mới; phần bạn đã nhập hợp lệ được giữ lại."
      action={<Button variant="secondary" title="Tải lại" onPress={() => void reload()} />}
    />
  ) : null;

  if (step === "review") {
    const busy = save.state === "sending";
    return (
      <View style={styles.fill}>
        <ScrollView contentContainerStyle={styles.content}>
          <Text accessibilityRole="header" style={styles.heading}>
            {applyRange(today)}
          </Text>
          <Text style={styles.body}>{weeklySummary(value)}</Text>
          <Text style={styles.muted}>{`Tổng ${freeCount(windows)} khoảng rảnh trong 8 tuần (tối đa 200).`}</Text>
          {freeCount(windows) === 0 ? (
            // TBD #69 (Đính): no approved copy for confirming a calendar with no free time; this is the proposal.
            <Notice tone="warning" message="Sau khi xác nhận, phụ huynh sẽ không tìm thấy bạn cho tới khi bạn thêm lịch rảnh." />
          ) : null}
          <Notice tone="info" message="Buổi đã đặt giữ nguyên. Lịch rảnh mới chỉ dùng cho đề nghị mới." />
        </ScrollView>
        <StickyActionBar>
          {staleNotice ?? <CommandStatus command={command} confirmedMessage="" />}
          <Button
            title="Xác nhận lịch rảnh"
            busy={busy}
            disabled={stale || save.state === "unknown"}
            onPress={() => void confirm()}
          />
          <Button variant="secondary" title="Sửa tiếp" disabled={busy || save.state === "unknown"} onPress={() => onStep("edit")} />
        </StickyActionBar>
      </View>
    );
  }

  // No free time stored: the first visit, or a calendar whose free time was all removed. Either way parents do
  // not find the tutor, as the approved empty notice says.
  const noFreeTime = freeCount(stored) === 0;
  const empty = noFreeTime && !hasChanges;
  return (
    <KeyboardAvoidingView style={styles.fill} behavior="padding">
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        {staleNotice}
        {save.state === "confirmed" && !hasChanges && !noFreeTime ? (
          <Notice tone="success" message="Đã xác nhận lịch rảnh. Phụ huynh tìm gia sư sẽ thấy các giờ này." />
        ) : null}
        {empty ? (
          <Notice
            tone="info"
            message="Bạn chưa có lịch rảnh. Phụ huynh chỉ thấy bạn khi có lịch rảnh đã xác nhận, đủ cho chu kỳ 28 ngày của họ."
          />
        ) : null}
        {drafts > 0 ? (
          <Notice
            tone="warning"
            message={`Bạn có ${drafts} khung giờ chưa xác nhận. Phụ huynh chưa tìm thấy bạn trong các giờ này.`}
          />
        ) : null}
        {limit ? <Notice tone={limit.blocks ? "danger" : "warning"} message={limit.message} /> : null}
        {read.irregular ? (
          // TBD #69 Q3 (Đính): the wording approved for PH04 (#70 Q2) speaks of the child; this is the proposal.
          <Notice
            tone="warning"
            message={`Lịch rảnh của bạn có ngày khác mẫu tuần. Lưu ở đây sẽ thay toàn bộ lịch bằng mẫu tuần dưới đây.${
              keptBlocked ? " Ngày nghỉ và giờ bận đã có được giữ nguyên." : ""
            }`}
          />
        ) : null}
        {empty ? null : <Legend />}
        <WeekAvailability value={value} onChange={setValue} saved={read.available} />
      </ScrollView>
      {noFreeTime || hasChanges ? (
        <StickyActionBar>
          <Button
            variant={hasChanges ? "primary" : "secondary"}
            title="Xem lại"
            // Removing every slot of a stored calendar is a change to save (Đính, #263 review): the calendar is
            // replaced with no free time, so parents stop finding the tutor in the old hours.
            disabled={!hasChanges || limit?.blocks === true}
            onPress={() => {
              save.reset();
              onStep("review");
            }}
          />
          {hasChanges ? <Button variant="secondary" title="Bỏ thay đổi" onPress={discard} /> : null}
        </StickyActionBar>
      ) : null}
    </KeyboardAvoidingView>
  );
}

/** The lead and the chip key of the approved frames; the "đã có buổi" line comes with its chip (#69 Q1). */
function Legend() {
  return (
    <View style={styles.legend}>
      <Text style={styles.body}>
        Lịch rảnh lặp theo tuần, áp dụng cho 8 tuần tới, giờ Việt Nam. Sửa lịch rảnh không hủy buổi đã đặt.
      </Text>
      <View style={styles.key}>
        <TimeChip label="18:00–21:00" variant="effective" day="" />
        <Text style={styles.caption}>Rảnh đang hiệu lực: phụ huynh tìm thấy bạn trong giờ này.</Text>
      </View>
      <View style={styles.key}>
        <TimeChip label="07:00–09:00" variant="draft" day="" />
        <Text style={styles.caption}>Đang sửa, chưa xác nhận: chưa dùng khi lọc.</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  content: { gap: spacing.md, paddingBottom: spacing.lg },
  heading: { ...typography.heading, color: colors.text },
  body: { ...typography.body, color: colors.text },
  muted: { ...typography.body, color: colors.textMuted },
  caption: { ...typography.caption, color: colors.text },
  legend: { gap: spacing.sm },
  key: { gap: spacing.xs },
});
