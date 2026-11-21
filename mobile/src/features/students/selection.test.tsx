import { act, fireEvent, render, screen } from "@testing-library/react-native";
import { Pressable, Text } from "react-native";
import { SelectedStudentProvider, useSelectedStudent } from "./selection";

// Fake children only (AGENTS.md).
const MINH = { id: "a", displayName: "Minh An" };
const NGOC = { id: "b", displayName: "Bảo Ngọc" };

function Probe({ items, choose }: { items: { id: string; displayName: string }[]; choose?: string }) {
  const { selected, select } = useSelectedStudent(items);
  return (
    <>
      <Text testID="selected">{selected?.displayName ?? "none"}</Text>
      {choose ? <Pressable testID="choose" onPress={() => select(choose)} /> : null}
    </>
  );
}

const tree = (items: { id: string; displayName: string }[], choose?: string) => (
  <SelectedStudentProvider>
    <Probe items={items} choose={choose} />
  </SelectedStudentProvider>
);

describe("useSelectedStudent", () => {
  it("starts on the first child and has none when the list is empty", async () => {
    const { rerender } = await render(tree([MINH, NGOC]));
    expect(screen.getByTestId("selected")).toHaveTextContent("Minh An");

    await rerender(tree([]));
    expect(screen.getByTestId("selected")).toHaveTextContent("none");
  });

  it("keeps the chosen child across new lists and falls back to the first when it is gone", async () => {
    const { rerender } = await render(tree([MINH, NGOC], NGOC.id));
    await act(async () => fireEvent.press(screen.getByTestId("choose")));
    expect(screen.getByTestId("selected")).toHaveTextContent("Bảo Ngọc");

    // A reloaded list that still has the child keeps it.
    await rerender(tree([MINH, NGOC]));
    expect(screen.getByTestId("selected")).toHaveTextContent("Bảo Ngọc");

    // Deleted elsewhere: back to the first child, never a child that is not listed.
    await rerender(tree([MINH]));
    expect(screen.getByTestId("selected")).toHaveTextContent("Minh An");
  });

  it("refuses to run outside the parent area's provider", async () => {
    const quiet = jest.spyOn(console, "error").mockImplementation(() => {});
    await expect(render(<Probe items={[MINH]} />)).rejects.toThrow(/SelectedStudentProvider/);
    quiet.mockRestore();
  });
});
