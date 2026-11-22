import { useMutation } from "@tanstack/react-query";
import type { components } from "@tutorbridge/api-client";
import { CryptoDigestAlgorithm, digest } from "expo-crypto";
import { api, API_BASE_URL } from "@/shared/api/client";
import { newCommandKey } from "@/shared/api/command";
import { call } from "@/shared/api/errors";

export type MediaType = components["schemas"]["EvidenceUploadInput"]["mediaType"];
export type EvidenceRecord = components["schemas"]["TutorEvidenceRecord"];

/** G3-D07-04: PDF, JPEG or PNG, at most 5 MiB a file, at most 10 files a submission. */
export const MEDIA_TYPES: readonly MediaType[] = ["application/pdf", "image/jpeg", "image/png"];
export const MAX_BYTES = 5 * 1024 * 1024;
export const MAX_FILES = 10;

/**
 * A file the tutor picked. The picker itself waits for Đính (Q12 on #14); the screen takes a function
 * that returns one of these, so the rest of GS03 does not depend on which library reads the file.
 */
export type PickedFile = { name: string; mediaType: string; sizeBytes: number; read: () => Promise<Uint8Array<ArrayBuffer>> };

export function isAcceptable(file: PickedFile): file is PickedFile & { mediaType: MediaType } {
  return (MEDIA_TYPES as readonly string[]).includes(file.mediaType) && file.sizeBytes >= 1 && file.sizeBytes <= MAX_BYTES;
}

export type Sha256 = (bytes: Uint8Array<ArrayBuffer>) => Promise<string>;

/** SHA-256 in lowercase hex, as `EvidenceUploadInput.sha256` asks. */
export const sha256: Sha256 = async (bytes) => {
  const hash = new Uint8Array(await digest(CryptoDigestAlgorithm.SHA256, bytes));
  return Array.from(hash, (byte) => byte.toString(16).padStart(2, "0")).join("");
};

const contentPath = "/me/tutor-profile/evidence-uploads/{uploadId}/content";

/**
 * R0 points `uploadUrl` at uploadEvidenceContent on this API (contract). The session goes only to that
 * URL; any other answer stops the upload instead of sending the session to another origin.
 */
export function isOwnContentUrl(uploadUrl: string, uploadId: string): boolean {
  const expected = `${API_BASE_URL.replace(/\/$/, "")}/me/tutor-profile/evidence-uploads/${encodeURIComponent(uploadId)}/content`;
  try {
    return new URL(uploadUrl).href === new URL(expected).href;
  } catch {
    return false;
  }
}

/** The file never reached a usable state; the row shows the approved "Lỗi" text. */
export class UploadRefused extends Error {}

/**
 * createEvidenceUpload, uploadEvidenceContent, completeEvidenceUpload for one file. Each step is its own
 * command with its own Idempotency-Key (INV-20). The size and hash are declared from the bytes read
 * once, so all three steps describe the same bytes. A failed step leaves an unfinished upload that the
 * server expires; the tutor picks the file again.
 */
export async function uploadEvidence(
  file: PickedFile & { mediaType: MediaType },
  hash: Sha256 = sha256,
  /** Called before the last step: the server now checks the bytes ("Đang kiểm tra"). */
  onChecking: () => void = () => undefined,
): Promise<EvidenceRecord> {
  const bytes = await file.read();
  const declared = { sizeBytes: bytes.byteLength, sha256: await hash(bytes) };
  if (declared.sizeBytes < 1 || declared.sizeBytes > MAX_BYTES) throw new UploadRefused();

  const ticket = await call(
    api.POST("/me/tutor-profile/evidence-uploads", {
      params: { header: { "Idempotency-Key": newCommandKey() } },
      body: { mediaType: file.mediaType, ...declared },
    }),
  );
  if (!isOwnContentUrl(ticket.uploadUrl, ticket.uploadId)) throw new UploadRefused();

  await call(
    api.PUT(contentPath, {
      params: { path: { uploadId: ticket.uploadId }, header: { "Idempotency-Key": newCommandKey() } },
      // The raw bytes, not JSON. The contract types the binary body as a string.
      body: bytes as unknown as string,
      bodySerializer: (body: unknown) => body as BodyInit,
      headers: { "Content-Type": file.mediaType },
    }),
  );

  onChecking();
  return call(
    api.POST("/me/tutor-profile/evidence-uploads/{uploadId}/complete", {
      params: { path: { uploadId: ticket.uploadId }, header: { "Idempotency-Key": newCommandKey() } },
      body: declared,
    }),
  );
}

/** One mutation per file, so a 401 on any step ends the session like every other command. */
export function useUploadEvidence(hash?: Sha256) {
  return useMutation({
    mutationFn: ({ file, onChecking }: { file: PickedFile & { mediaType: MediaType }; onChecking: () => void }) =>
      uploadEvidence(file, hash, onChecking),
    retry: false,
  });
}
