// CL01 Chi tiết lớp, chỉ xem (UC04, UC07; FR-17–19). Thiết kế: docs/design/ui/screens/CL01.md
import type { components } from "@tutorbridge/api-client";
import { formatVnd } from "@tutorbridge/shared";
import type { ReactNode } from "react";
import { ScrollView, StyleSheet, Text, View } from "react-native";
import { useStudents } from "@/features/students/api";
import { Button } from "@/shared/ui/Button";
import { CycleStrip } from "@/shared/ui/CycleStrip";
import type { IconName } from "@/shared/ui/Icon";
import { QueryScreen } from "@/shared/ui/QueryScreen";
import { StatusLabel, type StatusTone } from "@/shared/ui/StatusLabel";
import { StickyActionBar } from "@/shared/ui/StickyActionBar";
import { colors, spacing, typography } from "@/shared/ui/theme";
import { combine, useClass, useSessions, useTutorName } from "../api";
import { ClassChild } from "../components/ClassChild";
import { cycleHeading, cycleMarks, nextSession, sessionWhen } from "../cycle";
import type { Session } from "../weeks";

type Class = components["schemas"]["Class"];
type Student = { id: string; displayName: string; grade: number };

type Props = {
  classId: string;
  role: "PARENT" | "TUTOR";
  onBack: () => void;
  /** "Xem lịch lớp": CL02 of this class. */
  onSessions: (classId: string) => void;
  /** The parent chose another child: the choice is kept and the screen leaves, as on CL02. */
  onOtherChild?: () => void;
  /** "Quản lý hồ sơ các con" in the switcher: PH02. */
  onManageStudents?: () => void;
  /** For tests; the clock decides the next session and which sessions are over. */
  now?: Date;
};

// `03` "Nhãn trạng thái theo hợp đồng". ENDED does not happen in Lát 1 (no UC08), so it has no label.
const LABELS: Partial<Record<Class["state"], { label: string; tone: StatusTone; icon: IconName }>> = {
  WAITING: { label: "Chưa bắt đầu", tone: "info", icon: "schedule" },
  ACTIVE: { label: "Đang học", tone: "success", icon: "check_circle" },
};

export function ClassDetailScreen(props: Props) {
  // Two components so a tutor never reads listStudents nor getTutorPublic, which are the parent's.
  return props.role === "PARENT" ? <ParentDetail {...props} /> : <TutorDetail {...props} />;
}

function ParentDetail({ classId, onBack, onSessions, onOtherChild, onManageStudents, now = new Date() }: Props) {
  const cls = useClass(classId);
  const sessions = useSessions(classId);
  const students = useStudents();
  // A failed read of the tutor's name leaves the name out; the class still shows (as CL02).
  const tutorName = useTutorName(cls.data?.tutorId).data;
  const all = students.data?.pages.flatMap((page) => page.items);
  const data = cls.data && sessions.data && all ? { cls: cls.data, sessions: sessions.data, students: all } : undefined;
  return (
    <QueryScreen title="Chi tiết lớp" onBack={onBack} query={combine([cls, sessions, students], data)} placesStaleNotice>
      {(loaded, staleNotice) => {
        const child = loaded.students.find((student) => student.id === loaded.cls.studentId);
        return (
          <Detail
            cls={loaded.cls}
            sessions={loaded.sessions}
            now={now}
            staleNotice={staleNotice}
            onSessions={onSessions}
            switcher={
              child ? (
                <ClassChild
                  students={loaded.students}
                  childId={child.id}
                  onOtherChild={onOtherChild}
                  onManageStudents={onManageStudents}
                />
              ) : null
            }
          >
            {tutorName ? <Field label="Gia sư" value={tutorName} /> : null}
            <Field label="Cho con" value={child ? childLabel(child) : loaded.cls.studentDisplayName} />
          </Detail>
        );
      }}
    </QueryScreen>
  );
}

