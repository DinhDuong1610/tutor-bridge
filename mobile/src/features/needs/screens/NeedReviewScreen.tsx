// PH05 Xem lại và xác nhận nhu cầu (UC04, FR-04, AC-UC04-01). Thiết kế: docs/design/ui/screens/PH05.md
import { useState } from "react";
import { ScrollView, StyleSheet, Text, View } from "react-native";
import { useStudentCalendar } from "@/features/calendar/api";
import { readWeek, vietnamDate } from "@/features/calendar/weekly";
import { useStudents } from "@/features/students/api";
import { Button } from "@/shared/ui/Button";
import { CommandStatus } from "@/shared/ui/CommandStatus";
import { ConfirmSheet } from "@/shared/ui/ConfirmSheet";
import { CycleStrip } from "@/shared/ui/CycleStrip";
import { Notice } from "@/shared/ui/Notice";
import { QueryBody, QueryScreen, type QueryState } from "@/shared/ui/QueryScreen";
import { StickyActionBar } from "@/shared/ui/StickyActionBar";
import { colors, spacing, typography } from "@/shared/ui/theme";
import { useConfirmNeed, useNeed, type NeedRevision } from "../api";
import { FREE_TIME, hasFreeTime } from "../freeTime";
import { budgetText, cycleText, sessionText, weekText } from "../review";
import { missingFields } from "../summary";

type Child = { id: string; displayName: string; grade: number };
type Props = {
  needId: string;
  onBack: () => void;
  /** "Sửa": PH04 for this need. Editing a confirmed need makes a new draft (UC04). */
  onEdit: (needId: string) => void;
  /** "Tìm gia sư": PH06 for this need (#71). */
  onFindTutors: (needId: string) => void;
  /** The clock, so a test can fix "today": the child's weekly pattern is read from today on. */
  now?: () => Date;
};

/**
 * Shows the need as the server holds it, never a copy on the device (AC-UC04-01), and confirms exactly that
 * version: the If-Match is the ETag of what is on screen. A stale ETag (412) is reloaded, never merged (INV-23).
 */
export function NeedReviewScreen({ needId, onBack, onEdit, onFindTutors, now = () => new Date() }: Props) {
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
  const confirmed = need.data?.data.status === "CONFIRMED";
  return (
    <QueryScreen title={confirmed ? "Nhu cầu đã xác nhận" : "Xem lại nhu cầu"} onBack={onBack} query={query}>
      {(loaded) => (
        <Review
          revision={loaded.need}
          child={loaded.students.find((child) => child.id === loaded.need.data.studentId) ?? null}
          onReload={async () => {
            await need.refetch();
          }}
          onEdit={onEdit}
          onFindTutors={onFindTutors}
          now={now}
        />
      )}
    </QueryScreen>
  );
}

