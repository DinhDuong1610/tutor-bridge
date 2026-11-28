// PH09 Chi tiết và hủy đề nghị (UC06; FR-17; TX06). Thiết kế: docs/design/ui/screens/PH09.md
import { formatDateTime } from "@tutorbridge/shared";
import { useEffect, useRef, useState } from "react";
import { ScrollView, StyleSheet, Text, View } from "react-native";
import { ChildOfNeed } from "@/features/matching/components/ChildOfNeed";
import { cycleScheduleText } from "@/features/matching/text";
import { useNeed } from "@/features/needs/api";
import { useStudents } from "@/features/students/api";
import { Button } from "@/shared/ui/Button";
import { CommandStatus } from "@/shared/ui/CommandStatus";
import { ConfirmSheet } from "@/shared/ui/ConfirmSheet";
import { CycleStrip } from "@/shared/ui/CycleStrip";
import { Notice } from "@/shared/ui/Notice";
import { PriceBlock } from "@/shared/ui/PriceBlock";
import { QueryBody, QueryScreen, type QueryState } from "@/shared/ui/QueryScreen";
import { StatusLabel } from "@/shared/ui/StatusLabel";
import { StickyActionBar } from "@/shared/ui/StickyActionBar";
import { colors, spacing, typography } from "@/shared/ui/theme";
import { useCancelOffer, useOffer, useTutorNamesOf, type Offer } from "../api";
import { offerLabel, offerLine } from "../status";
import { nameOf } from "./NeedOffersScreen";

type Child = { id: string; displayName: string; grade: number };
type Props = {
  offerId: string;
  onBack: () => void;
  /** "Gửi cho gia sư khác": PH06 of the offer's need. */
  onFindTutors: (needId: string) => void;
  onOpenClass: (classId: string) => void;
  /** After a confirmed cancel: back to the need's list, which says which offer was cancelled. */
  onCancelled: (needId: string, offerId: string) => void;
  onOtherChild: () => void;
  onManageStudents: () => void;
};

/**
 * One offer as the server holds it; the terms sent cannot change. A waiting offer can be cancelled against
 * the ETag on screen (INV-23); one the tutor answered meanwhile is not, and the screen shows it as it is now.
 */
export function OfferDetailScreen({ offerId, onBack, onFindTutors, onOpenClass, onCancelled, onOtherChild, onManageStudents }: Props) {
  const offer = useOffer(offerId);
  const students = useStudents();
  const names = useTutorNamesOf(offer.data ? [offer.data.data.tutorId] : []);
  const errors = [offer.error, students.error].filter((error) => error !== null);
  const query: QueryState<{ offer: { data: Offer; etag: string }; students: Child[] }> = {
    isPending: errors.length === 0 && (offer.isPending || students.isPending),
    isError: errors.length > 0,
    error: errors[0] ?? null,
    data: offer.data && students.data ? { offer: offer.data, students: students.data.pages.flatMap((page) => page.items) } : undefined,
    refetch: () => Promise.all([offer.refetch(), students.refetch()]),
  };
  return (
    <QueryScreen title="Chi tiết đề nghị" onBack={onBack} query={query}>
      {(loaded) => (
        <Detail
          revision={loaded.offer}
          students={loaded.students}
          name={nameOf(names, loaded.offer.data.tutorId)}
          onReload={async () => {
            await offer.refetch();
          }}
          onFindTutors={onFindTutors}
          onOpenClass={onOpenClass}
          onCancelled={onCancelled}
          onOtherChild={onOtherChild}
          onManageStudents={onManageStudents}
        />
      )}
    </QueryScreen>
  );
}

