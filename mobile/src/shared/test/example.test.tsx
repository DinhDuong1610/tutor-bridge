/**
 * The pattern for a feature screen and its test, using every foundation piece: session, QueryScreen,
 * react-hook-form with zod, useCommand, CommandStatus and mockApi. The screen lives in this test on
 * purpose; the real PH02 belongs to GD-11.
 */
import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient, useQuery } from "@tanstack/react-query";
import { act, fireEvent, screen, waitFor } from "@testing-library/react-native";
import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { Text, View } from "react-native";
import { z } from "zod";
import { api } from "@/shared/api/client";
import { useCommand } from "@/shared/api/command";
import { call } from "@/shared/api/errors";
import { FormTextField } from "@/shared/form/FormTextField";
import { useServerErrors } from "@/shared/form/serverErrors";
import { Button } from "@/shared/ui/Button";
import { CommandStatus } from "@/shared/ui/CommandStatus";
import { Notice } from "@/shared/ui/Notice";
import { QueryScreen } from "@/shared/ui/QueryScreen";
import { json, mockApi, problem } from "./mockApi";
import { renderScreen } from "./renderScreen";
import { fakeSession } from "./session";

// --- Example screen -----------------------------------------------------------------------------

// Limits from the contract (StudentProfileInput). The server checks them again.
const studentForm = z.object({
  displayName: z.string().trim().min(1, "Nhập tên hiển thị.").max(100, "Tên tối đa 100 ký tự."),
  shortGoal: z.string().max(2000, "Mục tiêu tối đa 2.000 ký tự."),
});
type StudentForm = z.infer<typeof studentForm>;

function ExampleStudentsScreen() {
  const queryClient = useQueryClient();
  const students = useQuery({ queryKey: ["students"], queryFn: () => call(api.GET("/students", {})) });
  const form = useForm<StudentForm>({ resolver: zodResolver(studentForm), defaultValues: { displayName: "", shortGoal: "" } });
  const create = useCommand((input: StudentForm, key: string) =>
    call(api.POST("/students", { params: { header: { "Idempotency-Key": key } }, body: { ...input, grade: 8 } })),
  );
  const otherErrors = useServerErrors(create.error, form.setError, ["displayName", "shortGoal"]);

  useEffect(() => {
    if (create.state === "confirmed") {
      form.reset();
      void queryClient.invalidateQueries({ queryKey: ["students"] });
    }
  }, [create.state, form, queryClient]);

  return (
    // No `empty` prop: the form must stay visible when the list is empty.
    <QueryScreen title="Hồ sơ các con" query={students}>
      {(page) => (
        <View>
          {page.items.length === 0 ? <Notice tone="info" message="Chưa có hồ sơ con nào." /> : null}
          {page.items.map((student) => (
            <Text key={student.id}>{student.displayName}</Text>
          ))}
          <FormTextField control={form.control} name="displayName" label="Tên hiển thị" />
          <FormTextField control={form.control} name="shortGoal" label="Mục tiêu ngắn" multiline />
          {otherErrors.map((message) => (
            <Notice key={message} tone="danger" message={message} />
          ))}
          <CommandStatus command={create} confirmedMessage="Đã thêm hồ sơ." />
          <Button title="Thêm hồ sơ" busy={create.state === "sending"} onPress={form.handleSubmit((values) => create.submit(values))} />
        </View>
      )}
    </QueryScreen>
  );
}

// --- Test ---------------------------------------------------------------------------------------

const MINH = { id: "0b7c4f3e-2a51-4a8e-9a43-6d1f0c2b9e10", displayName: "Minh", grade: 8, revision: 1 };
const LAN = { id: "1c8d5a4f-3b62-4b9f-8b54-7e2a1d3c0f21", displayName: "Lan", grade: 8, revision: 1 };

