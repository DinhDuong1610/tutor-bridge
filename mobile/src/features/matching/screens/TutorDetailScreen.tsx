// PH07 Chi tiết gia sư công khai (UC03, UC05; FR-05–FR-07, FR-12; BR07). Thiết kế: docs/design/ui/screens/PH07.md
import { ScrollView, StyleSheet, Text, View } from "react-native";
import { useNeed, type NeedRevision } from "@/features/needs/api";
import { useStudents } from "@/features/students/api";
import { ApiError } from "@/shared/api/errors";
import { Button } from "@/shared/ui/Button";
import { Notice } from "@/shared/ui/Notice";
import { PriceBlock } from "@/shared/ui/PriceBlock";
import { QueryScreen, type QueryState } from "@/shared/ui/QueryScreen";
import { StickyActionBar } from "@/shared/ui/StickyActionBar";
import { VerificationLabel } from "@/shared/ui/VerificationLabel";
import { colors, spacing, typography } from "@/shared/ui/theme";
import { useMatchingRun, useTutorPublic, type Candidate, type MatchingRun } from "../api";
import { ChildOfNeed } from "../components/ChildOfNeed";
import { detailScheduleText, verifiedLabels } from "../text";

type Child = { id: string; displayName: string; grade: number };
type TutorPublic = Candidate["tutor"];
type Props = {
  tutorId: string;
  runId: string;
  needId: string;
  onBack: () => void;
  /** Another child chosen in the switcher: the run is this child's need's, so the screen is left (as PH06). */
  onOtherChild: () => void;
  onManageStudents: () => void;
};

/**
 * One candidate of a run as the parent compared it on PH06: the tutor's public profile at the version the run
 * found, the server's price for this need and the common sessions. Nothing private is read or shown: no photo,
 * contact or evidence (BR07). The current public profile is read too, so a profile hidden or changed since the
 * run shows "Không còn khả dụng" instead of terms that no longer hold.
 */
export function TutorDetailScreen({ tutorId, runId, needId, onBack, onOtherChild, onManageStudents }: Props) {
  const run = useMatchingRun(runId);
  const current = useTutorPublic(tutorId);
  const need = useNeed(needId);
  const students = useStudents();
  // A 404 on the current profile is a state of this screen (hidden or gone), not a failed read.
  const hidden = current.error instanceof ApiError && current.error.kind === "not-found";
  const errors = [run.error, hidden ? null : current.error, need.error, students.error].filter((error) => error !== null);
  const query: QueryState<{ run: MatchingRun; current: TutorPublic | null; need: NeedRevision; students: Child[] }> = {
    isPending: errors.length === 0 && (run.isPending || (current.isPending && !hidden) || need.isPending || students.isPending),
    isError: errors.length > 0,
    error: errors[0] ?? null,
    data:
      run.data && (current.data || hidden) && need.data && students.data
        ? {
            run: run.data,
            // TanStack Query keeps the last 200 in `data` after a reread fails, so a profile hidden since must
            // not pass as current (Codex, #275).
            current: hidden ? null : (current.data ?? null),
            need: need.data,
            students: students.data.pages.flatMap((page) => page.items),
          }
        : undefined,
    refetch: () => Promise.all([run.refetch(), current.refetch(), need.refetch(), students.refetch()]),
  };
  return (
    <QueryScreen title="Thông tin gia sư" onBack={onBack} query={query}>
      {(loaded) => {
        const candidate = loaded.run.candidates.find((c) => c.tutor.tutorId === tutorId);
        const child = loaded.students.find((s) => s.id === loaded.need.data.studentId);
        // Not a candidate of this run, hidden since, or a newer profile version than the one compared. A run of
        // an older version of the need is out too: its price is for that version's duration, which getNeed no
        // longer returns, and PH06 shows such a run for reference only (Codex, #274).
        const available =
          candidate !== undefined &&
          loaded.run.needVersionId === loaded.need.data.versionId &&
          loaded.current !== null &&
          loaded.current.profileVersionId === candidate.tutor.profileVersionId;
        return (
          <View style={styles.fill}>
            <ChildOfNeed
              students={loaded.students}
              childId={loaded.need.data.studentId}
              onOtherChild={onOtherChild}
              onManageStudents={onManageStudents}
            />
            {available ? (
              <Detail
                candidate={candidate}
                childName={child?.displayName ?? null}
                durationMinutes={loaded.need.data.draft.durationMinutes ?? 0}
              />
            ) : (
              <>
                <ScrollView contentContainerStyle={styles.content}>
                  <Notice tone="danger" message="Không tìm thấy nội dung này. Có thể nó đã bị xóa hoặc bạn không có quyền xem." />
                  <Text style={styles.bodyMuted}>Hồ sơ có thể đã ẩn hoặc thay đổi. Quay lại danh sách và tìm lại để có kết quả mới.</Text>
                </ScrollView>
                <StickyActionBar>
                  <Button title="Quay lại danh sách" onPress={onBack} />
                </StickyActionBar>
              </>
            )}
          </View>
        );
      }}
    </QueryScreen>
  );
}

function Detail({ candidate, childName, durationMinutes }: { candidate: Candidate; childName: string | null; durationMinutes: number }) {
  const { tutor } = candidate;
  const verified = verifiedLabels(tutor.verificationScopes);
  // "Gửi đề nghị" comes with PH08 in #72 (Đính, #71, 28/11/2026): no bar until then.
  return (
    <ScrollView contentContainerStyle={styles.content}>
      <Text accessibilityRole="header" style={styles.name}>
        {tutor.displayName}
      </Text>
      <View style={styles.labels}>
        {verified.labels.map((label) => (
          <VerificationLabel key={label} kind="verified" text={label} />
        ))}
        {verified.more ? <VerificationLabel kind="verified" text={verified.more} /> : null}
        <VerificationLabel kind="self" text={`Kinh nghiệm ${tutor.experienceMonths} tháng (tự khai)`} />
      </View>
      <Text style={styles.caption}>Nhãn xác minh chỉ nói phạm vi đã kiểm minh chứng, không bảo đảm chất lượng giảng dạy.</Text>
      <View style={styles.section}>
        <Text style={styles.heading}>{childName ? `Học phí cho nhu cầu của ${childName}` : "Học phí cho nhu cầu"}</Text>
        <PriceBlock priceVndSession={candidate.priceVndSession} durationMinutes={durationMinutes} rateVnd60={tutor.rateVnd60} />
      </View>
      <View style={styles.section}>
        <Text style={styles.heading}>Cách dạy (tự giới thiệu)</Text>
        <Text style={styles.body}>{tutor.description}</Text>
      </View>
      <View style={styles.section}>
        <Text style={styles.heading}>Giờ chung trong chu kỳ</Text>
        <Text style={styles.body}>{detailScheduleText(candidate.commonSchedule)}</Text>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  content: { gap: spacing.lg, padding: spacing.md },
  name: { ...typography.title, color: colors.text },
  labels: { gap: spacing.xs },
  section: { gap: spacing.xs },
  heading: { ...typography.heading, color: colors.text },
  body: { ...typography.body, color: colors.text },
  bodyMuted: { ...typography.body, color: colors.textMuted },
  caption: { ...typography.caption, color: colors.textMuted },
});
