package com.videoguard.app.data

/**
 * Universal TransliterationEngine
 * Provides high-accuracy phonetic Romanization (Any script to Latin/English script)
 * for Hindi, Punjabi, Korean, Japanese, Chinese, Russian/Cyrillic, Tamil, Telugu,
 * Arabic/Urdu, Spanish/French, and all world languages.
 *
 * Uses Android ICU Any-Latin on Android 10+ (API 29+), covering literally every language,
 * with pure Kotlin phonetic rule fallbacks for Indic, Cyrillic, Korean, Japanese, etc.
 */
object TransliterationEngine {

    private val devanagariRegex = Regex("[\\u0900-\\u097F]")

    fun isDevanagari(text: String): Boolean {
        return devanagariRegex.containsMatchIn(text)
    }

    /**
     * Determines whether the given text contains any non-Latin characters
     * that can and should be Romanized.
     */
    fun canRomanize(text: String): Boolean {
        if (text.isBlank()) return false
        return text.any { c ->
            val code = c.code
            // Greek, Cyrillic, Hebrew, Arabic, Persian, Urdu
            (code in 0x0370..0x08FF) ||
            // All Indic scripts (Devanagari, Bengali, Gurmukhi, Gujarati, Odia, Tamil, Telugu, Kannada, Malayalam, Sinhala) + Thai, Lao, Burmese
            (code in 0x0900..0x109F) ||
            // Japanese Hiragana & Katakana
            (code in 0x3040..0x30FF) ||
            // Chinese Hanzi / Japanese Kanji
            (code in 0x3400..0x4DBF) ||
            (code in 0x4E00..0x9FFF) ||
            // Korean Hangul Syllables & Jamo
            (code in 0xAC00..0xD7AF) ||
            (code in 0x1100..0x11FF) ||
            (code in 0x3130..0x318F)
        }
    }

    /**
     * Romanizes any line of text from any language into Latin / English script.
     * Preserves punctuation, whitespace, and existing Latin text.
     */
    fun romanize(text: String): String {
        if (!canRomanize(text)) return text

        // 1. Primary Engine: Android ICU Any-Latin (Standard across all Unicode scripts on API 29+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val icuResult = IcuHelper.transliterate(text)
            if (!icuResult.isNullOrBlank() && icuResult != text) {
                return icuResult.trimEnd()
            }
        }

