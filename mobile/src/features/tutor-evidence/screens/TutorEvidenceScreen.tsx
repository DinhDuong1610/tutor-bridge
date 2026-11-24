// GS03 Minh chứng và kết quả xác minh (UC03, FR-07). Thiết kế: docs/design/ui/screens/GS03.md
import { useState } from "react";
import { ScrollView, StyleSheet, Text, View } from "react-native";
import { Button } from "@/shared/ui/Button";
import { CommandStatus } from "@/shared/ui/CommandStatus";
import { ConfirmSheet } from "@/shared/ui/ConfirmSheet";
import { FileRow, type FileState } from "@/shared/ui/FileRow";
import { Icon } from "@/shared/ui/Icon";
import { Notice } from "@/shared/ui/Notice";
import { QueryScreen } from "@/shared/ui/QueryScreen";
import { StatusLabel } from "@/shared/ui/StatusLabel";
import { StickyActionBar } from "@/shared/ui/StickyActionBar";
import { colors, spacing, typography } from "@/shared/ui/theme";
import { useSaveTutorProfile, useTutorProfile, type ProfileRevision, type TutorProfileData } from "../../tutor-profile/api";
import { useSubmitTutorProfile } from "../api";
import { APPROVED, deriveEvidenceMode, describeFile, NEEDS_INFO, NOT_SENT, REJECTED, WAITING } from "../derive";
import { isAcceptable, MAX_FILES, REFUSED, uploadFailureMessage, useUploadEvidence, type PickedFile, type Sha256 } from "../upload";

type Props = {
  onBack: () => void;
  /** GS02, to write the profile first or change it after a refusal. */
  onEditProfile: () => void;
  /** Opens the system file picker (`pickEvidenceFile`, Q12 on #14); null when the tutor closed it. */
  pickFile: () => Promise<PickedFile | null>;
  /** Tests pass a SHA-256 that runs without the native module. */
  hash?: Sha256;
};

const TITLE = "Minh chứng và xác minh";
const RULES =
  "PDF, JPEG hoặc PNG; mỗi tệp tối đa 5 MB; tối đa 10 tệp mỗi lần gửi. Chỉ người xác minh được giao xem tệp gốc; phụ huynh không thấy.";
const STALE = "Dữ liệu đã thay đổi từ lúc bạn mở. Tải lại để xem bản mới; phần bạn đã nhập hợp lệ được giữ lại.";

type Row = { key: number; name: string; detail: string; state: FileState; id?: string; errorMessage?: string };

/**
 * Loading, lost access and the end of the session come from QueryScreen (INV-06). The files picked in
 * this visit live in the screen only: the contract has no way to list them again (Đính accepted this for Lát 1, Q13 (a) on #14).
 */
export function TutorEvidenceScreen({ onBack, ...props }: Props) {
  const query = useTutorProfile();
  return (
    <QueryScreen title={TITLE} onBack={onBack} query={query}>
      {(loaded) => <Evidence data={loaded} onReload={query.refetch} {...props} />}
    </QueryScreen>
  );
}

