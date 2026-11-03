import { render, screen } from "@testing-library/react-native";
import App from "./App";

describe("App", () => {
  it("shows the product name as a heading", async () => {
    await render(<App />);

    expect(await screen.findByRole("header", { name: "TutorBridge" })).toBeOnTheScreen();
  });
});
