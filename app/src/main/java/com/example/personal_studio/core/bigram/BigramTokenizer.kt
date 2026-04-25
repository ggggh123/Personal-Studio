package com.example.personal_studio.core.bigram

/**
 * Pre-tokenizes text for the FTS4 shadow table without needing a CJK tokenizer.
 *
 * Strategy:
 *  - Split by whitespace + punctuation into chunks.
 *  - ASCII chunks (`code < 128`) are lowercased and kept whole.
 *  - CJK chunks are split into sliding bigrams (length 1 → kept as the single char).
 *
 * The query side joins per-chunk bigrams with AND so a 4-char term like 微积分极 must
 * have all three bigrams 微积, 积分, 分极 present in the document. Cross-chunk noise
 * is mitigated by encouraging users to add spaces between conceptual terms; the
 * ViewModel layer additionally falls back to OR when the AND query returns < 5 hits.
 */
object BigramTokenizer {

    private val SEP = Regex("[\\s\\p{Punct}\\p{IsPunctuation}]+")

    /** Index time. Returns space-separated tokens to insert into FTS. */
    fun tokenizeForIndex(text: String): String {
        if (text.isBlank()) return ""
        val out = StringBuilder()
        for (chunk in text.split(SEP)) {
            if (chunk.isEmpty()) continue
            if (chunk.all { it.code < 128 }) {
                if (out.isNotEmpty()) out.append(' ')
                out.append(chunk.lowercase())
            } else {
                appendBigrams(chunk, out)
            }
        }
        return out.toString()
    }

    /** Query time. Returns an FTS MATCH expression with AND semantics. */
    fun tokenizeForQuery(input: String): String {
        if (input.isBlank()) return ""
        val parts = mutableListOf<String>()
        for (chunk in input.split(SEP)) {
            if (chunk.isEmpty()) continue
            if (chunk.all { it.code < 128 }) {
                parts += "\"${chunk.lowercase()}\""
            } else {
                val sb = StringBuilder()
                appendBigrams(chunk, sb)
                val bigrams = sb.toString().split(' ').filter { it.isNotBlank() }
                if (bigrams.isNotEmpty()) {
                    parts += bigrams.joinToString(" AND ") { "\"$it\"" }
                }
            }
        }
        return parts.joinToString(" AND ")
    }

    private fun appendBigrams(word: String, out: StringBuilder) {
        if (word.length == 1) {
            if (out.isNotEmpty()) out.append(' ')
            out.append(word)
            return
        }
        for (i in 0 until word.length - 1) {
            if (out.isNotEmpty()) out.append(' ')
            out.append(word, i, i + 2)
        }
    }
}
