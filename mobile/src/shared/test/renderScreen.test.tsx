import { screen } from "@testing-library/react-native";
import { Text, View } from "react-native";
import { renderScreen } from "./renderScreen";
import { fakeSession } from "./session";

function TwoSectionScreen() {
  return (
    <View>
      <Text accessibilityRole="header">Hồ sơ các con</Text>
      <Text>Danh sách</Text>
      <Text accessibilityRole="header">Thêm hồ sơ con</Text>
    </View>
  );
}

describe("renderScreen", () => {
  it("renders a screen that has a section title as a second header", async () => {
    await renderScreen(<TwoSectionScreen />, { session: fakeSession("PARENT") });

    expect(screen.getAllByRole("header")).toHaveLength(2);
    expect(screen.getByRole("header", { name: "Thêm hồ sơ con" })).toBeOnTheScreen();
  });

  it("renders without a session", async () => {
    await renderScreen(<TwoSectionScreen />);

    expect(screen.getByText("Danh sách")).toBeOnTheScreen();
  });
});
