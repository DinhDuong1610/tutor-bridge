/** JSON Schema counts code points, not UTF-16 units, so an emoji counts as one character. */
export function lengthOf(value: string): number {
  return [...value].length;
}