function Detail({
  revision,
  students,
  name,
  onReload,
  onFindTutors,
  onOpenClass,
  onCancelled,
  onOtherChild,
  onManageStudents,
}: {
  revision: { data: Offer; etag: string };
  students: Child[];
  name: string;
  onReload: () => Promise<void>;
} & Omit<Props, "offerId" | "onBack">) {
  const offer = revision.data;
  // The child of the offer's need, for the switcher: the need is read once the offer is known.
  const need = useNeed(offer.needId);
  const cancel = useCancelOffer();
  const [asking, setAsking] = useState(false);
  const stale = cancel.state === "failed" && cancel.error?.kind === "stale";
  // The tutor answered or the deadline passed while the parent looked: read the offer again (no silent undo).
  const ended = cancel.state === "failed" && (cancel.error?.code === "OFFER_NOT_PENDING" || cancel.error?.code === "OFFER_EXPIRED");
  // Each runs once when its state is reached: the callbacks are new on every render of the route.
  const latest = useRef({ onReload, onCancelled });
  useEffect(() => {
    latest.current = { onReload, onCancelled };
  });
  useEffect(() => {
    if (ended) void latest.current.onReload();
  }, [ended]);
  const cancelled = cancel.state === "confirmed";
  useEffect(() => {
    if (cancelled) latest.current.onCancelled(offer.needId, offer.id);
  }, [cancelled, offer.needId, offer.id]);

  const label = offerLabel(offer);
  const pending = offer.state === "PENDING";
  const reason = pending || offer.state === "ACCEPTED" ? null : offerLine(offer, new Date());
  const reload = async () => {
    await onReload();
    cancel.reset();
  };

  let bar;
  if (offer.state === "ACCEPTED") {
    bar = offer.classId ? <Button title="Xem lớp" onPress={() => onOpenClass(offer.classId as string)} /> : null;
  } else if (pending) {
    bar = (
      <>
        {stale ? (
          <Notice
            tone="warning"
            message="Dữ liệu đã thay đổi từ lúc bạn mở. Tải lại để xem bản mới; phần bạn đã nhập hợp lệ được giữ lại."
            action={<Button variant="secondary" title="Tải lại" onPress={() => void reload()} />}
          />
        ) : (
          <CommandStatus command={cancel} confirmedMessage="" />
        )}
        <Button title="Gửi cho gia sư khác" disabled={cancel.state === "sending"} onPress={() => onFindTutors(offer.needId)} />
        <Button
          variant="secondary"
          title="Hủy đề nghị"
          busy={cancel.state === "sending"}
          disabled={stale || cancel.state === "unknown"}
          onPress={() => setAsking(true)}
        />
      </>
    );
  } else {
    bar = <Button title="Gửi cho gia sư khác" onPress={() => onFindTutors(offer.needId)} />;
  }

  return (
    <View style={styles.fill}>
      <QueryBody query={need}>
        {(loadedNeed) => (
          <ChildOfNeed
            students={students}
            childId={loadedNeed.data.studentId}
            onOtherChild={onOtherChild}
            onManageStudents={onManageStudents}
          />
        )}
      </QueryBody>
      <ScrollView contentContainerStyle={styles.content}>
        {offer.state === "ACCEPTED" ? (
          <Notice tone="success" message={`${name} đã chấp nhận. Lớp và đủ ${offer.schedule.length} buổi đã được đặt.`} />
        ) : null}
        {ended && cancel.error ? <Notice tone="danger" message={cancel.error.problem.title} /> : null}
        <StatusLabel label={label.label} tone={label.tone} icon={label.icon} />
        <Text accessibilityRole="header" style={styles.name}>
          {name}
        </Text>
        {pending ? (
          <Text style={styles.strong}>{`Hết hạn lúc ${formatDateTime(offer.expiresAt)} nếu gia sư chưa trả lời. Đề nghị chưa giữ chỗ.`}</Text>
        ) : null}
        {reason ? <Text style={styles.body}>{reason}</Text> : null}
        <View style={styles.section}>
          <Text style={styles.heading}>Điều kiện đã gửi (không đổi được)</Text>
          <Text style={styles.body}>{`Mục tiêu: ${offer.goalSnapshot}`}</Text>
          <PriceBlock priceVndSession={offer.priceVndSession} durationMinutes={offer.durationMinutes} rateVnd60={offer.rateVnd60} />
        </View>
        <CycleStrip
          heading={cycleScheduleText(offer.schedule)}
          perWeek={offer.frequencyPer7Days}
          booked={offer.state === "ACCEPTED"}
          caption={offer.state === "ACCEPTED" ? "Chấm đặc: buổi đã chốt." : "Vòng nét đứt: buổi trong đề nghị, chưa chốt, chưa giữ chỗ."}
        />
        <Text style={styles.caption}>{`Gửi lúc ${formatDateTime(offer.sentAt)}`}</Text>
      </ScrollView>
      {bar ? <StickyActionBar>{bar}</StickyActionBar> : null}
      <ConfirmSheet
        visible={asking}
        title={`Hủy đề nghị gửi ${name}?`}
        message="Gia sư sẽ không chấp nhận được đề nghị này nữa. Các đề nghị khác của nhu cầu này không bị ảnh hưởng."
        confirmTitle="Hủy đề nghị"
        onConfirm={() => {
          setAsking(false);
          void cancel.submit({ offerId: offer.id, etag: revision.etag });
        }}
        onCancel={() => setAsking(false)}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  content: { gap: spacing.lg, padding: spacing.md },
  name: { ...typography.title, color: colors.text },
  strong: { ...typography.bodyStrong, color: colors.text },
  section: { gap: spacing.xs },
  heading: { ...typography.heading, color: colors.text },
  body: { ...typography.body, color: colors.text },
  caption: { ...typography.caption, color: colors.textMuted },
});
