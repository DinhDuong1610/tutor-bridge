import { tutorProfileSchema } from "./schemas";

const DESCRIPTION = "Tôi dạy Toán THCS 2 năm, tập trung vào đại số lớp 8, chữa lỗi ngay trong buổi học.";
const parse = (experienceMonths: string, rateVnd60: string) =>
  tutorProfileSchema.safeParse({ description: DESCRIPTION, experienceMonths, rateVnd60 });

describe("GS02 numbers are read as typed, never rewritten into another number (Codex review of #209)", () => {
  it.each([
    ["24", "250.000", 24, 250000],
    ["0", "250000", 0, 250000],
    ["600", "250 000", 600, 250000],
    [" 12 ", "1.500.000", 12, 1500000],
    ["6", "1 500 000", 6, 1500000],
  ])("accepts months %p and price %p", (months, price, expectedMonths, expectedPrice) => {
    const result = parse(months, price);
    expect(result.success).toBe(true);
    expect(result.data).toMatchObject({ experienceMonths: expectedMonths, rateVnd60: expectedPrice });
  });

  it.each(["0.5", "1.5", "1,5", "2 4", "-1", "601", ""])("refuses months %p instead of reading another number", (months) => {
    const result = parse(months, "250.000");
    expect(result.success).toBe(false);
    expect(result.error?.issues.map((issue) => issue.path[0])).toEqual(["experienceMonths"]);
  });

  it.each(["1.5", "25.00", "2.50.000", "250.00", "250,000", "250. 000", "1.500 000", "0", ""])(
    "refuses price %p instead of reading another number",
    (price) => {
      const result = parse("24", price);
      expect(result.success).toBe(false);
      expect(result.error?.issues.map((issue) => issue.path[0])).toEqual(["rateVnd60"]);
    },
  );
});
