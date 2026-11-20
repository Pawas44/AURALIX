package com.videoguard.app.data

/**
 * TransliterationEngine
 * Provides high-accuracy phonetic Romanization (Devanagari to Latin script)
 * for Hindi, Haryanvi, Punjabi, Marathi and other Indic lyrics, matching
 * Spotify / RiMusic / ViMusic Romanized karaoke conventions.
 */
object TransliterationEngine {

    private val devanagariRegex = Regex("[\\u0900-\\u097F]")

    fun isDevanagari(text: String): Boolean {
        return devanagariRegex.containsMatchIn(text)
    }

    // Independent Vowels
    private val vowels = mapOf(
        'अ' to "a", 'आ' to "ā", 'इ' to "i", 'ई' to "ī",
        'उ' to "u", 'ऊ' to "ū", 'ऋ' to "ṛ", 'ॠ' to "ṝ",
        'ऌ' to "ḷ", 'ॡ' to "ḹ", 'ए' to "e", 'ऐ' to "ai",
        'ओ' to "o", 'औ' to "au"
    )

    // Dependent Matras
    private val matras = mapOf(
        'ा' to "ā", 'ि' to "i", 'ी' to "ī", 'ु' to "u",
        'ू' to "ū", 'ृ' to "ṛ", 'ॄ' to "ṝ", 'ॢ' to "ḷ",
        'ॣ' to "ḹ", 'े' to "e", 'ै' to "ai", 'ो' to "o",
        'ौ' to "au"
    )

    // Consonants (with base consonant transliteration, default inherent 'a' added when applicable)
    private val consonants = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "ng",
        'च' to "c", 'छ' to "ch", 'ज' to "j", 'झ' to "jh", 'ञ' to "ny",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "sh",
        'ष' to "sh", 'स' to "s", 'ह' to "h",
        // Single-char Devanagari extensions
        'क़' to "q", 'ख़' to "kh", 'ग़' to "gh", 'ज़' to "z",
        'ड़' to "r", 'ढ़' to "rh", 'फ़' to "f", 'य़' to "y"
    )

    // Halant / Virama suppresses inherent 'a'
    private const val VIRAMA = '्'
    private const val ANUSVARA = 'ं'
    private const val CHANDRABINDU = 'ँ'
    private const val VISARGA = 'ः'
    private const val NUKTA = '़'

    /**
     * Romanizes a line of text if it contains Devanagari script.
     * Preserves punctuation, whitespace, and existing Latin text.
     */
    fun romanize(text: String): String {
        if (!isDevanagari(text)) return text

        val sb = StringBuilder()
        val words = text.split(Regex("(?<=\\s)|(?=\\s)"))

        for (word in words) {
            sb.append(romanizeWord(word))
        }

        return sb.toString().trimEnd()
    }

    private fun romanizeWord(word: String): String {
        if (!isDevanagari(word)) return word

        val sb = StringBuilder()
        val len = word.length
        var i = 0

        while (i < len) {
            val c = word[i]

            // Check two-char composite with Nukta (e.g. क + ़ = क़)
            if (i + 1 < len && word[i + 1] == NUKTA) {
                val composite = "$c$NUKTA"
                val mapped = consonants[c] ?: composite
                val trans = when (c) {
                    'क' -> "q"
                    'ख' -> "kh"
                    'ग' -> "gh"
                    'ज' -> "z"
                    'ड' -> "r"
                    'ढ' -> "rh"
                    'फ' -> "f"
                    else -> mapped
                }
                i += 2
                appendConsonantVowel(sb, word, i, trans)
                continue
            }

            if (vowels.containsKey(c)) {
                sb.append(vowels[c])
                i++
                continue
            }

            if (consonants.containsKey(c)) {
                val base = consonants[c]!!
                i++
                appendConsonantVowel(sb, word, i, base)
                continue
            }

            if (matras.containsKey(c)) {
                sb.append(matras[c])
                i++
                continue
            }

            when (c) {
                ANUSVARA -> {
                    sb.append("n")
                    i++
                }
                CHANDRABINDU -> {
                    sb.append("n")
                    i++
                }
                VISARGA -> {
                    sb.append("h")
                    i++
                }
                VIRAMA -> {
                    // Handled inside appendConsonantVowel
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

    private fun appendConsonantVowel(sb: StringBuilder, word: String, nextIdx: Int, consonantTrans: String) {
        sb.append(consonantTrans)
        val len = word.length

        if (nextIdx >= len) {
            // Modern Indic schwa deletion at word-final position (e.g. याद -> yād, not yāda)
            return
        }

        val nextChar = word[nextIdx]
        if (nextChar == VIRAMA) {
            // Virama: inherent 'a' is suppressed
            return
        }

        if (matras.containsKey(nextChar)) {
            // Matra will be handled in the next loop iteration
            return
        }

        if (nextChar == ANUSVARA || nextChar == CHANDRABINDU || nextChar == VISARGA) {
            // Inherent 'a' is kept before nasalization if no other matra
            sb.append("a")
            return
        }

        if (!isDevanagari(nextChar.toString())) {
            // End of Indic word (punctuation, space, etc.) -> Schwa deleted
            return
        }

        // Medial consonant followed by another consonant -> Inherent 'a'
        sb.append("a")
    }
}
