// PH08 Xem lại và gửi đề nghị (UC06; FR-15, FR-17; BR02, BR04, BR05). Thiết kế: docs/design/ui/screens/PH08.md
import { formatDateTime } from "@tutorbridge/shared";
import { useState } from "react";
import { ScrollView, StyleSheet, Text, View } from "react-native";
import { useMatchingRun, type Candidate, type MatchingRun } from "@/features/matching/api";
import { ChildOfNeed } from "@/features/matching/components/ChildOfNeed";
import { sessionDate, sessionTime } from "@/features/matching/text";
import { useNeed, type NeedRevision } from "@/features/needs/api";
import { cycleText } from "@/features/needs/review";
import { useStudents } from "@/features/students/api";
import { Button } from "@/shared/ui/Button";
import { CommandStatus } from "@/shared/ui/CommandStatus";
import { ConfirmSheet } from "@/shared/ui/ConfirmSheet";
import { CycleStrip } from "@/shared/ui/CycleStrip";
import { Notice } from "@/shared/ui/Notice";
import { PriceBlock } from "@/shared/ui/PriceBlock";
import { QueryScreen, type QueryState } from "@/shared/ui/QueryScreen";
import { ProposedSessionCard } from "@/shared/ui/SessionCard";
import { StickyActionBar } from "@/shared/ui/StickyActionBar";
import { colors, spacing, typography } from "@/shared/ui/theme";
import { TERMS_CHANGED, useSendOffer, type Offer } from "../api";

type Child = { id: string; displayName: string; grade: number };
type Props = {
  needId: string;
  runId: string;
  tutorId: string;
  onBack: () => void;
  /** "Tìm lại gia sư": PH06 of this need, for a search with current prices and calendars. */
  onFindAgain: () => void;
  /** "Sửa nhu cầu": PH04 of this need. */
  onEdit: () => void;
  /** "Xem đề nghị": the need's offers (PH09). */
  onViewOffers: () => void;
  onOtherChild: () => void;
  onManageStudents: () => void;
};

/**
 * The terms of one candidate, sent as they are (UC06). Nothing is held before the tutor accepts (BR05): the
 * server checks the need version, the run, the tutor's price and calendar and every session again, and the
 * screen says "Đã gửi" only after its 201.
 */
export function SendOfferScreen({ needId, runId, tutorId, onBack, onFindAgain, onEdit, onViewOffers, onOtherChild, onManageStudents }: Props) {
  const run = useMatchingRun(runId);
  const need = useNeed(needId);
  const students = useStudents();
  const send = useSendOffer();
  const errors = [run.error, need.error, students.error].filter((error) => error !== null);
  const query: QueryState<{ run: MatchingRun; need: NeedRevision; students: Child[] }> = {
    isPending: errors.length === 0 && (run.isPending || need.isPending || students.isPending),
    isError: errors.length > 0,
    error: errors[0] ?? null,
    data:
      run.data && need.data && students.data
        ? { run: run.data, need: need.data, students: students.data.pages.flatMap((page) => page.items) }
        : undefined,
    refetch: () => Promise.all([run.refetch(), need.refetch(), students.refetch()]),
  };
  const sent = send.state === "confirmed" ? send.result : undefined;
  return (
    <QueryScreen title={sent ? "Đề nghị đã gửi" : "Xem lại đề nghị"} onBack={onBack} query={query}>
      {(loaded) => {
        const header = (
          <ChildOfNeed
            students={loaded.students}
            childId={loaded.need.data.studentId}
            onOtherChild={onOtherChild}
            onManageStudents={onManageStudents}
          />
        );
        const candidate = loaded.run.candidates.find((c) => c.tutor.tutorId === tutorId);
        if (sent) {
          return (
            <View style={styles.fill}>
              {header}
              <Sent offer={sent} tutorName={candidate?.tutor.displayName ?? ""} onViewOffers={onViewOffers} onFindAgain={onFindAgain} />
            </View>
          );
        }
        return (
          <View style={styles.fill}>
            {header}
            <Review
              revision={loaded.need}
              run={loaded.run}
              candidate={candidate}
              send={send}
              onFindAgain={onFindAgain}
              onEdit={onEdit}
            />
          </View>
        );
      }}
    </QueryScreen>
  );
}