function TutorDetail({ classId, onBack, onSessions, now = new Date() }: Props) {
  const cls = useClass(classId);
  const sessions = useSessions(classId);
  const data = cls.data && sessions.data ? { cls: cls.data, sessions: sessions.data } : undefined;
  return (
    <QueryScreen title="Chi tiết lớp" onBack={onBack} query={combine([cls, sessions], data)} placesStaleNotice>
      {(loaded, staleNotice) => (
        <Detail cls={loaded.cls} sessions={loaded.sessions} now={now} staleNotice={staleNotice} onSessions={onSessions}>
          {/* OP-25 (#97): the tutor of the class sees the student's display name. */}
          <Field label="Học sinh" value={loaded.cls.studentDisplayName} />
        </Detail>
      )}
    </QueryScreen>
  );
}

const childLabel = (child: Student) => `${child.displayName}, lớp ${child.grade}`;

/** The parts both sides share; `children` are the people of the class, as each side names them. */
function Detail({
  cls,
  sessions,
  now,
  staleNotice,
  switcher,
  onSessions,
  children,
}: {
  cls: Class;
  sessions: Session[];
  now: Date;
  staleNotice: ReactNode;
  switcher?: ReactNode;
  onSessions: (classId: string) => void;
  children: ReactNode;
}) {
  const label = LABELS[cls.state];
  const heading = cycleHeading(sessions);
  const next = nextSession(sessions, now);
  return (
    <View style={styles.edgeToEdge}>
      <ScrollView contentContainerStyle={styles.content}>
        {switcher}
        <View style={styles.padded}>
          {staleNotice}
          {label ? <StatusLabel {...label} /> : null}
          <View style={styles.people}>{children}</View>
          <Section title="Mục tiêu đã chốt">
            <Text style={styles.value}>{cls.goal}</Text>
          </Section>
          {/* TBD (#73, Đính): `PriceBlock` of `03` wants the price per 60 minutes, which Class does not carry; this
              is the CL01 frame, the price and length fixed at TX05 (BR02: never worked out on the device). */}
          <Section title="Học phí đã chốt">
            <Text style={styles.price}>{`${formatVnd(cls.priceVndSession)} / buổi ${cls.durationMinutes} phút`}</Text>
            <Text style={styles.caption}>Giữ nguyên theo thỏa thuận khi chốt, kể cả khi gia sư đổi học phí sau này.</Text>
          </Section>
          {heading ? <CycleStrip heading={heading} marks={cycleMarks(sessions, now)} caption="Chấm đặc: buổi đã chốt." /> : null}
          {next ? <Text style={styles.next}>{`Buổi tới: ${sessionWhen(next)}`}</Text> : null}
        </View>
      </ScrollView>
      <StickyActionBar>
        <Button title="Xem lịch lớp" onPress={() => onSessions(cls.id)} />
      </StickyActionBar>
    </View>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <View style={styles.section}>
      <Text accessibilityRole="header" style={styles.sectionTitle}>
        {title}
      </Text>
      {children}
    </View>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <View>
      <Text style={styles.label}>{label}</Text>
      <Text style={styles.value}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  // Undoes QueryScreen's padding so the switcher bar and the action bar span the screen, as on CL02.
  edgeToEdge: { flex: 1, marginHorizontal: -spacing.md },
  content: { gap: spacing.md, paddingBottom: spacing.lg },
  padded: { paddingHorizontal: spacing.md, gap: spacing.lg },
  people: { gap: spacing.sm },
  section: { gap: spacing.xs },
  sectionTitle: { ...typography.heading, color: colors.text },
  label: { ...typography.caption, color: colors.textMuted },
  value: { ...typography.body, color: colors.text },
  price: { ...typography.bodyStrong, color: colors.text },
  caption: { ...typography.caption, color: colors.textMuted },
  next: { ...typography.bodyStrong, color: colors.text },
});
