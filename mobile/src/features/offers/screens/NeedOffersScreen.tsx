// PH09 Danh sách đề nghị của một nhu cầu (UC06; FR-17). Thiết kế: docs/design/ui/screens/PH09.md
import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { ChildOfNeed } from "@/features/matching/components/ChildOfNeed";
import { useNeed, type NeedRevision } from "@/features/needs/api";
import { useStudents } from "@/features/students/api";
import { Button } from "@/shared/ui/Button";
import { Notice } from "@/shared/ui/Notice";
import { QueryScreen, type QueryState } from "@/shared/ui/QueryScreen";
import { StatusLabel } from "@/shared/ui/StatusLabel";
import { StickyActionBar } from "@/shared/ui/StickyActionBar";
import { colors, minTouchSize, radius, spacing, typography } from "@/shared/ui/theme";
import { useNeedOffers, useTutorNamesOf, type Offer } from "../api";
import { offerLabel, offerLine, sortOffers, termsLine } from "../status";

type Child = { id: string; displayName: string; grade: number };
type Props = {
  needId: string;
  /** Set by the detail screen after a cancel: the list says which offer was cancelled. */
  cancelledOfferId?: string;
  onBack: () => void;
  onOpenOffer: (offerId: string) => void;
  onOpenClass: (classId: string) => void;
  /** "Xem gia sư phù hợp": PH06 of this need. */
  onFindTutors: () => void;
  onOtherChild: () => void;
  onManageStudents: () => void;
  now?: () => Date;
};

/** Every offer the parent sent for one need, as the server holds them; waiting ones first. */
export function NeedOffersScreen({
  needId,
  cancelledOfferId,
  onBack,
  onOpenOffer,
  onOpenClass,
  onFindTutors,
  onOtherChild,
  onManageStudents,
  now = () => new Date(),
}: Props) {
  const need = useNeed(needId);
  const students = useStudents();
  const offers = useNeedOffers(needId);
  const names = useTutorNamesOf(offers.offers?.map((offer) => offer.tutorId) ?? []);
  const errors = [need.error, students.error, offers.error].filter((error) => error !== null);
  const query: QueryState<{ need: NeedRevision; students: Child[]; offers: Offer[] }> = {
    isPending: errors.length === 0 && (need.isPending || students.isPending || offers.offers === undefined),
    isError: errors.length > 0,
    error: errors[0] ?? null,
    data:
      need.data && students.data && offers.offers
        ? { need: need.data, students: students.data.pages.flatMap((page) => page.items), offers: offers.offers }
        : undefined,
    refetch: () => Promise.all([need.refetch(), students.refetch(), offers.refetch()]),
  };
  return (
    <QueryScreen title="Đề nghị đã gửi" onBack={onBack} query={query}>
      {(loaded) => {
        const cancelled = loaded.offers.find((offer) => offer.id === cancelledOfferId && offer.state === "CANCELLED");
        const empty = loaded.offers.length === 0;
        return (
          <View style={styles.fill}>
            <ChildOfNeed
              students={loaded.students}
              childId={loaded.need.data.studentId}
              onOtherChild={onOtherChild}
              onManageStudents={onManageStudents}
            />
            <ScrollView contentContainerStyle={styles.content}>
              {empty ? (
                <Notice tone="info" message="Bạn chưa gửi đề nghị nào cho nhu cầu này. Chọn gia sư trong danh sách gợi ý để gửi." />
              ) : (
                <>
                  {cancelled ? (
                    <Notice tone="success" message={`Đã hủy đề nghị gửi ${nameOf(names, cancelled.tutorId)}.`} />
                  ) : null}
                  <Text style={styles.heading}>{`Nhu cầu: ${loaded.need.data.draft.goal ?? ""}`}</Text>
                  <Text style={styles.caption}>
                    Bạn có thể gửi đề nghị cho nhiều gia sư. Khi một gia sư chấp nhận, lớp được chốt và các đề nghị còn lại tự kết thúc.
                  </Text>
                  {sortOffers(loaded.offers).map((offer) => (
                    <OfferCard
                      key={offer.id}
                      offer={offer}
                      name={nameOf(names, offer.tutorId)}
                      now={now()}
                      onOpen={() => onOpenOffer(offer.id)}
                      onOpenClass={onOpenClass}
                    />
                  ))}
                </>
              )}
            </ScrollView>
            {empty ? (
              <StickyActionBar>
                <Button title="Xem gia sư phù hợp" onPress={onFindTutors} />
              </StickyActionBar>
            ) : null}
          </View>
        );
      }}
    </QueryScreen>
  );
}

/** A tutor whose public profile is hidden now has no name to show: "Gia sư" stands in. */
export function nameOf(names: Map<string, string>, tutorId: string): string {
  return names.get(tutorId) ?? "Gia sư";
}

function OfferCard({
  offer,
  name,
  now,
  onOpen,
  onOpenClass,
}: {
  offer: Offer;
  name: string;
  now: Date;
  onOpen: () => void;
  onOpenClass: (classId: string) => void;
}) {
  const label = offerLabel(offer);
  const line = offerLine(offer, now);
  // Only the open offer and the class stay in front; the others are kept for reference, muted.
  const open = offer.state === "PENDING" || offer.state === "ACCEPTED";
  return (
    <View style={[styles.card, !open && styles.cardMuted]}>
      <StatusLabel label={label.label} tone={label.tone} icon={label.icon} />
      <Text style={[styles.name, !open && styles.muted]} numberOfLines={2}>
        {name}
      </Text>
      <Text style={styles.bodyMuted}>{termsLine(offer)}</Text>
      {line ? <Text style={[styles.body, !open && styles.muted]}>{line}</Text> : null}
      {offer.state === "PENDING" ? <Link title="Xem chi tiết" label={`Xem chi tiết đề nghị gửi ${name}`} onPress={onOpen} /> : null}
      {offer.state === "ACCEPTED" && offer.classId ? (
        <Link title="Xem lớp" label={`Xem lớp với ${name}`} onPress={() => onOpenClass(offer.classId as string)} />
      ) : null}
    </View>
  );
}

function Link({ title, label, onPress }: { title: string; label: string; onPress: () => void }) {
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={label} onPress={onPress} style={styles.link}>
      <Text style={styles.linkText}>{title}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  content: { gap: spacing.lg, padding: spacing.md },
  heading: { ...typography.heading, color: colors.text },
  caption: { ...typography.caption, color: colors.textMuted },
  card: {
    gap: spacing.xs,
    paddingTop: spacing.md,
    paddingHorizontal: spacing.md,
    paddingBottom: spacing.md,
    borderWidth: 1,
    borderColor: colors.divider,
    borderRadius: radius.md,
    backgroundColor: colors.background,
  },
  cardMuted: { backgroundColor: colors.surface },
  name: { ...typography.bodyStrong, color: colors.text },
  muted: { color: colors.textMuted },
  body: { ...typography.body, color: colors.text },
  bodyMuted: { ...typography.body, color: colors.textMuted },
  link: { minHeight: minTouchSize, justifyContent: "center", alignSelf: "flex-start" },
  linkText: { ...typography.label, color: colors.primary },
});