function Evidence({
  data,
  onReload,
  onEditProfile,
  pickFile,
  hash,
}: Omit<Props, "onBack"> & { data: TutorProfileData; onReload: () => Promise<{ isSuccess: boolean }> }) {
  const mode = deriveEvidenceMode(data);
  const [rows, setRows] = useState<Row[]>([]);
  const [nextKey, setNextKey] = useState(0);
  const [asking, setAsking] = useState(false);
  const upload = useUploadEvidence(hash);
  const save = useSaveTutorProfile();
  const submit = useSubmitTutorProfile();
  const stillPublic = (data.verification?.approvedVersion ?? null) !== null;

  // The ids of the last submission, to read the server's `evidenceUploadIds[i]` problems against.
  const [sentIds, setSentIds] = useState<string[]>([]);

  const update = (key: number, change: Partial<Row>) =>
    setRows((current) => current.map((row) => (row.key === key ? { ...row, ...change } : row)));

  // Evidence the server refused at submission shows the server's message on its row and is not sent again.
  // The last failure is read from `submit.error`; earlier ones are kept in `kept`, written just before
  // that error is cleared by the next submission or by "Tải lại".
  const [kept, setKept] = useState<ReadonlyMap<string, string>>(new Map());
  const refused = new Map(kept);
  if (submit.state === "failed" && submit.error) {
    for (const [path, message] of Object.entries(submit.error.fields)) {
      const index = /^evidenceUploadIds\[(\d+)]$/.exec(path)?.[1];
      const id = index === undefined ? undefined : sentIds[Number(index)];
      if (id !== undefined) refused.set(id, message);
    }
  }
  const keepRefused = () => setKept(refused);
  const shown: Row[] = rows.map((row) =>
    row.id !== undefined && refused.has(row.id) ? { ...row, state: "error", errorMessage: refused.get(row.id) } : row,
  );
  const ready = shown.filter((row) => row.state === "ready" && row.id !== undefined);
  const inProgress = shown.filter((row) => row.state === "uploading" || row.state === "checking");
  const counted = ready.length + inProgress.length;

  const addFile = async () => {
    const file = await pickFile();
    if (!file) return;
    const key = nextKey;
    setNextKey(key + 1);
    const detail = describeFile(file.name, file.mediaType, file.sizeBytes);
    if (!isAcceptable(file)) {
      setRows((current) => [...current, { key, name: file.name, detail, state: "error", errorMessage: REFUSED }]);
      return;
    }
    setRows((current) => [...current, { key, name: file.name, detail, state: "uploading" }]);
    try {
      const record = await upload.mutateAsync({ file, onChecking: () => update(key, { state: "checking" }) });
      update(key, record.state === "READY" ? { state: "ready", id: record.id } : record.state === "QUARANTINED" ? { state: "checking" } : { state: "error", errorMessage: REFUSED });
    } catch (error) {
      // The server expires an unfinished upload; the tutor picks the file again. The sentence says whether
      // the file or the connection failed (Q15 (b) on #14).
      update(key, { state: "error", errorMessage: uploadFailureMessage(error) });
    }
  };

  // The sheet stays open on a failure or an unknown outcome, so its "Thử lại" or message stays in view.
  const submitDraft = async (draft: { profile: { id: string }; etag: string }) => {
    const ids = ready.map((row) => row.id as string);
    keepRefused();
    setSentIds(ids);
    if (await submit.submit({ profileVersionId: draft.profile.id, etag: draft.etag, evidenceUploadIds: ids })) setAsking(false);
  };
  const afterSave = (saved: ProfileRevision | undefined) => (saved ? submitDraft({ profile: saved.data, etag: saved.etag }) : undefined);

  const send = async () => {
    if (mode.kind !== "collect") return;
    if (mode.draft !== null) return submitDraft(mode.draft);
    if (mode.needsInfo === null) return;
    // OP-24: only a DRAFT can be sent, so a version that needs information is first saved as the next draft.
    const { profile, etag } = mode.needsInfo;
    await afterSave(
      await save.submit({
        etag,
        values: { description: profile.description, experienceMonths: profile.experienceMonths, rateVnd60: profile.rateVnd60 },
      }),
    );
  };
  // "Thử lại" after an unknown outcome goes on the same way as the first attempt.
  const saveCommand = { ...save, retry: async () => afterSave(await save.retry()) };
  const submitCommand = {
    ...submit,
    retry: async () => {
      if (await submit.retry()) setAsking(false);
    },
  };

  // INV-23: after a stale ETag, "Tải lại" reads the new revision, then clears the stale answers so the
  // next submission starts as a new command against it. The files picked in this visit stay (Q13).
  const reload = async () => {
    const result = await onReload();
    if (!result.isSuccess) return;
    keepRefused();
    save.reset();
    submit.reset();
  };

  const sent = submit.state === "confirmed";
  const stale = (submit.state === "failed" && submit.error?.kind === "stale") || (save.state === "failed" && save.error?.kind === "stale");
  const fileList = shown.length > 0 ? shown.map(({ key, ...row }) => <FileRow key={key} {...row} />) : null;

  if (mode.kind === "noProfile") {
    // No frame for opening GS03 before GS02 is saved: copy chosen by Đính, Q14 (1) on #14.
    return (
      <View style={styles.body}>
        <Notice tone="info" message="Lưu hồ sơ chuyên môn trước, rồi tải minh chứng." />
        <Button variant="secondary" title="Mở hồ sơ chuyên môn" onPress={onEditProfile} />
      </View>
    );
  }

  if (mode.kind === "waiting") {
    return (
      <ScrollView contentContainerStyle={styles.body}>
        {sent ? <Notice tone="success" message="Đã gửi hồ sơ đi xác minh." /> : null}
        <StatusRow {...WAITING} />
        <Text style={styles.text}>{`Người xác minh sẽ kiểm tra phạm vi ${mode.scope}. Kết quả hiện ở đây và trong Thông báo.`}</Text>
        {fileList ? (
          <>
            <EvidenceHeading />
            {fileList}
          </>
        ) : null}
      </ScrollView>
    );
  }

  if (mode.kind === "verified") {
    return (
      <ScrollView contentContainerStyle={styles.body}>
        <StatusRow {...APPROVED} />
        {mode.scope ? (
          <View style={styles.verified}>
            <Icon name="verified_fill" color={colors.success} size={16} />
            <Text style={styles.verifiedText}>{`Đã xác minh: ${mode.scope}`}</Text>
          </View>
        ) : null}
        <Text style={styles.text}>
          Phụ huynh tìm gia sư sẽ thấy nhãn này cùng phạm vi đã kiểm. Nhãn không bảo đảm chất lượng giảng dạy, và minh chứng gốc không công khai.
        </Text>
        <Text style={styles.muted}>Kinh nghiệm và học phí vẫn hiện là thông tin tự khai.</Text>
      </ScrollView>
    );
  }

  if (mode.kind === "rejected") {
    return (
      <ScrollView contentContainerStyle={styles.body}>
        <StatusRow {...REJECTED} />
        <Text style={styles.text}>
          {stillPublic
            ? // OP-24 and GS01 Q1: parents keep seeing the approved version.
              "Phụ huynh vẫn thấy bản hồ sơ đã duyệt trước của bạn. Bản sửa: chưa được duyệt."
            : "Hồ sơ chưa được xác minh nên chưa xuất hiện khi phụ huynh tìm gia sư."}
        </Text>
        {mode.reason ? <Text style={styles.text}>{`Lý do: ${mode.reason}`}</Text> : null}
        <Button variant="secondary" title="Sửa hồ sơ và gửi lại" onPress={onEditProfile} />
      </ScrollView>
    );
  }

  const submitTitle = mode.needsInfo ? "Gửi lại để xác minh" : "Gửi duyệt";
  const busy = save.state === "sending" || submit.state === "sending";
  const blocked = save.state === "unknown" || submit.state === "unknown" || stale;
  const full = counted >= MAX_FILES;

  return (
    <View style={styles.fill}>
      <ScrollView contentContainerStyle={styles.form}>
        <StatusRow {...(mode.needsInfo ? NEEDS_INFO : NOT_SENT)} />
        {mode.needsInfo ? (
          <>
            <Text style={styles.text}>Người xác minh cần thêm thông tin trước khi kết luận.</Text>
            <Text style={styles.text}>{`Lý do: ${mode.needsInfo.reason}`}</Text>
          </>
        ) : null}
        <EvidenceHeading />
        {fileList ?? <Notice tone="info" message="Chưa có tệp nào. Tải ít nhất 1 tệp để gửi hồ sơ đi xác minh." />}
        <Button
          variant="secondary"
          title={rows.length === 0 ? "Tải minh chứng" : "Tải thêm minh chứng"}
          disabled={full || busy}
          onPress={() => void addFile()}
        />
        {full ? (
          <Text style={styles.caption}>Đã đủ 10 tệp, không thể tải thêm.</Text>
        ) : ready.length === 0 ? (
          <Text style={styles.caption}>Gửi duyệt cần ít nhất 1 tệp ở trạng thái Sẵn sàng.</Text>
        ) : inProgress.length > 0 ? (
          <Text style={styles.caption}>
            Tệp đang tải hoặc đang kiểm tra sẽ không được gửi kèm. Bạn có thể đợi hoặc gửi với các tệp đã Sẵn sàng.
          </Text>
        ) : null}
      </ScrollView>
      <StickyActionBar>
        {stale ? (
          <Notice tone="warning" message={STALE} action={<Button variant="secondary" title="Tải lại" onPress={() => void reload()} />} />
        ) : null}
        <Button title={submitTitle} disabled={ready.length === 0 || blocked} busy={busy && !asking} onPress={() => setAsking(true)} />
      </StickyActionBar>
      <ConfirmSheet
        visible={asking}
        title="Gửi hồ sơ để xác minh?"
        message={`Bản hồ sơ hiện tại và ${ready.length} tệp minh chứng sẽ được giữ nguyên để người xác minh xem. ${
          stillPublic
            ? "Phụ huynh vẫn thấy bản hồ sơ đã duyệt trước của bạn cho tới khi bản này được xác minh."
            : "Phụ huynh chưa thấy hồ sơ cho tới khi được xác minh."
        }`}
        confirmTitle={submitTitle}
        busy={busy}
        confirmDisabled={save.state === "unknown" || submit.state === "unknown"}
        onConfirm={() => void send()}
        onCancel={() => setAsking(false)}
      >
        {stale ? null : save.state === "unknown" || save.state === "failed" ? (
          <CommandStatus command={saveCommand} confirmedMessage="" />
        ) : (
          <CommandStatus command={submitCommand} confirmedMessage="" />
        )}
      </ConfirmSheet>
    </View>
  );
}

