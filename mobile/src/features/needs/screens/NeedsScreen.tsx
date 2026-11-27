// PH03 Danh sách và phiên bản nhu cầu (UC04, FR-04). Thiết kế: docs/design/ui/screens/PH03.md
import type { ReactNode } from "react";
import { ScrollView, StyleSheet, Text, View } from "react-native";
import { useStudents } from "@/features/students/api";
import { studentChoices, useSelectedStudent } from "@/features/students/selection";
import { Button } from "@/shared/ui/Button";
import { Notice } from "@/shared/ui/Notice";
import { QueryBody, QueryScreen } from "@/shared/ui/QueryScreen";
import { StickyActionBar } from "@/shared/ui/StickyActionBar";
import { StudentSwitcher } from "@/shared/ui/StudentSwitcher";
import { colors, spacing, typography } from "@/shared/ui/theme";
import { useFirstOffers, useNeeds, useTutorNames } from "../api";
import { NeedCardView, TextAction } from "../components/NeedCardView";
import { hasFreeTime } from "../freeTime";
import { useStudentCalendar } from "@/features/calendar/api";
import { hasOpenNeed, needCard, offerFactsByNeed, type Need, type NeedAction } from "../summary";

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
  // The child's free time is the child's calendar (#16 Q1): a draft card names it in "Cần thêm" once the
  // calendar is known to hold none. Loading or failing leaves it unsaid, as the counts below.
  const calendar = useStudentCalendar(student.id);
  const freeTime = calendar.data === undefined ? undefined : hasFreeTime(calendar.data?.data.windows ?? []);
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
                    <NeedCardView key={need.id} card={needCard(need, facts.get(need.id) ?? { pending: 0 }, freeTime)} onAction={onAction} />
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

const styles = StyleSheet.create({
  // Undoes QueryScreen's padding so the switcher bar and the sticky bar span the screen, as in Figma;
  // the rest keeps the padding.
  area: { flex: 1, marginHorizontal: -spacing.md, marginBottom: -spacing.md },
  scroll: { flex: 1 },
  content: { gap: spacing.md, paddingBottom: spacing.md },
  padded: { paddingHorizontal: spacing.md },
  list: { gap: spacing.md },
  lead: { ...typography.body, color: colors.textMuted },
});
