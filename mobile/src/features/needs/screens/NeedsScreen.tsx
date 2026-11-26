// PH03 Danh sách và phiên bản nhu cầu (UC04, FR-04). Thiết kế: docs/design/ui/screens/PH03.md
import type { ReactNode } from "react";
import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { useStudents } from "@/features/students/api";
import { studentChoices, useSelectedStudent } from "@/features/students/selection";
import { Button } from "@/shared/ui/Button";
import type { IconName } from "@/shared/ui/Icon";
import { Notice } from "@/shared/ui/Notice";
import { QueryBody, QueryScreen } from "@/shared/ui/QueryScreen";
import { StatusLabel, type StatusTone } from "@/shared/ui/StatusLabel";
import { StickyActionBar } from "@/shared/ui/StickyActionBar";
import { StudentSwitcher } from "@/shared/ui/StudentSwitcher";
import { colors, minTouchSize, radius, spacing, typography } from "@/shared/ui/theme";
import { useFirstOffers, useNeeds, useTutorNames } from "../api";
import { hasOpenNeed, needCard, offerFactsByNeed, type Need, type NeedAction, type NeedCard } from "../summary";

type Props = {
  onAddStudent: () => void;
  onManageStudents: () => void;
  /** PH04 for a new need. PH04 takes the child being viewed (06: not changeable in the form). */
  onCreate: () => void;
  onAction: (action: NeedAction) => void;
};

type StudentItem = { id: string; displayName: string; grade: number };

export function NeedsScreen(props: Props) {
  const students = useStudents();
  return (
    // Figma names this screen after its tab, "Tìm gia sư".
    <QueryScreen title="Tìm gia sư" query={students} placesStaleNotice>
      {(data, staleNotice) => <Needs {...props} items={data.pages.flatMap((page) => page.items)} staleNotice={staleNotice} />}
    </QueryScreen>
  );
}

function Needs({ items, staleNotice, onAddStudent, onManageStudents, onCreate, onAction }: Props & { items: StudentItem[]; staleNotice: ReactNode }) {
  const { selected, select } = useSelectedStudent(items);

  if (!selected) {
    // Without a child every parent tab leads to PH02 with this reminder (05-dieu-huong-luong.md); PH01's words.
    return (
      <ScrollView contentContainerStyle={styles.content}>
        {staleNotice}
        <Notice tone="info" message="Tạo hồ sơ cho con trước. Mọi việc tìm gia sư, lịch và lớp đều gắn với một con." />
        <Button title="Thêm hồ sơ con" onPress={onAddStudent} />
      </ScrollView>
    );
  }

  const switcher = (
    <StudentSwitcher choices={studentChoices(items)} selectedId={selected.id} onSelect={select} onManage={onManageStudents} />
  );
  return (
    // Keyed by the child: changing child starts a fresh list, never the previous child's (AC-UC02-01).
    <ChildNeeds
      key={selected.id}
      student={selected}
      header={
        <>
          {switcher}
          {staleNotice ? <View style={styles.padded}>{staleNotice}</View> : null}
        </>
      }
      onCreate={onCreate}
      onAction={onAction}
    />
  );
}

