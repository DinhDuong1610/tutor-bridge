// PH06 Kết quả và so sánh gia sư (UC05, FR-09–FR-14, BR01, BR03). Thiết kế: docs/design/ui/screens/PH06.md
import { formatVnd } from "@tutorbridge/shared";
import { useEffect, useState, type ReactNode } from "react";
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { useNeed, type Need, type NeedRevision } from "@/features/needs/api";
import { useStudents } from "@/features/students/api";
import { Button } from "@/shared/ui/Button";
import { CommandStatus } from "@/shared/ui/CommandStatus";
import { Notice } from "@/shared/ui/Notice";
import { PriceBlock } from "@/shared/ui/PriceBlock";
import { QueryScreen, type QueryState } from "@/shared/ui/QueryScreen";
import { StickyActionBar } from "@/shared/ui/StickyActionBar";
import { VerificationLabel } from "@/shared/ui/VerificationLabel";
import { colors, minTouchSize, radius, spacing, typography } from "@/shared/ui/theme";
import { useCreateMatchingRun, type Candidate } from "../api";
import { ChildOfNeed } from "../components/ChildOfNeed";
import { commonScheduleText, shownReasons, verifiedText } from "../text";

type Child = { id: string; displayName: string; grade: number };
type Props = {
  needId: string;
  onBack: () => void;
  /** "Sửa nhu cầu" when no tutor fits: PH04 of this need. */
  onEdit: (needId: string) => void;
  /** "Xem chi tiết": PH07 of this candidate of this run. */
  onOpenTutor: (tutorId: string, runId: string) => void;
  /** Another child chosen in the switcher: the need is this child's, so the screen is left (as CL02). */
  onOtherChild: () => void;
  onManageStudents: () => void;
  /** The clock, so a test can pass a run's expiry. */
  now?: () => Date;
};

/**
 * Runs matching B for the confirmed version on screen (BR03: the parent's tap is the explicit choice) and
 * shows the run as the server answered it. Nothing is ranked or scored here: the order and the reasons are
 * the server's (INV-37), and a result is no reservation (BR01).
 */
export function MatchesScreen({ needId, onBack, onEdit, onOpenTutor, onOtherChild, onManageStudents, now = () => new Date() }: Props) {
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
    <QueryScreen title="Gia sư phù hợp" onBack={onBack} query={query}>
      {(loaded) => (
        <Matches
          revision={loaded.need}
          students={loaded.students}
          onReload={async () => {
            await need.refetch();
          }}
          onBack={onBack}
          onEdit={onEdit}
          onOpenTutor={onOpenTutor}
          onOtherChild={onOtherChild}
          onManageStudents={onManageStudents}
          now={now}
        />
      )}
    </QueryScreen>
  );
}