function EvidenceHeading() {
  return (
    <>
      <Text accessibilityRole="header" style={styles.heading}>
        Minh chứng chuyên môn
      </Text>
      <Text style={styles.muted}>{RULES}</Text>
    </>
  );
}

function StatusRow({ label, tone, icon }: typeof NOT_SENT) {
  return (
    <View style={styles.statusRow}>
      <Text style={styles.muted}>Trạng thái</Text>
      <StatusLabel label={label} tone={tone} icon={icon} />
    </View>
  );
}

const styles = StyleSheet.create({
  // Lets the list and the action bar reach the screen edges inside QueryScreen's padding.
  fill: { flex: 1, marginHorizontal: -spacing.md, marginBottom: -spacing.md },
  form: { gap: spacing.md, paddingHorizontal: spacing.md, paddingBottom: spacing.md },
  body: { gap: spacing.md, paddingBottom: spacing.md },
  statusRow: { flexDirection: "row", flexWrap: "wrap", alignItems: "center", gap: spacing.sm },
  verified: { flexDirection: "row", alignItems: "center", gap: spacing.xs },
  verifiedText: { ...typography.label, color: colors.success, flexShrink: 1 },
  heading: { ...typography.heading, color: colors.text },
  text: { ...typography.body, color: colors.text },
  muted: { ...typography.body, color: colors.textMuted },
  caption: { ...typography.caption, color: colors.textMuted },
});
