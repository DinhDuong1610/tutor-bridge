import { getDocumentAsync } from "expo-document-picker";
import { pickEvidenceFile } from "./pickFile";

jest.mock("expo-document-picker", () => ({ getDocumentAsync: jest.fn() }));

const mockBytes = new TextEncoder().encode("%PDF-1.7");
jest.mock("expo-file-system", () => ({
  File: jest.fn().mockImplementation((uri: string) => ({
    uri,
    size: 8,
    arrayBuffer: () => Promise.resolve(mockBytes.buffer.slice(0)),
  })),
}));

const picker = jest.mocked(getDocumentAsync);

describe("pickEvidenceFile (Q12)", () => {
  it("asks the system picker for one PDF, JPEG or PNG copied to the cache", async () => {
    picker.mockResolvedValueOnce({ canceled: true, assets: null });
    await pickEvidenceFile();
    expect(picker).toHaveBeenCalledWith({
      type: ["application/pdf", "image/jpeg", "image/png"],
      copyToCacheDirectory: true,
      multiple: false,
    });
  });

  it("returns null when the tutor closes the picker", async () => {
    picker.mockResolvedValueOnce({ canceled: true, assets: null });
    await expect(pickEvidenceFile()).resolves.toBeNull();
  });

  it("returns the name, type and size of the asset, and reads its bytes from the cached copy", async () => {
    picker.mockResolvedValueOnce({
      canceled: false,
      assets: [{ uri: "file:///cache/bang-tot-nghiep.pdf", name: "bang-tot-nghiep.pdf", mimeType: "application/pdf", size: 8, lastModified: 0 }],
    });
    const file = await pickEvidenceFile();
    expect(file).toMatchObject({ name: "bang-tot-nghiep.pdf", mediaType: "application/pdf", sizeBytes: 8 });
    expect(Array.from(await file!.read())).toEqual(Array.from(mockBytes));
  });

  it("falls back to the size of the file when the picker does not give one", async () => {
    picker.mockResolvedValueOnce({
      canceled: false,
      assets: [{ uri: "file:///cache/anh.png", name: "anh.png", lastModified: 0 }],
    });
    await expect(pickEvidenceFile()).resolves.toMatchObject({ mediaType: "", sizeBytes: 8 });
  });
});
