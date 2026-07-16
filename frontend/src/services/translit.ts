// Ukrainian-focused Cyrillic -> Latin transliteration, with a few Russian-only letters
// (ы/э/ъ/ё) included too since real messages sometimes mix in Russian text. Simplified
// from the official Ukrainian national transliteration standard (KMU resolution 55/2010):
// that standard uses different mappings for є/ї/й/ю/я at the start of a word vs mid-word;
// here every occurrence uses the mid-word form, since "readable-ish on a feature phone
// font" doesn't need to be a precise passport-grade transliteration.
const BASE_MAP: Record<string, string> = {
  а: "a", б: "b", в: "v", г: "h", ґ: "g", д: "d", е: "e", є: "ie",
  ж: "zh", з: "z", и: "y", і: "i", ї: "i", й: "i", к: "k", л: "l",
  м: "m", н: "n", о: "o", п: "p", р: "r", с: "s", т: "t", у: "u",
  ф: "f", х: "kh", ц: "ts", ч: "ch", ш: "sh", щ: "shch", ь: "'",
  ю: "iu", я: "ia", ы: "y", э: "e", ъ: "", ё: "yo",
};

// Checked before falling back to single-character mapping - "ьо" reads as one "yo"
// sound (e.g. "Льоша" -> "Liosha"), which the plain per-letter map (ь dropped/apostrophe,
// о -> o) would otherwise flatten to "Losha"/"L'osha", losing that sound entirely.
const DIGRAPHS: Record<string, string> = {
  ьо: "io",
};

function applyCase(original: string, mapped: string): string {
  if (mapped.length === 0) return mapped;
  return original !== original.toLowerCase()
    ? mapped.charAt(0).toUpperCase() + mapped.slice(1)
    : mapped;
}

/**
 * Opera Mini on the Barbie Phone's font can't render Cyrillic at all (shows as tofu
 * boxes) - this makes the legacy render path show something readable instead. Only
 * applied to the legacy view; modern clients render Cyrillic fine and see the original text.
 */
export function transliterate(text: string): string {
  const chars = Array.from(text);
  let result = "";
  let i = 0;

  while (i < chars.length) {
    const pair = i + 1 < chars.length ? chars[i] + chars[i + 1] : undefined;
    const digraph = pair ? DIGRAPHS[pair.toLowerCase()] : undefined;

    if (pair && digraph !== undefined) {
      result += applyCase(chars[i], digraph);
      i += 2;
      continue;
    }

    const char = chars[i];
    const mapped = BASE_MAP[char.toLowerCase()];
    result += mapped === undefined ? char : applyCase(char, mapped);
    i += 1;
  }

  return result;
}
