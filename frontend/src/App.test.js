import { render } from "@testing-library/react";
import App from "./App";
import { test, expect } from "@jest/globals"; // bring jest globals into scope

test("renders App component without errors", () => {
  // Just verify the App component renders without throwing errors
  const { container } = render(<App />);
  expect(container).toBeTruthy();
});