function Review({
  revision,
  run,
  candidate,
  send,
  onFindAgain,
  onEdit,
}: {
  revision: NeedRevision;
  run: MatchingRun;
  candidate: Candidate | undefined;
  send: ReturnType<typeof useSendOffer>;
  onFindAgain: () => void;
  onEdit: () => void;
}) {
  const [asking, setAsking] = useState(false);
  // The need version a send was made from, kept with it: while its outcome is in doubt, the screen shows
  // those terms and keeps "Thử lại", whatever a reread of the need says since (Codex, #276).
  const [sentFrom, setSentFrom] = useState<NeedRevision["data"] | null>(null);
  const inDoubt = (send.state === "sending" || send.state === "unknown") && sentFrom !== null;
  const need = inDoubt ? sentFrom : revision.data;
  const draft = need.draft;
  const code = send.state === "failed" ? send.error?.code : undefined;
  // The run is of another version than the need on screen: its price and sessions are not of this version.
  const otherVersion = run.needVersionId !== need.versionId;
  // ...or the server says the need moved on (412, 409). A send in doubt is never blocked: only "Thử lại"
  // with the same key and body tells whether the first send made an offer (the server replays it first).
  const oldVersion =
    !inDoubt && (otherVersion || (send.state === "failed" && (send.error?.kind === "stale" || code === "NEED_VERSION_CHANGED")));
  const termsChanged = code !== undefined && TERMS_CHANGED.has(code);
  // A candidate the run does not have cannot be offered: the same way out as an old version.
  const blocked = oldVersion || candidate === undefined;
  // Never the terms of one version beside the price and sessions of another (Codex, #276), as on PH07.
  const shown = otherVersion ? undefined : candidate;

  const submit = () => {
    setAsking(false);
    if (!candidate) return;
    setSentFrom(revision.data);
    void send.submit({
      needId: need.id,
      etag: revision.etag,
      needVersionId: need.versionId,
      runId: run.id,
      studentCalendarVersionId: run.studentCalendarVersionId,
      candidate,
    });
  };

  let bar;
  if (blocked) {
    bar = <Button title="Tìm lại gia sư" onPress={onFindAgain} />;
  } else if (termsChanged) {
    bar = (
      <>
        <Button title="Tìm lại gia sư" onPress={onFindAgain} />
        <Button variant="secondary" title="Sửa nhu cầu" onPress={onEdit} />
      </>
    );
  } else {
    bar = (
      <>
        <CommandStatus command={send} confirmedMessage="" />
        <Button
          title="Gửi đề nghị"
          busy={send.state === "sending"}
          // An unknown outcome is settled by "Thử lại" with the same key, never by a second send (PH08.md).
          disabled={send.state === "unknown"}
          onPress={() => setAsking(true)}
        />
      </>
    );
  }

  return (
    <View style={styles.fill}>
      <ScrollView contentContainerStyle={styles.content}>
        {blocked ? (
          <Notice
            tone="warning"
            message="Nhu cầu đã có bản mới từ lúc bạn tìm. Đề nghị này dựa trên bản cũ nên không gửi được. Tìm lại từ bản đang hiệu lực."
          />
        ) : termsChanged ? (
          <Notice tone="warning" message="Giá hoặc lịch của gia sư đã thay đổi. Hãy xem lại trước khi gửi." />
        ) : null}
        {shown ? (
          <>
            <View style={styles.terms}>
              <Text style={styles.label}>Gia sư</Text>
              <Text style={styles.body}>{shown.tutor.displayName}</Text>
              <Text style={styles.label}>Mục tiêu</Text>
              <Text style={styles.body}>{draft.goal ?? ""}</Text>
              <Text style={styles.label}>Học phí</Text>
              <PriceBlock
                priceVndSession={shown.priceVndSession}
                durationMinutes={draft.durationMinutes ?? 0}
                rateVnd60={shown.tutor.rateVnd60}
              />
            </View>
            {draft.frequencyPer7Days && draft.cycleStartDate ? (
              <CycleStrip
                heading={cycleText({ frequencyPer7Days: draft.frequencyPer7Days, cycleStartDate: draft.cycleStartDate })}
                perWeek={draft.frequencyPer7Days}
                caption="Vòng nét đứt: buổi trong đề nghị, chưa chốt, chưa giữ chỗ."
              />
            ) : null}
            <View style={styles.sessions}>
              <Text style={styles.heading}>{`Đủ ${shown.commonSchedule.length} buổi trong đề nghị`}</Text>
              {shown.commonSchedule.map((window) => (
                <ProposedSessionCard key={window.start} date={sessionDate(window.start)} time={sessionTime(window)} />
              ))}
            </View>
            <Text style={styles.bodyMuted}>
              Gia sư có 48 giờ để trả lời, hoặc ít hơn nếu buổi đầu bắt đầu sớm hơn. Đề nghị chưa giữ chỗ cho tới khi gia sư chấp nhận.
            </Text>
          </>
        ) : null}
      </ScrollView>
      <StickyActionBar>{bar}</StickyActionBar>
      <ConfirmSheet
        visible={asking}
        title={`Gửi đề nghị cho ${candidate?.tutor.displayName ?? ""}?`}
        message="Gửi đề nghị là bạn đồng ý các điều kiện trên. Gia sư chỉ có thể chấp nhận nguyên bản hoặc từ chối."
        confirmTitle="Gửi đề nghị"
        onConfirm={submit}
        onCancel={() => setAsking(false)}
      />
    </View>
  );
}

function Sent({
  offer,
  tutorName,
  onViewOffers,
  onFindAgain,
}: {
  offer: Offer;
  tutorName: string;
  onViewOffers: () => void;
  onFindAgain: () => void;
}) {
  return (
    <View style={styles.fill}>
      <ScrollView contentContainerStyle={styles.content}>
        <Notice tone="success" message={`Đã gửi đề nghị cho ${tutorName}.`} />
        <Text style={styles.strong}>{`Hết hạn lúc ${formatDateTime(offer.expiresAt)} nếu gia sư chưa trả lời.`}</Text>
        <Text style={styles.bodyMuted}>
          Bạn sẽ nhận thông báo khi gia sư trả lời. Trong lúc chờ, bạn vẫn có thể gửi đề nghị cho gia sư khác từ cùng nhu cầu; lớp chỉ được chốt với một gia sư.
        </Text>
      </ScrollView>
      <StickyActionBar>
        <Button title="Xem đề nghị" onPress={onViewOffers} />
        <Button variant="secondary" title="Về danh sách gia sư" onPress={onFindAgain} />
      </StickyActionBar>
    </View>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  content: { gap: spacing.lg, padding: spacing.md },
  terms: { gap: spacing.xs },
  sessions: { gap: spacing.sm },
  label: { ...typography.caption, color: colors.textMuted },
  heading: { ...typography.heading, color: colors.text },
  body: { ...typography.body, color: colors.text },
  bodyMuted: { ...typography.body, color: colors.textMuted },
  strong: { ...typography.bodyStrong, color: colors.text },
});