function Matches({
  revision,
  students,
  onReload,
  onBack,
  onEdit,
  onOpenTutor,
  onOtherChild,
  onManageStudents,
  now,
}: {
  revision: NeedRevision;
  students: Child[];
  onReload: () => Promise<void>;
} & Omit<Props, "needId" | "now"> & { now: () => Date }) {
  const run = useCreateMatchingRun();
  const need = revision.data;
  // The terms of the version searched, kept with its run: a later version of the need (read again on focus)
  // must not change the duration or the conditions shown next to the old run's prices (Codex, #274).
  const [searched, setSearched] = useState<Need["draft"] | null>(null);
  const result = run.state === "confirmed" ? run.result : undefined;
  const expired = useExpired(result?.expiresAt, now);
  const search = () => {
    run.reset();
    setSearched(need.draft);
    void run.submit({ needId: need.id, needVersionId: need.versionId, etag: revision.etag });
  };
  // 412 (another revision) or 409 NEED_VERSION_CHANGED: the need changed since it loaded (INV-23).
  const stale = run.state === "failed" && (run.error?.kind === "stale" || run.error?.code === "NEED_VERSION_CHANGED");
  const reload = async () => {
    await onReload();
    run.reset();
  };

  const header = (
    <ChildOfNeed students={students} childId={need.studentId} onOtherChild={onOtherChild} onManageStudents={onManageStudents} />
  );

  if (need.status !== "CONFIRMED") {
    return (
      <Body header={header}>
        {need.status === "DRAFT" ? <Notice tone="warning" message="Chưa thể tìm gia sư từ bản nháp." /> : null}
      </Body>
    );
  }
  if (run.state === "sending") {
    return (
      <Body header={header}>
        <View style={styles.loading}>
          <ActivityIndicator size="large" color={colors.primary} />
          <Text style={styles.loadingText}>Đang tìm gia sư phù hợp…</Text>
        </View>
      </Body>
    );
  }
  // A run the server could not rank, or a refusal other than a changed need: one sentence for all (#71, Đính).
  if ((run.state === "failed" && !stale) || result?.status === "FAILED") {
    return (
      <Body header={header}>
        <Notice
          tone="danger"
          message="Không tìm được lúc này. Nhu cầu của bạn vẫn được giữ."
          action={<Button variant="secondary" title="Thử lại" onPress={search} />}
        />
      </Body>
    );
  }
  if (result) {
    const terms = searched ?? need.draft;
    // The need moved on (edited and confirmed again) after this run, or the run expired: its results are kept
    // for reference only, with or without tutors, and the one action is a new search (Codex, #274).
    const oldVersion = result.needVersionId !== need.versionId;
    const muted = oldVersion || expired;
    const mutedNotice = oldVersion ? (
      <Notice
        tone="warning"
        message="Đây là kết quả của bản nhu cầu cũ, chỉ để đối chiếu. Tìm lại từ bản đang hiệu lực để gửi đề nghị."
      />
    ) : expired ? (
      <Notice tone="warning" message="Kết quả này đã cũ. Tìm lại để có danh sách theo giá và lịch hiện tại." />
    ) : null;
    const searchAgain = muted ? (
      <StickyActionBar>
        <Button title={oldVersion ? "Tìm từ bản mới" : "Tìm lại"} onPress={search} />
      </StickyActionBar>
    ) : null;
    if (result.candidates.length === 0) {
      return (
        <Body
          header={header}
          bar={
            searchAgain ?? (
              <StickyActionBar>
                <Button title="Sửa nhu cầu" onPress={() => onEdit(need.id)} />
                <Button variant="secondary" title="Tìm lại sau" onPress={onBack} />
              </StickyActionBar>
            )
          }
        >
          {mutedNotice ?? <Text style={styles.heading}>Chưa có gia sư đáp ứng đủ điều kiện.</Text>}
          <Text style={styles.body}>Đã kiểm các điều kiện sau với mọi gia sư đang nhận lớp:</Text>
          <Text style={styles.bodyMuted}>• Phạm vi Toán 8, đại số, học trực tuyến</Text>
          <Text style={styles.bodyMuted}>{`• Giá một buổi ${terms.durationMinutes} phút không vượt ${vnd(terms.budgetVndSession)}`}</Text>
          <Text style={styles.bodyMuted}>{`• Rảnh đủ ${terms.frequencyPer7Days} buổi mỗi tuần trong cả 28 ngày, trùng giờ rảnh của con`}</Text>
          <Text style={styles.bodyMuted}>• Hồ sơ đã được xác minh</Text>
          {muted ? null : (
            <Text style={styles.body}>Bạn có thể nới ngân sách, thêm giờ rảnh hoặc giảm số buổi. Sửa sẽ tạo bản nhu cầu mới.</Text>
          )}
        </Body>
      );
    }
    return (
      <Body header={header} bar={searchAgain}>
        {mutedNotice ?? (
          <>
            <Notice
              tone="info"
              message="Danh sách lọc theo điều kiện của bạn, không dùng xếp hạng AI. Xếp theo kinh nghiệm tự khai và mức khớp giờ ưu tiên."
            />
            <Text style={styles.caption}>Gợi ý chưa giữ chỗ. Lớp chỉ được chốt khi gia sư chấp nhận đề nghị.</Text>
          </>
        )}
        {result.candidates.map((candidate) => (
          <TutorCard
            key={candidate.tutor.tutorId}
            candidate={candidate}
            durationMinutes={terms.durationMinutes ?? 0}
            muted={muted}
            onOpen={() => onOpenTutor(candidate.tutor.tutorId, result.id)}
          />
        ))}
      </Body>
    );
  }
  return (
    <Body
      header={header}
      bar={
        <StickyActionBar>
          {stale ? (
            <Notice
              tone="warning"
              message="Dữ liệu đã thay đổi từ lúc bạn mở. Tải lại để xem bản mới; phần bạn đã nhập hợp lệ được giữ lại."
              action={<Button variant="secondary" title="Tải lại" onPress={() => void reload()} />}
            />
          ) : (
            <CommandStatus command={run} confirmedMessage="" />
          )}
          <Button
            title="Xem danh sách lọc theo điều kiện"
            disabled={stale || run.state === "unknown"}
            onPress={search}
          />
        </StickyActionBar>
      }
    >
      <Text style={styles.heading}>{`Tìm gia sư cho nhu cầu "${need.draft.goal ?? ""}"`}</Text>
      <Text style={styles.body}>
        Hiện chưa dùng xếp hạng AI. Bạn sẽ thấy tối đa 5 gia sư đáp ứng đủ điều kiện: đúng phạm vi, giá một buổi trong ngân sách, rảnh đủ cả chu kỳ 28 ngày và hồ sơ đã được xác minh.
      </Text>
      <Text style={styles.bodyMuted}>
        Danh sách xếp theo kinh nghiệm tự khai và mức khớp giờ ưu tiên. Thứ tự không phải là đánh giá chất lượng giảng dạy.
      </Text>
    </Body>
  );
}

