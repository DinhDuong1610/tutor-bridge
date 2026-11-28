// #123: react-native exports View, Text and the rest through lazy getters, so Babel compiled them inside the
// first test of each file, where a cold cache took longer than the 5 s test timeout. Touching them here
// compiles them while the file is set up, outside any test.
import * as ReactNative from "react-native";

const USED_BY_THE_APP = [
  "ActivityIndicator",
  "KeyboardAvoidingView",
  "Modal",
  "Pressable",
  "ScrollView",
  "StyleSheet",
  "Text",
  "TextInput",
  "View",
] as const;

for (const name of USED_BY_THE_APP) {
  void ReactNative[name];
}