function Review({
  revision,
  child,
  onReload,
  onEdit,
  onFindTutors,
  now,
}: {
  revision: NeedRevision;
  /** A child past the first page of 50 is not in the list: the row then shows no name. */
  child: Child | null;
  onReload: () => Promise<void>;
  onEdit: (needId: string) => void;
  onFindTutors: (needId: string) => void;
  now: () => Date;
}) {
  const calendar = useStudentCalendar(revision.data.studentId);
  const confirm = useConfirmNeed();
  const [asking, setAsking] = useState(false);
  const need = revision.data;
  const draft = need.draft;
  const stale = confirm.state === "failed" && confirm.error?.kind === "stale";
  const busy = confirm.state === "sending";

  const reload = async () => {
    await onReload();
    confirm.reset();
  };
  const send = () => {
    setAsking(false);
    void confirm.submit({ needId: need.id, etag: revision.etag });
  };

  return (
    <QueryBody query={calendar}>
      {(stored) => {
        const windows = stored?.data.windows ?? [];
        const week = readWeek(windows, vietnamDate(now()));
        const missing = [...missingFields(draft), ...(hasFreeTime(windows) ? [] : [FREE_TIME])];
        const complete = missing.length === 0;
        return (
          <View style={styles.fill}>
            <ScrollView contentContainerStyle={styles.content}>
              {stale ? (
                <Notice
                  tone="warning"
                  message="Dữ liệu đã thay đổi từ lúc bạn mở. Tải lại để xem bản mới; phần bạn đã nhập hợp lệ được giữ lại."
                  action={<Button variant="secondary" title="Tải lại" onPress={() => void reload()} />}
                />
              ) : null}
              {need.status === "CONFIRMED" && confirm.state === "confirmed" ? (
                <Notice tone="success" message="Đã xác nhận nhu cầu. Bạn có thể tìm gia sư từ bản này." />
              ) : null}
              {need.status === "DRAFT" && !complete ? (
                <Notice
                  tone="warning"
                  message={`Đã lưu nháp. Cần thêm: ${missing.join(", ")}. Chưa thể tìm gia sư từ bản nháp.`}
                />
              ) : null}
              <Text style={styles.body}>Kiểm tra lại trước khi tìm gia sư. Đây là bản đang lưu trên hệ thống.</Text>
              <View>
                <Row label="Cho con" value={child ? `${child.displayName}, lớp ${child.grade}` : ""} />
                <Row label="Phạm vi" value="Toán 8, đại số, học trực tuyến" />
                <Row label="Mục tiêu" value={draft.goal ?? ""} />
                {draft.durationMinutes && draft.frequencyPer7Days ? <Row label="Mỗi buổi" value={sessionText(draft)} /> : null}
                {draft.budgetVndSession && draft.durationMinutes ? <Row label="Ngân sách tối đa" value={budgetText(draft)} /> : null}
                <Row label="Giờ con rảnh" value={weekText(week.available)} />
                <Row label="Giờ ưu tiên" value={weekText(week.preferred)} />
              </View>
              {draft.frequencyPer7Days && draft.cycleStartDate ? (
                <CycleStrip
                  heading={cycleText({ frequencyPer7Days: draft.frequencyPer7Days, cycleStartDate: draft.cycleStartDate })}
                  perWeek={draft.frequencyPer7Days}
                  caption="Vòng nét đứt: số buổi cần mỗi tuần. Giờ cụ thể có khi gửi đề nghị cho gia sư."
                />
              ) : null}
            </ScrollView>
            {need.status === "CLOSED" ? null : (
              <StickyActionBar>
                {stale || confirm.state === "confirmed" ? null : <CommandStatus command={confirm} confirmedMessage="" />}
                {need.status === "CONFIRMED" ? (
                  <Button title="Tìm gia sư" onPress={() => onFindTutors(need.id)} />
                ) : (
                  <>
                    <Button
                      title="Xác nhận nhu cầu"
                      busy={busy}
                      disabled={!complete || stale || confirm.state === "unknown"}
                      onPress={() => setAsking(true)}
                    />
                    <Button variant="secondary" title="Sửa" disabled={busy} onPress={() => onEdit(need.id)} />
                  </>
                )}
              </StickyActionBar>
            )}
            <ConfirmSheet
              visible={asking}
              title="Xác nhận nhu cầu này?"
              message="Gợi ý gia sư và đề nghị sẽ dùng đúng bản này. Sau khi xác nhận, muốn sửa sẽ tạo bản nháp mới; kết quả cũ được giữ để đối chiếu."
              confirmTitle="Xác nhận nhu cầu"
              onConfirm={send}
              onCancel={() => setAsking(false)}
            />
          </View>
        );
      }}
    </QueryBody>
  );
}

/** One line of the review: a small label over its value, as the approved frame lists them. */
function Row({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.row}>
      <Text style={styles.label}>{label}</Text>
      <Text style={styles.value}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  content: { gap: spacing.lg, paddingBottom: spacing.lg },
  body: { ...typography.body, color: colors.textMuted },
  row: { gap: spacing.xs, paddingVertical: spacing.sm, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.divider },
  label: { ...typography.caption, color: colors.textMuted },
  value: { ...typography.body, color: colors.text },
});