function ChildNeeds({
  student,
  header,
  onCreate,
  onAction,
}: {
  student: StudentItem;
  header: ReactNode;
  onCreate: () => void;
  onAction: (action: NeedAction) => void;
}) {
  const needs = useNeeds(student.id);
  // Counts and names on the cards. Loading them is not needed to read the list, so a failure only
  // leaves those words out; the cards and their actions stay.
  const offers = useFirstOffers().data?.items ?? [];
  const loaded: Need[] = needs.data?.pages.flatMap((page) => page.items) ?? [];
  // Names only for the closed needs on screen: the first page of offers spans every child and class.
  const closedIds = new Set(loaded.filter((need) => need.status === "CLOSED").map((need) => need.id));
  const tutorNames = useTutorNames(offers.filter((offer) => closedIds.has(offer.needId)));
  const isEmpty = needs.data !== undefined && loaded.length === 0;
  // The open need is the newest one: a child gets a new need only after the last one closed (#16 Q4).
  const canCreate = needs.data !== undefined && !hasOpenNeed(loaded);

  return (
    <View style={styles.area}>
      <ScrollView style={styles.scroll} contentContainerStyle={styles.content}>
        {header}
        <View style={styles.padded}>
          <QueryBody
            query={needs}
            empty={{
              when: () => isEmpty,
              message: `${student.displayName} chưa có nhu cầu học kèm. Tạo nhu cầu để bắt đầu tìm gia sư.`,
            }}
          >
            {() => {
              const facts = offerFactsByNeed(offers, tutorNames);
              return (
                <View style={styles.list}>
                  <Text style={styles.lead}>
                    Mỗi nhu cầu là một lần tìm gia sư. Sửa nhu cầu đã xác nhận sẽ tạo bản nháp mới; lớp đã chốt không đổi.
                  </Text>
                  {loaded.map((need) => (
                    <Card key={need.id} card={needCard(need, facts.get(need.id) ?? { pending: 0 })} onAction={onAction} />
                  ))}
                  {needs.hasNextPage ? (
                    <TextAction
                      title="Xem thêm"
                      disabled={needs.isFetchingNextPage}
                      onPress={() => void needs.fetchNextPage()}
                    />
                  ) : null}
                </View>
              );
            }}
          </QueryBody>
        </View>
      </ScrollView>
      {canCreate ? (
        <StickyActionBar>
          {/* Figma: "Tạo nhu cầu học kèm" on the empty frame, "Tạo nhu cầu mới" below a list. */}
          <Button title={isEmpty ? "Tạo nhu cầu học kèm" : "Tạo nhu cầu mới"} onPress={onCreate} />
        </StickyActionBar>
      ) : null}
    </View>
  );
}

// `03` "Nhãn trạng thái theo hợp đồng": Nháp and Đã đóng neutral, Đã xác nhận info. Icons as in PH03 Figma.
const LABELS: Record<Need["status"], { label: string; tone: StatusTone; icon: IconName }> = {
  DRAFT: { label: "Nháp", tone: "neutral", icon: "edit" },
  CONFIRMED: { label: "Đã xác nhận", tone: "info", icon: "schedule" },
  CLOSED: { label: "Đã đóng", tone: "neutral", icon: "edit" },
};

function Card({ card, onAction }: { card: NeedCard; onAction: (action: NeedAction) => void }) {
  return (
    <View style={[styles.card, card.status === "CLOSED" && styles.closedCard]}>
      <StatusLabel {...LABELS[card.status]} />
      <Text style={styles.title}>{card.title}</Text>
      <Text style={styles.body}>{card.body}</Text>
      {card.actions.map((action) => (
        <TextAction key={action.kind} title={action.title} onPress={() => onAction(action)} />
      ))}
    </View>
  );
}

function TextAction({ title, onPress, disabled = false }: { title: string; onPress: () => void; disabled?: boolean }) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [styles.action, pressed && styles.actionPressed]}
    >
      <Text style={styles.actionText}>{title}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  // Undoes QueryScreen's padding so the switcher bar and the sticky bar span the screen, as in Figma;
  // the rest keeps the padding.
  area: { flex: 1, marginHorizontal: -spacing.md, marginBottom: -spacing.md },
  scroll: { flex: 1 },
  content: { gap: spacing.md, paddingBottom: spacing.md },
  padded: { paddingHorizontal: spacing.md },
  list: { gap: spacing.md },
  lead: { ...typography.body, color: colors.textMuted },
  card: { gap: spacing.xs, padding: spacing.md, borderRadius: radius.md, borderWidth: 1, borderColor: colors.divider, backgroundColor: colors.background },
  // A closed need is for reference only; the frame sets it on the surface colour.
  closedCard: { backgroundColor: colors.surface },
  title: { ...typography.bodyStrong, color: colors.text },
  body: { ...typography.body, color: colors.textMuted },
  // A text button as in the frames; the pressable area stays at least 48 tall (minTouchSize).
  action: { alignSelf: "flex-start", minHeight: minTouchSize, justifyContent: "center", borderRadius: radius.sm, marginLeft: -spacing.xs, paddingHorizontal: spacing.xs },
  actionPressed: { backgroundColor: colors.infoSurface },
  actionText: { ...typography.label, color: colors.primary },
});
