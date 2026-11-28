import {
  budgetLabel,
  draftFormSchema,
  emptyForm,
  startDateHint,
  toDraftInput,
  toFormValues,
  weekdayLabel,
  type NeedDraftInput,
} from "./draftForm";

// Thứ 4 18/11/2026, as in the approved frames.
const TODAY = "2026-11-18";
const schema = draftFormSchema(TODAY);

const errorsOf = (values: Record<string, string>) => {
  const result = schema.safeParse({ ...emptyForm(), ...values });
  return result.success ? {} : Object.fromEntries(result.error.issues.map((issue) => [issue.path[0], issue.message]));
};

describe("PH04 draft form", () => {
  it("AC-UC04-01 an empty form is a valid draft: nothing is sent for an empty field", () => {
    const result = schema.parse(emptyForm());
    expect(toDraftInput(result)).toEqual({});
  });

  it("reads every field into the contract's NeedDraftInput", () => {
    const result = schema.parse({
      goal: "  Củng cố hằng đẳng thức và phân thức đại số trước kỳ thi học kỳ 1.  ",
      durationMinutes: "90",
      frequencyPer7Days: "2",
      budgetVndSession: "400.000",
      cycleStartDate: "18/11/2026",
    });
    expect(toDraftInput(result)).toEqual({
      goal: "Củng cố hằng đẳng thức và phân thức đại số trước kỳ thi học kỳ 1.",
      durationMinutes: 90,
      frequencyPer7Days: 2,
      budgetVndSession: 400000,
      cycleStartDate: "2026-11-18",
    });
  });

  it("shows a stored draft as typed values, and back to the same input", () => {
    const draft: NeedDraftInput = {
      goal: "Ôn tập phân thức đại số lớp 8 cho kỳ thi.",
      durationMinutes: 60,
      frequencyPer7Days: 1,
      budgetVndSession: 250000,
      cycleStartDate: "2026-12-01",
    };
    const values = toFormValues(draft);
    expect(values).toEqual({
      goal: draft.goal,
      durationMinutes: "60",
      frequencyPer7Days: "1",
      budgetVndSession: "250.000",
      cycleStartDate: "01/12/2026",
    });
    expect(toDraftInput(schema.parse(values))).toEqual(draft);
    expect(toFormValues({})).toEqual(emptyForm());
  });

  it("uses the approved copy for a short goal and a bad budget (PH04 'Lỗi')", () => {
    expect(errorsOf({ goal: "Học thêm Toán." })).toEqual({ goal: "Mục tiêu cần ít nhất 20 ký tự (hiện 14)." });
    for (const budget of ["0", "-5", "1.5", "abc"]) {
      expect(errorsOf({ budgetVndSession: budget })).toEqual({ budgetVndSession: "Ngân sách phải là số nguyên lớn hơn 0." });
    }
  });

  it("refuses a goal past 2000 characters and a start date outside today..today+28 (copy approved on #264)", () => {
    expect(errorsOf({ goal: "a".repeat(2001) }).goal).toBeDefined();
    expect(errorsOf({ cycleStartDate: "17/11/2026" }).cycleStartDate).toBe("Chọn ngày từ 18/11/2026 đến 16/12/2026.");
    expect(errorsOf({ cycleStartDate: "17/12/2026" }).cycleStartDate).toBe("Chọn ngày từ 18/11/2026 đến 16/12/2026.");
    expect(errorsOf({ cycleStartDate: "16/12/2026" })).toEqual({});
    for (const typed of ["2026-11-18", "31/02/2027", "18/11"]) {
      expect(errorsOf({ cycleStartDate: typed }).cycleStartDate).toBe("Nhập ngày theo dạng ngày/tháng/năm, ví dụ 18/11/2026.");
    }
  });

  it("names the duration in the budget label and the weekday of the start date", () => {
    expect(budgetLabel("90")).toBe("Ngân sách tối đa cho một buổi 90 phút (VND)");
    expect(budgetLabel("")).toBe("Ngân sách tối đa cho một buổi (VND)");
    expect(weekdayLabel("2026-11-18")).toBe("Thứ 4, 18/11/2026");
    expect(weekdayLabel("2026-11-22")).toBe("Chủ nhật, 22/11/2026");
    expect(startDateHint(TODAY)).toBe("Từ hôm nay đến 16/12/2026. Chu kỳ gồm 4 tuần liên tiếp kể từ ngày này.");
  });
});
