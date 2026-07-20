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

// Latin -> Ukrainian Cyrillic, for typing Ukrainian on a feature-phone keypad that only has
// Latin letters (wrapped in <ua>...</ua> in the reply box, see resolveUaTags below). Not a
// true inverse of BASE_MAP - several Cyrillic letters collapse to the same Latin output there
// (и/ы -> "y", і/ї/й -> "i"), so this picks the Ukrainian-typical letter (и, і) rather than
// trying to recover which original letter it was. Checked longest-first so e.g. "shch" isn't
// consumed as "sh" + "ch" first.
const REVERSE_DIGRAPHS: [string, string][] = [
  ["shch", "щ"],
  ["zh", "ж"], ["kh", "х"], ["ts", "ц"], ["ch", "ч"], ["sh", "ш"],
  ["iu", "ю"], ["yu", "ю"], ["ia", "я"], ["ya", "я"], ["ie", "є"], ["ye", "є"],
  ["io", "ьо"],
];

const REVERSE_SINGLE_MAP: Record<string, string> = {
  a: "а", b: "б", v: "в", h: "г", g: "ґ", d: "д", e: "е",
  z: "з", y: "и", i: "і", k: "к", l: "л", m: "м", n: "н",
  o: "о", p: "п", r: "р", s: "с", t: "т", u: "у", f: "ф",
  "'": "ь",
};

// A bare "i" is itself ambiguous: standalone/after a consonant it's "і" ("fil'm" -> "фільм"),
// but after a vowel it's the end of a diphthong and reads as "й" ("tsei" -> "цей", "mii" ->
// "мій"). Checked against the raw input letter, not the resolved Cyrillic one, so this only
// needs the Latin vowel set.
const LATIN_VOWELS = new Set(["a", "e", "i", "o", "u", "y"]);

export function reverseTransliterate(text: string): string {
  const chars = Array.from(text);
  let result = "";
  let i = 0;

  outer: while (i < chars.length) {
    for (const [latin, cyrillic] of REVERSE_DIGRAPHS) {
      const slice = chars.slice(i, i + latin.length).join("").toLowerCase();
      if (slice === latin) {
        result += applyCase(chars[i], cyrillic);
        i += latin.length;
        continue outer;
      }
    }

    const char = chars[i];
    const lower = char.toLowerCase();

    if (lower === "i" && i > 0 && LATIN_VOWELS.has(chars[i - 1].toLowerCase())) {
      result += applyCase(char, "й");
      i += 1;
      continue;
    }

    const mapped = REVERSE_SINGLE_MAP[lower];
    result += mapped === undefined ? char : applyCase(char, mapped);
    i += 1;
  }

  return result;
}

const UA_TAG_PATTERN = /<ua>([\s\S]*?)<\/ua>/gi;

/**
 * The reply box is a plain HTML form on the legacy client (no JS, no Cyrillic keyboard) - a
 * message can wrap a phonetic-Latin span in <ua>...</ua> to have it sent to Telegram as real
 * Ukrainian Cyrillic instead. Text outside the tags is left untouched.
 */
export function resolveUaTags(text: string): string {
  return text.replace(UA_TAG_PATTERN, (_match, inner: string) => reverseTransliterate(inner));
}