describe("example feature screen", () => {
  let server: ReturnType<typeof mockApi>;
  afterEach(() => server.restore());

  it("lists the children of the signed-in parent with the session token", async () => {
    server = mockApi({ "GET /students": () => json(200, { items: [MINH], nextCursor: null }) });

    await renderScreen(<ExampleStudentsScreen />, { session: fakeSession("PARENT") });

    expect(await screen.findByText("Minh")).toBeOnTheScreen();
    expect(server.requests[0].headers.get("Authorization")).toBe("Bearer test-access-token-not-a-secret");
    expect(server.unexpected).toEqual([]);
  });

  it("checks the form before sending anything", async () => {
    server = mockApi({ "GET /students": () => json(200, { items: [], nextCursor: null }) });
    await renderScreen(<ExampleStudentsScreen />, { session: fakeSession("PARENT") });
    await screen.findByText("Chưa có hồ sơ con nào.");

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Thêm hồ sơ" })));

    expect(await screen.findByText("Lỗi: Nhập tên hiển thị.")).toBeOnTheScreen();
    expect(server.requests.filter((r) => r.method === "POST")).toHaveLength(0);
  });

  it("puts a server field error at the field, keeps the input, then succeeds with a new key", async () => {
    let created = false;
    server = mockApi({
      "GET /students": () => json(200, { items: created ? [MINH, LAN] : [MINH], nextCursor: null }),
      "POST /students": async (request) => {
        const body = (await request.json()) as { displayName: string };
        if (body.displayName === "Lan!") {
          return problem(422, "VALIDATION_FAILED", "Dữ liệu chưa hợp lệ.", [
            { path: "displayName", message: "Tên không được chứa ký tự đặc biệt." },
          ]);
        }
        created = true;
        return json(201, LAN);
      },
    });
    await renderScreen(<ExampleStudentsScreen />, { session: fakeSession("PARENT") });
    await screen.findByText("Minh");

    await fireEvent.changeText(screen.getByLabelText("Tên hiển thị"), "Lan!");
    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Thêm hồ sơ" })));

    expect(await screen.findByText("Lỗi: Tên không được chứa ký tự đặc biệt.")).toBeOnTheScreen();
    expect(screen.getByText("Vui lòng sửa các ô được đánh dấu rồi gửi lại.")).toBeOnTheScreen();
    expect(screen.getByLabelText("Tên hiển thị")).toHaveDisplayValue("Lan!");

    await fireEvent.changeText(screen.getByLabelText("Tên hiển thị"), "Lan");
    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Thêm hồ sơ" })));

    expect(await screen.findByText("Đã thêm hồ sơ.")).toBeOnTheScreen();
    expect(await screen.findByText("Lan")).toBeOnTheScreen();
    const posts = server.requests.filter((r) => r.method === "POST");
    expect(posts).toHaveLength(2);
    const [firstKey, secondKey] = posts.map((r) => r.headers.get("Idempotency-Key"));
    expect(firstKey).toMatch(/^[0-9a-f]{32}$/);
    expect(secondKey).not.toBe(firstKey);
    expect(await posts[1].json()).toEqual({ displayName: "Lan", shortGoal: "", grade: 8 });
    expect(server.unexpected).toEqual([]);
  });

  it("retries an unknown outcome with the same key", async () => {
    let attempts = 0;
    server = mockApi({
      "GET /students": () => json(200, { items: [MINH], nextCursor: null }),
      "POST /students": () => {
        attempts += 1;
        if (attempts === 1) throw new TypeError("Network request failed");
        return json(201, LAN);
      },
    });
    await renderScreen(<ExampleStudentsScreen />, { session: fakeSession("PARENT") });
    await screen.findByText("Minh");

    await fireEvent.changeText(screen.getByLabelText("Tên hiển thị"), "Lan");
    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Thêm hồ sơ" })));
    expect(await screen.findByText(/Chưa rõ kết quả/)).toBeOnTheScreen();

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Thử lại" })));

    expect(await screen.findByText("Đã thêm hồ sơ.")).toBeOnTheScreen();
    await waitFor(() => expect(attempts).toBe(2));
    const keys = server.requests.filter((r) => r.method === "POST").map((r) => r.headers.get("Idempotency-Key"));
    expect(keys[1]).toBe(keys[0]);
  });
});
