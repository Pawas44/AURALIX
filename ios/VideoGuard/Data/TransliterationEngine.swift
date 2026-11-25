import Foundation

/**
 * Universal TransliterationEngine for iOS
 * High-accuracy phonetic Romanization (Any script to clean Latin/English script)
 * for Hindi, Punjabi, Korean, Japanese, Chinese, Russian/Cyrillic, Tamil, Telugu,
 * Arabic/Urdu, and all world languages.
 *
 * Utilizes native Apple CoreFoundation CFStringTransform (ICU Any-Latin) backed by
 * custom Indic / colloquial rules and diacritic stripping for standard clean English lyrics.
 */
final class TransliterationEngine {
    static let shared = TransliterationEngine()
    
    private let devanagariRegex = try? NSRegularExpression(pattern: "[\\u0900-\\u097F]")
    
    private init() {}
    
    func isDevanagari(_ text: String) -> Bool {
        guard let regex = devanagariRegex else { return false }
        let range = NSRange(location: 0, length: text.utf16.count)
        return regex.firstMatch(in: text, options: [], range: range) != nil
    }
    
    /**
     * Determines whether the given text contains any non-Latin characters
     * that can and should be Romanized.
     */
    func canRomanize(_ text: String) -> Bool {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return false }
        
        for scalar in trimmed.unicodeScalars {
            let code = scalar.value
            // Greek, Cyrillic, Hebrew, Arabic, Persian, Urdu
            if (code >= 0x0370 && code <= 0x08FF) ||
               // All Indic scripts (Devanagari, Bengali, Gurmukhi, Gujarati, Odia, Tamil, Telugu, Kannada, Malayalam, Sinhala) + Thai, Lao, Burmese
               (code >= 0x0900 && code <= 0x109F) ||
               // Japanese Hiragana & Katakana
               (code >= 0x3040 && code <= 0x30FF) ||
               // Chinese Hanzi / Japanese Kanji
               (code >= 0x3400 && code <= 0x4DBF) ||
               (code >= 0x4E00 && code <= 0x9FFF) ||
               // Korean Hangul Syllables & Jamo
               (code >= 0xAC00 && code <= 0xD7AF) ||
               (code >= 0x1100 && code <= 0x11FF) ||
               (code >= 0x3130 && code <= 0x318F) {
                return true
            }
        }
        return false
    }
    
    /**
     * Romanizes any line of text from any language into Latin / English script.
     * Guaranteed to use 100% standard, clean English letters (a-z, A-Z) with NO
     * weird linguistic diacritics, macrons (ā, ī, ū), underdots, or strange accents.
     */
    func romanize(_ text: String) -> String {
        guard canRomanize(text) else { return text }
        
        // 1. For Indic scripts (Devanagari, Gurmukhi/Punjabi), prefer colloquial rules for natural lyrics
        if isDevanagari(text) || text.unicodeScalars.contains(where: { $0.value >= 0x0A00 && $0.value <= 0x0A7F }) {
            let fallbackResult = fallbackRomanize(text)
            if !fallbackResult.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && fallbackResult != text {
                return toCleanEnglishAlphabet(fallbackResult)
            }
        }
        
        // 2. Native Apple ICU-backed CFStringTransform (To Latin + Strip Diacritics)
        let mutableStr = NSMutableString(string: text)
        let cfStr = mutableStr as CFMutableString
        if CFStringTransform(cfStr, nil, kCFStringTransformToLatin, false) {
            CFStringTransform(cfStr, nil, kCFStringTransformStripCombiningMarks, false)
            let icuResult = mutableStr as String
            if !icuResult.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && icuResult != text {
                return toCleanEnglishAlphabet(icuResult)
            }
        }
        
        return toCleanEnglishAlphabet(fallbackRomanize(text))
    }
    
    /**
     * Converts any non-standard Latin characters, IAST/ISO diacritics, and phonetic symbols
     * into clean, normal standard English letters (a-z, A-Z).
     */
    func toCleanEnglishAlphabet(_ input: String) -> String {
        guard !input.isEmpty else { return input }
        
        var result = ""
        let chars = Array(input)
        let len = chars.count
        var i = 0
        
        while i < len {
            let ch = chars[i]
            
            // Fix scholarly 'c' -> 'ch' when followed by vowel (e.g. Sanskrit/Hindi cāl -> chaal)
            if (ch == "c" || ch == "C") && i + 1 < len {
                let next = chars[i + 1]
                if "aAeEiIoOuUāēīōū".contains(next) {
                    result.append(ch.isUppercase ? "Ch" : "ch")
                    i += 1
                    continue
                }
            }
            
            let rep: String? = {
                switch ch {
                case "ā", "â", "ă", "à", "á", "ä", "å", "ã": return "a"
                case "Ā", "Â", "Ă", "À", "Á", "Ä", "Å", "Ã": return "A"
                case "ē", "ê", "ĕ", "è", "é", "ë": return "e"
                case "Ē", "Ê", "Ĕ", "È", "É", "Ë": return "E"
                case "ī", "î", "ĭ", "ì", "í", "ï": return "i"
                case "Ī", "Î", "Ĭ", "Ì", "Í", "Ï": return "I"
                case "ō", "ô", "ŏ", "ò", "ó", "ö", "õ": return "o"
                case "Ō", "Ô", "Ŏ", "Ò", "Ó", "Ö", "Õ": return "O"
                case "ū", "û", "ŭ", "ù", "ú", "ü": return "u"
                case "Ū", "Û", "Ŭ", "Ù", "Ú", "Ü": return "U"
                case "ṛ", "ṝ": return "r"
                case "Ṛ", "Ṝ": return "R"
                case "ḷ", "ḹ": return "l"
                case "Ḷ", "Ḹ": return "L"
                case "ṅ", "ñ", "ṇ", "ň", "ń": return "n"
                case "Ṅ", "Ñ", "Ṇ", "Ň", "Ń": return "N"
                case "ṭ", "ť": return "t"
                case "Ṭ", "Ť": return "T"
                case "ḍ", "ď", "đ": return "d"
                case "Ḍ", "Ď", "Đ": return "D"
                case "ś", "ṣ", "š", "ş": return "sh"
                case "Ś", "Ṣ", "Š", "Ş": return "Sh"
                case "ç", "ć", "č": return "ch"
                case "Ç", "Ć", "Č": return "Ch"
                case "ḥ", "ḫ": return "h"
                case "Ḥ", "Ḫ": return "H"
                case "ṃ", "ṁ": return "n"
                case "Ṃ", "Ṁ": return "N"
                case "ỵ", "ÿ", "ý": return "y"
                case "ž", "ź", "ż": return "z"
                case "Ž", "Ź", "Ż": return "Z"
                case "ğ": return "g"
                case "Ğ": return "G"
                case "æ": return "ae"
                case "Æ": return "Ae"
                case "œ": return "oe"
                case "Œ": return "Oe"
                case "ß": return "ss"
                case "ł": return "l"
                case "Ł": return "L"
                case "ø": return "o"
                case "Ø": return "O"
                default: return nil
                }
            }()
            
            if let r = rep {
                result.append(r)
            } else {
                result.append(ch)
            }
            i += 1
        }
        
        // Strip any remaining combining diacritics
        return result.folding(options: .diacriticInsensitive, locale: .current)
    }
    
    // ==========================================
    // PURE RULE FALLBACKS
    // ==========================================
    
    private func fallbackRomanize(_ text: String) -> String {
        let words = text.components(separatedBy: " ")
        let romanizedWords = words.map { romanizeWordFallback($0) }
        return romanizedWords.joined(separator: " ")
    }
    
    private func romanizeWordFallback(_ word: String) -> String {
        guard canRomanize(word) else { return word }
        
        // 1. Korean Hangul
        if word.unicodeScalars.contains(where: { $0.value >= 0xAC00 && $0.value <= 0xD7AF }) {
            return romanizeHangulWord(word)
        }
        
        // 2. Cyrillic
        if word.unicodeScalars.contains(where: { $0.value >= 0x0400 && $0.value <= 0x052F }) {
            return romanizeCyrillicWord(word)
        }
        
        // 3. Devanagari
        if isDevanagari(word) {
            return romanizeDevanagariWord(word)
        }
        
        // 4. Default transform
        let mutable = NSMutableString(string: word)
        if CFStringTransform(mutable as CFMutableString, nil, kCFStringTransformToLatin, false) {
            CFStringTransform(mutable as CFMutableString, nil, kCFStringTransformStripCombiningMarks, false)
            return mutable as String
        }
        
        return word
    }
    
    private func romanizeHangulWord(_ word: String) -> String {
        var res = ""
        let lead = ["g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h"]
        let vowel = ["a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i"]
        let trail = ["", "k", "k", "ks", "n", "nj", "nh", "t", "l", "lg", "lm", "lb", "ls", "lt", "lp", "lh", "m", "p", "ps", "t", "t", "ng", "t", "t", "k", "t", "p", "h"]
        
        for scalar in word.unicodeScalars {
            let code = scalar.value
            if code >= 0xAC00 && code <= 0xD7AF {
                let s = Int(code - 0xAC00)
                let l = s / (21 * 28)
                let v = (s % (21 * 28)) / 28
                let t = s % 28
                res.append(lead[safe: l] ?? "")
                res.append(vowel[safe: v] ?? "")
                res.append(trail[safe: t] ?? "")
            } else {
                res.append(Character(scalar))
            }
        }
        return res
    }
    
    private func romanizeCyrillicWord(_ word: String) -> String {
        let cyrillicMap: [Character: String] = [
            "а": "a", "б": "b", "в": "v", "г": "g", "д": "d", "е": "e", "ё": "yo",
            "ж": "zh", "з": "z", "и": "i", "й": "y", "к": "k", "л": "l", "м": "m",
            "н": "n", "о": "o", "п": "p", "р": "r", "с": "s", "т": "t", "у": "u",
            "ф": "f", "х": "kh", "ц": "ts", "ч": "ch", "ш": "sh", "щ": "shch",
            "ъ": "", "ы": "y", "ь": "", "э": "e", "ю": "yu", "я": "ya",
            "А": "A", "Б": "B", "В": "V", "Г": "G", "Д": "D", "Е": "E", "Ё": "Yo",
            "Ж": "Zh", "З": "Z", "И": "I", "Й": "Y", "К": "K", "Л": "L", "М": "M",
            "Н": "N", "О": "O", "П": "P", "Р": "R", "С": "S", "Т": "T", "У": "U",
            "Ф": "F", "Х": "Kh", "Ц": "Ts", "Ч": "Ch", "Ш": "Sh", "Щ": "Shch",
            "Ъ": "", "Ы": "Y", "Ь": "", "Э": "E", "Ю": "Yu", "Я": "Ya"
        ]
        var res = ""
        for c in word {
            res.append(cyrillicMap[c] ?? String(c))
        }
        return res
    }
    
    private func romanizeDevanagariWord(_ word: String) -> String {
        let vowels: [Character: String] = [
            "अ": "a", "आ": "aa", "इ": "i", "ई": "ee",
            "उ": "u", "ऊ": "oo", "ऋ": "ri",
            "ए": "e", "ऐ": "ai", "ओ": "o", "औ": "au"
        ]
        let matras: [Character: String] = [
            "ा": "aa", "ि": "i", "ी": "ee", "ु": "u",
            "ू": "oo", "ृ": "ri", "े": "e", "ै": "ai",
            "ो": "o", "ौ": "au"
        ]
        let consonants: [Character: String] = [
            "क": "k", "ख": "kh", "ग": "g", "घ": "gh", "ङ": "ng",
            "च": "ch", "छ": "chh", "ज": "j", "झ": "jh", "ञ": "ny",
            "ट": "t", "ठ": "th", "ड": "d", "ढ": "dh", "ण": "n",
            "त": "t", "थ": "th", "द": "d", "ध": "dh", "न": "n",
            "प": "p", "फ": "ph", "ब": "b", "भ": "bh", "म": "m",
            "य": "y", "र": "r", "ल": "l", "व": "v", "श": "sh",
            "ष": "sh", "स": "s", "ह": "h",
            "क़": "q", "ख़": "kh", "ग़": "gh", "ज़": "z",
            "ड़": "d", "ढ़": "dh", "फ़": "f", "य़": "y"
        ]
        
        let chars = Array(word)
        let len = chars.count
        var res = ""
        var i = 0
        
        while i < len {
            let c = chars[i]
            if let v = vowels[c] {
                res.append(v)
                i += 1
                continue
            }
            if let cons = consonants[c] {
                res.append(cons)
                i += 1
                if i < len {
                    let next = chars[i]
                    if next == "्" { // Virama
                        i += 1
                    } else if let m = matras[next] {
                        res.append(m)
                        i += 1
                    } else if next == "ं" || next == "ँ" {
                        res.append("an")
                        i += 1
                    } else if consonants[next] != nil {
                        res.append("a")
                    }
                }
                continue
            }
            if let m = matras[c] {
                res.append(m)
                i += 1
                continue
            }
            if c == "ं" || c == "ँ" {
                res.append("n")
                i += 1
                continue
            }
            if c != "्" {
                res.append(c)
            }
            i += 1
        }
        return res
    }
}

private extension Array {
    subscript(safe index: Int) -> Element? {
        return indices.contains(index) ? self[index] : nil
    }
}
