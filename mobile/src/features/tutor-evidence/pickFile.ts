import { getDocumentAsync } from "expo-document-picker";
import { File } from "expo-file-system";
import { MEDIA_TYPES, type PickedFile } from "./upload";

/**
 * Opens the system picker for one PDF, JPEG or PNG (Đính approved the two libraries, Q12 on #14,
 * 23/11/2026). The picker copies the file to the app cache, so it can be read again on "Thử lại".
 * The type filter is only a hint to the system UI: `isAcceptable` still checks the type and size,
 * and the upload declares the size of the bytes it actually read.
 */
export async function pickEvidenceFile(): Promise<PickedFile | null> {
  const result = await getDocumentAsync({ type: [...MEDIA_TYPES], copyToCacheDirectory: true, multiple: false });
  const asset = result.canceled ? undefined : result.assets[0];
  if (!asset) return null;
  const file = new File(asset.uri);
  return {
    name: asset.name,
    mediaType: asset.mimeType ?? "",
    sizeBytes: asset.size ?? file.size,
    read: async () => new Uint8Array(await file.arrayBuffer()),
  };
}
