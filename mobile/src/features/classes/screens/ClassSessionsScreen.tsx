// CL02 Lịch lớp và chi tiết buổi, chỉ xem (UC07; FR-19, FR-20). Thiết kế: docs/design/ui/screens/CL02.md
import { formatTime } from "@tutorbridge/shared";
import { useState, type ReactNode } from "react";
import { Modal, Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { useStudents } from "@/features/students/api";
import { useSelectedStudent } from "@/features/students/selection";
import { Button } from "@/shared/ui/Button";
import { QueryScreen, type QueryState } from "@/shared/ui/QueryScreen";
import { SessionCard } from "@/shared/ui/SessionCard";
import { StatusLabel } from "@/shared/ui/StatusLabel";
import { StudentSwitcher, type StudentChoice } from "@/shared/ui/StudentSwitcher";
import { colors, minTouchSize, radius, spacing, typography } from "@/shared/ui/theme";
import { useClass, useSessions, useTutorName } from "../api";
import {
  classWeeks,
  dateBlock,
  dayMonth,
  fullDate,
  lastWeek,
  openingWeek,
  sessionLook,
  sessionsInWeek,
  vietnamDay,
  weekdayName,
  weekLabel,
  type ClassWeeks,
  type Session,
  type SessionLook,
} from "../weeks";

type Props = {
  classId: string;
  role: "PARENT" | "TUTOR";
  onBack: () => void;
  /**
   * The parent chose another child in the switcher. The class belongs to one child, so the screen
   * leaves (Huy, #73 Q3, 27/11/2026). TBD: go to that child's class list once listClasses exists (#97).
   */
  onOtherChild?: () => void;
  /** For tests; the clock decides the opening week and which sessions are past. */
  now?: Date;
};

type Student = { id: string; displayName: string; grade: number };
type Loaded = { cls: { studentId: string; tutorId: string; durationMinutes: number }; sessions: Session[]; students: Student[] };

/** One frame state for the reads this screen needs, so a failure of any shows the load error. */
function combine(parts: { isPending: boolean; isError: boolean; error: Error | null; refetch: () => unknown }[], data: Loaded | undefined): QueryState<Loaded> {
  return {
    isPending: parts.some((part) => part.isPending),
    isError: parts.some((part) => part.isError),
    error: parts.find((part) => part.error)?.error ?? null,
    data,
    refetch: () => parts.forEach((part) => void part.refetch()),
  };
}

export function ClassSessionsScreen(props: Props) {
  // Two components so a tutor never reads listStudents, which is the parent's own list.
  return props.role === "PARENT" ? <ParentSessions {...props} /> : <TutorSessions {...props} />;
}

function ParentSessions({ classId, onBack, onOtherChild, now = new Date() }: Props) {
  const cls = useClass(classId);
  const sessions = useSessions(classId);
  const students = useStudents();
  const tutorName = useTutorName(cls.data?.tutorId).data;
  const data =
    cls.data && sessions.data && students.data
      ? { cls: cls.data, sessions: sessions.data, students: students.data.pages.flatMap((page) => page.items) }
      : undefined;
  return (
    <QueryScreen title="Lịch lớp" onBack={onBack} query={combine([cls, sessions, students], data)} placesStaleNotice>
      {(loaded, staleNotice) => (
        <Week loaded={loaded} parent tutorName={tutorName} now={now} staleNotice={staleNotice} onOtherChild={onOtherChild} />
      )}
    </QueryScreen>
  );
}

function TutorSessions({ classId, onBack, now = new Date() }: Props) {
  const cls = useClass(classId);
  const sessions = useSessions(classId);
  const data = cls.data && sessions.data ? { cls: cls.data, sessions: sessions.data, students: [] } : undefined;
  return (
    <QueryScreen title="Lịch lớp" onBack={onBack} query={combine([cls, sessions], data)} placesStaleNotice>
      {(loaded, staleNotice) => <Week loaded={loaded} parent={false} tutorName={undefined} now={now} staleNotice={staleNotice} />}
    </QueryScreen>
  );
}

function Week({
  loaded,
  parent,
  tutorName,
  now,
  staleNotice,
  onOtherChild,
}: {
  loaded: Loaded;
  parent: boolean;
  tutorName: string | undefined;
  now: Date;
  staleNotice: ReactNode;
  onOtherChild?: () => void;
}) {
  const weeks = classWeeks(loaded.sessions);
  const [week, setWeek] = useState(() => (weeks ? openingWeek(weeks, now) : 0));
  const [open, setOpen] = useState<Session | null>(null);
  const child = loaded.students.find((student) => student.id === loaded.cls.studentId);

  // Parent: "Toán 8 với Nguyễn Thị Lan". Tutor: the fixed scope, as in the Figma "Gia sư" frame; the
  // student's name comes with studentDisplayName (#97, OP-25).
  const classTitle = parent ? (tutorName ? `Toán 8 với ${tutorName}` : "Toán 8") : "Lớp Toán 8 đại số";
  const inCard = parent ? classTitle : "lớp Toán 8 đại số";
  const total = loaded.sessions.length;

  return (
    <ScrollView style={styles.edgeToEdge} contentContainerStyle={styles.content}>
      {parent && child ? (
        <ClassChild students={loaded.students} childId={child.id} onOtherChild={onOtherChild} />
      ) : null}
      <View style={styles.padded}>
        {staleNotice}
        {weeks ? (
          <>
            <Text style={styles.lead}>
              {`${classTitle}, ${total} buổi từ ${dayMonth(weeks.firstDay)} đến ${fullDate(weeks.lastDay)}`}
            </Text>
            <WeekBar weeks={weeks} week={week} last={lastWeek(weeks, now)} onChange={setWeek} />
            <SessionList
              sessions={sessionsInWeek(weeks, loaded.sessions, week)}
              total={total}
              durationMinutes={loaded.cls.durationMinutes}
              inCard={inCard}
              now={now}
              onOpen={setOpen}
            />
          </>
        ) : (
          <Text style={styles.lead}>Không có buổi nào trong tuần này.</Text>
        )}
      </View>
      {open ? (
        <SessionSheet
          session={open}
          total={total}
          durationMinutes={loaded.cls.durationMinutes}
          classTitle={parent ? classTitle : "Toán 8 đại số"}
          childLabel={parent && child ? `${child.displayName}, lớp ${child.grade}` : undefined}
          look={sessionLook(open, now)}
          onClose={() => setOpen(null)}
        />
      ) : null}
    </ScrollView>
  );
}

// As PH01 builds them (TB-14a). #259 moves this into features/students/selection.tsx as studentChoices;
// use it here once both are merged.
const choiceOf = (student: Student): StudentChoice => ({
  id: student.id,
  name: student.displayName,
  label: `${student.displayName}, lớp ${student.grade}`,
  detail: `Lớp ${student.grade}.`,
});

/** The class's child in the switcher. Choosing another child leaves the class (#73 Q3). */
function ClassChild({ students, childId, onOtherChild }: { students: Student[]; childId: string; onOtherChild?: () => void }) {
  const { select } = useSelectedStudent(students);
  return (
    <StudentSwitcher
      choices={students.map(choiceOf)}
      selectedId={childId}
      onSelect={(studentId) => {
        if (studentId === childId) return;
        select(studentId);
        onOtherChild?.();
      }}
      onManage={() => onOtherChild?.()}
    />
  );
}

function WeekBar({ weeks, week, last, onChange }: { weeks: ClassWeeks; week: number; last: number; onChange: (week: number) => void }) {
  return (
    // CL02 "Hành vi": the label on top, the two buttons on the row below.
    <View style={styles.weekBar}>
      <Text accessibilityRole="header" style={styles.weekLabel}>
        {weekLabel(weeks, week)}
      </Text>
      <View style={styles.weekButtons}>
        <TextAction title="Tuần trước" disabled={week <= 0} onPress={() => onChange(week - 1)} />
        <TextAction title="Tuần sau" disabled={week >= last} onPress={() => onChange(week + 1)} />
      </View>
    </View>
  );
}

function SessionList({
  sessions,
  total,
  durationMinutes,
  inCard,
  now,
  onOpen,
}: {
  sessions: Session[];
  total: number;
  durationMinutes: number;
  inCard: string;
  now: Date;
  onOpen: (session: Session) => void;
}) {
  if (sessions.length === 0) {
    return <Text style={styles.lead}>Không có buổi nào trong tuần này.</Text>;
  }
  return (
    <View style={styles.list}>
      {sessions.map((session) => (
        <SessionCard
          key={session.id}
          variant={sessionLook(session, now)}
          date={dateBlock(vietnamDay(session.during.start))}
          time={timeRange(session)}
          detail={`Buổi ${session.ordinal} trên ${total}. ${durationMinutes} phút, ${inCard}`}
          onPress={() => onOpen(session)}
        />
      ))}
    </View>
  );
}

const timeRange = (session: Session) => `${formatTime(session.during.start)}–${formatTime(session.during.end)}`;

// Session labels of `03`: SCHEDULED "Đã chốt" (success), CANCELLED "Đã hủy" (neutral); past is a look.
const SHEET_LABELS: Record<SessionLook, { label: string; tone: "success" | "neutral"; icon: "check_circle" | "event" }> = {
  scheduled: { label: "Đã chốt", tone: "success", icon: "check_circle" },
  past: { label: "Đã qua", tone: "neutral", icon: "event" },
  cancelled: { label: "Đã hủy", tone: "neutral", icon: "event" },
};

/** CL02 "Chi tiết buổi": a bottom sheet, read only, with "Đóng". */
function SessionSheet({
  session,
  total,
  durationMinutes,
  classTitle,
  childLabel,
  look,
  onClose,
}: {
  session: Session;
  total: number;
  durationMinutes: number;
  classTitle: string;
  childLabel?: string;
  look: SessionLook;
  onClose: () => void;
}) {
  const insets = useSafeAreaInsets();
  const day = vietnamDay(session.during.start);
  return (
    <Modal visible transparent animationType="slide" onRequestClose={onClose}>
      <View style={styles.backdrop}>
        <Pressable style={styles.fill} accessibilityElementsHidden importantForAccessibility="no-hide-descendants" onPress={onClose} />
        <View accessibilityViewIsModal style={[styles.sheet, { paddingBottom: Math.max(insets.bottom, spacing.md) }]}>
          <View style={styles.handle} />
          <Text accessibilityRole="header" style={styles.sheetTitle}>{`Buổi ${session.ordinal} trên ${total}`}</Text>
          <StatusLabel {...SHEET_LABELS[look]} />
          <Field label="Thời gian" value={`${weekdayName(day)}, ${fullDate(day)}, ${timeRange(session)} (giờ Việt Nam)`} />
          <Field label="Thời lượng" value={`${durationMinutes} phút`} />
          <Field label="Lớp" value={classTitle} />
          {childLabel ? <Field label="Cho con" value={childLabel} /> : null}
          <Button variant="secondary" title="Đóng" onPress={onClose} />
        </View>
      </View>
    </Modal>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <View>
      <Text style={styles.fieldLabel}>{label}</Text>
      <Text style={styles.fieldValue}>{value}</Text>
    </View>
  );
}

function TextAction({ title, onPress, disabled }: { title: string; onPress: () => void; disabled: boolean }) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [styles.action, pressed && styles.actionPressed]}
    >
      <Text style={[styles.actionText, disabled && styles.actionDisabled]}>{title}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  // Undoes QueryScreen's padding so the switcher bar spans the screen, as on PH01.
  edgeToEdge: { marginHorizontal: -spacing.md },
  content: { gap: spacing.md, paddingBottom: spacing.md },
  padded: { paddingHorizontal: spacing.md, gap: spacing.md },
  lead: { ...typography.body, color: colors.textMuted },
  list: { gap: spacing.sm },
  weekBar: { gap: spacing.xs, padding: spacing.sm, borderRadius: radius.md, backgroundColor: colors.surface },
  weekLabel: { ...typography.bodyStrong, color: colors.text, textAlign: "center" },
  weekButtons: { flexDirection: "row", justifyContent: "space-between" },
  action: { minHeight: minTouchSize, justifyContent: "center", paddingHorizontal: spacing.sm, borderRadius: radius.sm },
  actionPressed: { backgroundColor: colors.infoSurface },
  actionText: { ...typography.label, color: colors.primary },
  actionDisabled: { color: colors.textMuted },
  backdrop: { flex: 1, justifyContent: "flex-end", backgroundColor: `${colors.text}66` }, // as ConfirmSheet
  fill: { flex: 1 },
  sheet: { gap: spacing.md, padding: spacing.md, borderTopLeftRadius: radius.md, borderTopRightRadius: radius.md, backgroundColor: colors.background },
  handle: { alignSelf: "center", width: 32, height: 4, borderRadius: radius.full, backgroundColor: colors.border },
  sheetTitle: { ...typography.heading, color: colors.text },
  fieldLabel: { ...typography.caption, color: colors.textMuted },
  fieldValue: { ...typography.body, color: colors.text },
});