/** The switcher on top, the scrolling content, then the bar, as every PH06 frame lays them out. */
function Body({ header, bar, children }: { header: ReactNode; bar?: ReactNode; children: ReactNode }) {
  return (
    <View style={styles.fill}>
      {header}
      <ScrollView contentContainerStyle={styles.content}>{children}</ScrollView>
      {bar}
    </View>
  );
}

/** Figma "TutorCard": rank, name, labels, price, common time, reasons, "Xem chi tiết". Never a score. */
function TutorCard({
  candidate,
  durationMinutes,
  muted,
  onOpen,
}: {
  candidate: Candidate;
  durationMinutes: number;
  muted: boolean;
  onOpen: () => void;
}) {
  const { tutor } = candidate;
  const verified = verifiedText(tutor.verificationScopes);
  const reasons = shownReasons(candidate.reasons);
  return (
    <View style={[styles.card, muted ? styles.cardMuted : null]}>
      <View style={styles.cardHeader}>
        <Text style={styles.rank}>{candidate.rank}</Text>
        <Text style={styles.name} numberOfLines={2}>
          {tutor.displayName}
        </Text>
      </View>
      <View style={styles.labels}>
        {verified ? <VerificationLabel kind="verified" text={verified} /> : null}
        <VerificationLabel kind="self" text={`Kinh nghiệm ${tutor.experienceMonths} tháng (tự khai)`} />
      </View>
      <PriceBlock priceVndSession={candidate.priceVndSession} durationMinutes={durationMinutes} rateVnd60={tutor.rateVnd60} />
      <Text style={styles.body}>{commonScheduleText(candidate.commonSchedule)}</Text>
      <View style={styles.reasons}>
        {reasons.lines.map((line, index) => (
          <Text key={index} style={styles.caption}>{`• ${line}`}</Text>
        ))}
        {reasons.more ? <Text style={styles.caption}>{reasons.more}</Text> : null}
      </View>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`Xem chi tiết ${tutor.displayName}`}
        onPress={onOpen}
        style={styles.link}
      >
        <Text style={styles.linkText}>Xem chi tiết</Text>
      </Pressable>
    </View>
  );
}

/** True once the run's `expiresAt` has passed, and flips on time while the screen stays open. */
function useExpired(expiresAt: string | undefined, now: () => Date): boolean {
  const [, tick] = useState(0);
  const left = expiresAt ? new Date(expiresAt).getTime() - now().getTime() : null;
  useEffect(() => {
    if (left === null || left <= 0) return;
    // setTimeout holds at most about 24.8 days; a run lives far less.
    const timer = setTimeout(() => tick((n) => n + 1), Math.min(left, 2_000_000_000));
    return () => clearTimeout(timer);
  }, [left]);
  return left !== null && left <= 0;
}

const vnd = (amount: number | undefined) => (amount === undefined ? "" : formatVnd(amount));

const styles = StyleSheet.create({
  fill: { flex: 1 },
  content: { gap: spacing.lg, padding: spacing.md },
  loading: { alignItems: "center", gap: spacing.md, paddingVertical: spacing.xl },
  loadingText: { ...typography.body, color: colors.textMuted, textAlign: "center" },
  heading: { ...typography.heading, color: colors.text },
  body: { ...typography.body, color: colors.text },
  bodyMuted: { ...typography.body, color: colors.textMuted },
  caption: { ...typography.caption, color: colors.textMuted },
  card: {
    gap: spacing.sm,
    paddingTop: spacing.md,
    paddingHorizontal: spacing.md,
    paddingBottom: spacing.xs,
    borderWidth: 1,
    borderColor: colors.divider,
    borderRadius: radius.md,
    backgroundColor: colors.background,
  },
  cardMuted: { backgroundColor: colors.surface },
  cardHeader: { flexDirection: "row", alignItems: "flex-start", gap: spacing.sm },
  rank: { ...typography.label, color: colors.textMuted },
  name: { ...typography.bodyStrong, color: colors.text, flex: 1 },
  labels: { flexDirection: "row", flexWrap: "wrap", columnGap: spacing.sm + spacing.xs, rowGap: spacing.xs },
  reasons: { gap: spacing.xs },
  link: { minHeight: minTouchSize, justifyContent: "center", alignSelf: "flex-start" },
  linkText: { ...typography.label, color: colors.primary },
});