        // 2. High-precision pure Kotlin fallback for Indic, Cyrillic, Hangul, Kana, etc.
        return fallbackRomanize(text)
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.Q)
    private object IcuHelper {
        private var transliterator: android.icu.text.Transliterator? = null
        private var initAttempted = false

        fun transliterate(text: String): String? {
            return try {
                if (!initAttempted) {
                    initAttempted = true
                    transliterator = android.icu.text.Transliterator.getInstance("Any-Latin")
                }
                synchronized(this) {
                    transliterator?.transliterate(text)
                }
            } catch (e: Throwable) {
                null
            }
        }
    }

    // ==========================================
    // PURE KOTLIN FALLBACK RULES
    // ==========================================

    private fun fallbackRomanize(text: String): String {
        val sb = StringBuilder()
        val words = text.split(Regex("(?<=\\s)|(?=\\s)"))

        for (word in words) {
            sb.append(romanizeWordFallback(word))
        }

        return sb.toString().trimEnd()
    }

    private fun romanizeWordFallback(word: String): String {
        if (!canRomanize(word)) return word

        // 1. Korean Hangul
        if (word.any { it.code in 0xAC00..0xD7AF }) {
            return romanizeHangulWord(word)
        }

        // 2. Cyrillic (Russian, etc.)
        if (word.any { it.code in 0x0400..0x052F }) {
            return romanizeCyrillicWord(word)
        }

        // 3. Gurmukhi (Punjabi)
        if (word.any { it.code in 0x0A00..0x0A7F }) {
            return romanizeGurmukhiWord(word)
        }

        // 4. Japanese Kana
        if (word.any { it.code in 0x3040..0x30FF }) {
            return romanizeKanaWord(word)
        }

        // 5. Devanagari (Hindi, Haryanvi, Marathi, etc.)
        if (isDevanagari(word)) {
            return romanizeDevanagariWord(word)
        }

        return word
    }

    // --- Korean Hangul Syllable Decomposition ---
    private fun romanizeHangulWord(word: String): String {
        val sb = StringBuilder()
        val lead = listOf("g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h")
        val vowel = listOf("a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i")
        val trail = listOf("", "k", "k", "ks", "n", "nj", "nh", "t", "l", "lg", "lm", "lb", "ls", "lt", "lp", "lh", "m", "p", "ps", "t", "t", "ng", "t", "t", "k", "t", "p", "h")

        for (c in word) {
            val code = c.code
            if (code in 0xAC00..0xD7AF) {
                val s = code - 0xAC00
                val l = s / (21 * 28)
                val v = (s % (21 * 28)) / 28
                val t = s % 28
                sb.append(lead.getOrElse(l) { "" })
                sb.append(vowel.getOrElse(v) { "" })
                sb.append(trail.getOrElse(t) { "" })
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    // --- Cyrillic ---
    private val cyrillicMap = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo",
        'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
        'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
        'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch",
        'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
        'А' to "A", 'Б' to "B", 'В' to "V", 'Г' to "G", 'Д' to "D", 'Е' to "E", 'Ё' to "Yo",
        'Ж' to "Zh", 'З' to "Z", 'И' to "I", 'Й' to "Y", 'К' to "K", 'Л' to "L", 'М' to "M",
        'Н' to "N", 'О' to "O", 'П' to "P", 'Р' to "R", 'С' to "S", 'Т' to "T", 'У' to "U",
        'Ф' to "F", 'Х' to "Kh", 'Ц' to "Ts", 'Ч' to "Ch", 'Ш' to "Sh", 'Щ' to "Shch",
        'Ъ' to "", 'Ы' to "Y", 'Ь' to "", 'Э' to "E", 'Ю' to "Yu", 'Я' to "Ya"
    )

    private fun romanizeCyrillicWord(word: String): String {
        val sb = StringBuilder()
        for (c in word) {
            sb.append(cyrillicMap[c] ?: c.toString())
        }
        return sb.toString()
    }

    // --- Gurmukhi (Punjabi) ---
    private val gurmukhiVowels = mapOf(
        'ਅ' to "a", 'ਆ' to "aa", 'ਇ' to "i", 'ਈ' to "ee",
        'ਉ' to "u", 'ਊ' to "oo", 'ਏ' to "e", 'ਐ' to "ai",
        'ਓ' to "o", 'ਔ' to "au"
    )
    private val gurmukhiMatras = mapOf(
        'ਾ' to "aa", 'ਿ' to "i", 'ੀ' to "ee", 'ੁ' to "u",
        'ੂ' to "oo", 'ੇ' to "e", 'ੈ' to "ai", 'ੋ' to "o",
        'ੌ' to "au"
    )
    private val gurmukhiConsonants = mapOf(
        'ਕ' to "k", 'ਖ' to "kh", 'ਗ' to "g", 'ਘ' to "gh", 'ਙ' to "ng",
        'ਚ' to "ch", 'ਛ' to "chh", 'ਜ' to "j", 'ਝ' to "jh", 'ਞ' to "ny",
        'ਟ' to "t", 'ਠ' to "th", 'ਡ' to "d", 'ਢ' to "dh", 'ਣ' to "n",
        'ਤ' to "t", 'ਥ' to "th", 'ਦ' to "d", 'ਧ' to "dh", 'ਨ' to "n",
        'ਪ' to "p", 'ਫ' to "ph", 'ਬ' to "b", 'ਭ' to "bh", 'ਮ' to "m",
        'ਯ' to "y", 'ਰ' to "r", 'ਲ' to "l", 'ਵ' to "v", 'ੜ' to "r",
        'ਸ਼' to "sh", 'ਖ਼' to "kh", 'ਗ਼' to "gh", 'ਜ਼' to "z", 'ਫ਼' to "f", 'ਲ਼' to "l"
    )

    private fun romanizeGurmukhiWord(word: String): String {
        val sb = StringBuilder()
        val len = word.length
        var i = 0
        while (i < len) {
            val c = word[i]
            if (gurmukhiVowels.containsKey(c)) {
                sb.append(gurmukhiVowels[c])
                i++
                continue
            }
            if (gurmukhiConsonants.containsKey(c)) {
                val base = gurmukhiConsonants[c]!!
                sb.append(base)
                i++
                if (i < len) {
                    val next = word[i]
                    if (next == '੍') { // Virama
                        i++
                    } else if (gurmukhiMatras.containsKey(next)) {
                        sb.append(gurmukhiMatras[next])
                        i++
                    } else if (next == 'ਂ' || next == 'ੰ') { // Bindi / Tippi
                        sb.append("an")
                        i++
                    } else if (gurmukhiConsonants.containsKey(next)) {
                        sb.append("a")
                    }
                }
                continue
            }
            if (gurmukhiMatras.containsKey(c)) {
                sb.append(gurmukhiMatras[c])
                i++
                continue
            }
            if (c == 'ਂ' || c == 'ੰ') {
                sb.append("n")
                i++
                continue
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    // --- Japanese Kana ---
    private val kanaMap = mapOf(
        'あ' to "a", 'い' to "i", 'う' to "u", 'え' to "e", 'お' to "o",
        'か' to "ka", 'き' to "ki", 'く' to "ku", 'け' to "ke", 'こ' to "ko",
        'さ' to "sa", 'し' to "shi", 'す' to "su", 'せ' to "se", 'そ' to "so",
        'た' to "ta", 'ち' to "chi", 'つ' to "tsu", 'て' to "te", 'と' to "to",
        'な' to "na", 'に' to "ni", 'ぬ' to "nu", 'ね' to "ne", 'の' to "no",
        'は' to "ha", 'ひ' to "hi", 'ふ' to "fu", 'へ' to "he", 'ほ' to "ho",
        'ま' to "ma", 'み' to "mi", 'む' to "mu", 'め' to "me", 'も' to "mo",
        'や' to "ya", 'ゆ' to "yu", 'よ' to "yo",
        'ら' to "ra", 'り' to "ri", 'る' to "ru", 'れ' to "re", 'ろ' to "ro",
        'わ' to "wa", 'を' to "wo", 'ん' to "n",
        'が' to "ga", 'ぎ' to "gi", 'ぐ' to "gu", 'げ' to "ge", 'ご' to "go",
        'ざ' to "za", 'じ' to "ji", 'ず' to "zu", 'ぜ' to "ze", 'ぞ' to "zo",
        'だ' to "da", 'ぢ' to "ji", 'づ' to "zu", 'де' to "de", 'ど' to "do",
        'ば' to "ba", 'び' to "bi", 'ぶ' to "bu", 'べ' to "be", 'ぼ' to "bo",
        'ぱ' to "pa", 'ぴ' to "pi", 'ぷ' to "pu", 'ぺ' to "pe", 'ぽ' to "po",
        // Katakana
        'ア' to "a", 'イ' to "i", 'ウ' to "u", 'エ' to "e", 'オ' to "o",
        'カ' to "ka", 'キ' to "ki", 'ク' to "ku", 'ケ' to "ke", 'コ' to "ko",
        'サ' to "sa", 'シ' to "shi", 'ス' to "su", 'セ' to "se", 'ソ' to "so",
        'タ' to "ta", 'チ' to "chi", 'ツ' to "tsu", 'テ' to "te", 'ト' to "to",
        'ナ' to "na", 'ニ' to "ni", 'ヌ' to "nu", 'ネ' to "ne", 'ノ' to "no",
        'ハ' to "ha", 'ヒ' to "hi", 'フ' to "fu", 'ヘ' to "he", 'ホ' to "ho",
        'マ' to "ma", 'ミ' to "mi", 'ム' to "mu", 'メ' to "me", 'モ' to "mo",
        'ヤ' to "ya", 'ユ' to "yu", 'ヨ' to "yo",
        'ラ' to "ra", 'リ' to "ri", 'ル' to "ru", 'レ' to "re", 'ロ' to "ro",
        'ワ' to "wa", 'ヲ' to "wo", 'ン' to "n",
        'ガ' to "ga", 'ギ' to "gi", 'グ' to "gu", 'ゲ' to "ge", 'ゴ' to "go",
        'ザ' to "za", 'ジ' to "ji", 'ズ' to "zu", 'ゼ' to "ze", 'ゾ' to "zo",
        'ダ' to "da", 'バ' to "ba", 'ビ' to "bi", 'ブ' to "bu", 'ベ' to "be", 'ボ' to "bo",
        'パ' to "pa", 'ピ' to "pi", 'プ' to "pu", 'ペ' to "pe", 'ポ' to "po"
    )

    private fun romanizeKanaWord(word: String): String {
        val sb = StringBuilder()
        for (c in word) {
            sb.append(kanaMap[c] ?: c.toString())
        }
        return sb.toString()
    }

    // --- Devanagari (Hindi, Marathi, Haryanvi) ---
    private val devanagariVowels = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee",
        'उ' to "u", 'ऊ' to "oo", 'ऋ' to "ri",
        'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au"
    )
    private val devanagariMatras = mapOf(
        'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u",
        'ू' to "oo", 'ृ' to "ri", 'े' to "e", 'ै' to "ai",
        'ो' to "o", 'ौ' to "au"
    )
    private val devanagariConsonants = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "ng",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "ny",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "sh",
        'ष' to "sh", 'स' to "s", 'ह' to "h",
        'क़' to "q", 'ख़' to "kh", 'ग़' to "gh", 'ज़' to "z",
        'ड़' to "r", 'ढ़' to "rh", 'फ़' to "f", 'य़' to "y"
    )
    private const val VIRAMA = '्'
    private const val ANUSVARA = 'ं'
    private const val CHANDRABINDU = 'ँ'
    private const val VISARGA = 'ः'
    private const val NUKTA = '़'

    private fun romanizeDevanagariWord(word: String): String {
        val sb = StringBuilder()
        val len = word.length
        var i = 0

        while (i < len) {
            val c = word[i]

            // Check Nukta composite
            if (i + 1 < len && word[i + 1] == NUKTA) {
                val trans = when (c) {
                    'क' -> "q"
                    'ख' -> "kh"
                    'ग' -> "gh"
                    'ज' -> "z"
                    'ड' -> "r"
                    'ढ' -> "rh"
                    'फ' -> "f"
                    else -> devanagariConsonants[c] ?: "$c$NUKTA"
                }
                i += 2
                appendDevanagariConsonant(sb, word, i, trans)
                continue
            }

            if (devanagariVowels.containsKey(c)) {
                sb.append(devanagariVowels[c])
                i++
                continue
            }

            if (devanagariConsonants.containsKey(c)) {
                val base = devanagariConsonants[c]!!
                i++
                appendDevanagariConsonant(sb, word, i, base)
                continue
            }

            if (devanagariMatras.containsKey(c)) {
                sb.append(devanagariMatras[c])
                i++
                continue
            }

            when (c) {
                ANUSVARA, CHANDRABINDU -> {
                    sb.append("n")
                    i++
                }
                VISARGA -> {
                    sb.append("h")
                    i++
                }
                VIRAMA -> {
                    i++
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }

        return sb.toString()
    }

    private fun appendDevanagariConsonant(sb: StringBuilder, word: String, nextIdx: Int, consonantTrans: String) {
        sb.append(consonantTrans)
        val len = word.length

        if (nextIdx >= len) {
            // Modern Indic schwa deletion at word-final position
            return
        }

        val nextChar = word[nextIdx]
        if (nextChar == VIRAMA) {
            return
        }

        if (devanagariMatras.containsKey(nextChar)) {
            return
        }

        if (nextChar == ANUSVARA || nextChar == CHANDRABINDU || nextChar == VISARGA) {
            sb.append("a")
            return
        }

        if (!isDevanagari(nextChar.toString())) {
            return
        }

        sb.append("a")
    }
}
