package com.example.personal_studio.ui.components

/**
 * Markdown → HTML for model answers and knowledge-base entries, shown by
 * `assets/katex/render.html` (which styles every element produced here).
 *
 * Blocks: ATX headings, paragraphs (a single newline stays a line break), unordered and
 * ordered lists (nesting by indentation, a list may interrupt a paragraph as models write
 * them), block quotes (parsed recursively), fenced code, GFM pipe tables with alignment and
 * thematic breaks. Inline: code, bold, italic, strikethrough, links (shown as text, the
 * WebView must not navigate) and literal `<br>` (models put it in table cells). Everything
 * else is escaped.
 *
 * Fenced code, inline code and math (`$$…$$`, `$…$`, `\[…\]`, `\(…\)`) are lifted into
 * placeholders before any Markdown pass and put back verbatim at the end:
 * - Markdown never touches LaTeX (`*`, `_`, `|` inside formulas stay as written).
 * - Block math keeps its delimiters in one text node. KaTeX's auto-render only matches
 *   within a single text node, so a `<br>` inside `$$…$$` would leave the formula as raw text.
 * - `$` inside code is not mistaken for math.
 */
internal fun toHtml(md: String): String {
    val fenced = mutableListOf<String>()
    val inlineCode = mutableListOf<String>()
    val math = mutableListOf<String>()

    var text = md.replace("\r\n", "\n")
    text = FENCE.replace(text) { m ->
        fenced += "<pre><code>${escape(m.groupValues[2])}</code></pre>"
        "\n${placeholder('C', fenced.lastIndex)}\n"
    }
    text = INLINE_CODE.replace(text) { m ->
        inlineCode += "<code>${escape(m.groupValues[1])}</code>"
        placeholder('K', inlineCode.lastIndex)
    }
    text = MATH.replace(text) { m ->
        math += escape(m.value)
        placeholder('M', math.lastIndex)
    }

    val lines = text.split('\n').map(::expandLeadingTabs)
    var html = renderBlocks(lines)

    math.forEachIndexed { i, s -> html = html.replace(placeholder('M', i), s) }
    inlineCode.forEachIndexed { i, s -> html = html.replace(placeholder('K', i), s) }
    fenced.forEachIndexed { i, s -> html = html.replace(placeholder('C', i), s) }
    return html
}

private fun placeholder(kind: Char, index: Int) = "\u0000$kind$index\u0000"

private val FENCE = Regex("""(?m)^[ \t]*(`{3,}|~{3,})[^\n]*\n(?:([\s\S]*?)\n)?[ \t]*\1[ \t]*$""")
private val INLINE_CODE = Regex("""`([^`\n]+)`""")
private val MATH = Regex("""\$\$[\s\S]*?\$\$|\\\[[\s\S]*?\\\]|\$[^$\n]+?\$|\\\([\s\S]*?\\\)""")
private val CODE_BLOCK_LINE = Regex("""^\u0000C\d+\u0000$""")
private val HEADING = Regex("""^(#{1,6})[ \t]+(.+?)(?:[ \t]+#+)?[ \t]*$""")
private val RULE = Regex("""^ {0,3}([-*_])(?:[ \t]*\1){2,}[ \t]*$""")
private val LIST_ITEM = Regex("""^( *)([-*+]|\d{1,9}[.)])[ \t]+(.*)$""")
private val TABLE_RULE = Regex("""^\|?[ \t]*:?-+:?[ \t]*(?:\|[ \t]*:?-+:?[ \t]*)*\|?$""")
private val LINE_BREAK_TAG = Regex("""&lt;br\s*/?&gt;""", RegexOption.IGNORE_CASE)

private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private fun expandLeadingTabs(line: String): String {
    val lead = line.takeWhile { it == ' ' || it == '\t' }
    return if ('\t' !in lead) line else lead.replace("\t", "    ") + line.substring(lead.length)
}

private fun indentOf(line: String) = line.takeWhile { it == ' ' }.length

private fun startsBlock(trimmed: String) =
    trimmed.startsWith(">") || CODE_BLOCK_LINE.matches(trimmed) || HEADING.matches(trimmed) || RULE.matches(trimmed)

private fun isTableStart(lines: List<String>, i: Int) =
    '|' in lines[i] && i + 1 < lines.size && '|' in lines[i + 1] && TABLE_RULE.matches(lines[i + 1].trim())

private fun nextNonBlank(lines: List<String>, from: Int): Int? = (from until lines.size).firstOrNull { lines[it].isNotBlank() }

private fun renderBlocks(lines: List<String>): String {
    val out = StringBuilder()
    val paragraph = mutableListOf<String>()
    fun flush() {
        if (paragraph.isEmpty()) return
        out.append("<p>").append(paragraph.joinToString("<br>") { inline(it.trim()) }).append("</p>")
        paragraph.clear()
    }
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> { flush(); i++ }
            CODE_BLOCK_LINE.matches(trimmed) -> { flush(); out.append(trimmed); i++ }
            HEADING.matches(trimmed) -> {
                flush()
                val m = HEADING.matchEntire(trimmed)!!
                val level = minOf(m.groupValues[1].length, 4)
                out.append("<h$level>").append(inline(m.groupValues[2])).append("</h$level>")
                i++
            }
            RULE.matches(line) -> { flush(); out.append("<hr>"); i++ }
            trimmed.startsWith(">") -> {
                flush()
                val quoted = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quoted += lines[i].trim().removePrefix(">").removePrefix(" ")
                    i++
                }
                out.append("<blockquote>").append(renderBlocks(quoted)).append("</blockquote>")
            }
            isTableStart(lines, i) -> { flush(); i = renderTable(lines, i, out) }
            LIST_ITEM.matches(line) -> { flush(); i = renderList(lines, i, out) }
            else -> { paragraph += line; i++ }
        }
    }
    flush()
    return out.toString()
}

private class Marker(val indent: Int, val ordered: Boolean, val number: Int, val text: String)

private fun markerOf(line: String): Marker? {
    val m = LIST_ITEM.matchEntire(line) ?: return null
    val token = m.groupValues[2]
    val ordered = token[0].isDigit()
    return Marker(m.groupValues[1].length, ordered, if (ordered) token.dropLast(1).toIntOrNull() ?: 1 else 1, m.groupValues[3])
}

/**
 * One list starting at [start]. Items at the first item's indent (±1 space of slack) are
 * siblings; lines indented two or more spaces deeper belong to the current item (continuation
 * text or a nested list); unindented plain text right after an item continues it.
 */
private fun renderList(lines: List<String>, start: Int, out: StringBuilder): Int {
    val first = markerOf(lines[start])!!
    val base = first.indent
    fun isSibling(m: Marker?) = m != null && m.ordered == first.ordered && m.indent in base..base + 1
    out.append(
        when {
            !first.ordered -> "<ul>"
            first.number != 1 -> "<ol start=\"${first.number}\">"
            else -> "<ol>"
        },
    )
    var i = start
    while (i < lines.size) {
        val item = markerOf(lines[i])
        if (!isSibling(item)) break
        val body = mutableListOf(item!!.text)
        i++
        while (i < lines.size) {
            val l = lines[i]
            if (l.isBlank()) {
                val next = nextNonBlank(lines, i)
                if (next != null && indentOf(lines[next]) >= base + 2) { body += ""; i++; continue }
                break
            }
            val m = markerOf(l)
            if (m != null && m.indent <= base + 1) break
            if (indentOf(l) >= base + 2) { body += l; i++; continue }
            if (m == null && !startsBlock(l.trim()) && !isTableStart(lines, i)) { body += l.trim(); i++; continue }
            break
        }
        out.append("<li>").append(renderItem(body)).append("</li>")
        // Blank lines between sibling items keep the same list going.
        val next = nextNonBlank(lines, i)
        if (next != null && next != i && isSibling(markerOf(lines[next]))) i = next
    }
    out.append(if (first.ordered) "</ol>" else "</ul>")
    return i
}

/** Leading text lines render inline (a tight item); anything after them becomes nested blocks. */
private fun renderItem(body: List<String>): String {
    val split = (1 until body.size).firstOrNull { k ->
        val l = body[k]
        l.isBlank() || markerOf(l) != null || startsBlock(l.trim())
    } ?: body.size
    val lead = body.subList(0, split).joinToString("<br>") { inline(it.trim()) }
    val rest = body.subList(split, body.size)
    if (rest.all { it.isBlank() }) return lead
    val cut = rest.filter { it.isNotBlank() }.minOf(::indentOf)
    return lead + renderBlocks(rest.map { if (it.isBlank()) it else it.drop(cut) })
}

private fun renderTable(lines: List<String>, start: Int, out: StringBuilder): Int {
    val header = cells(lines[start])
    val aligns = cells(lines[start + 1]).map { c ->
        when {
            c.startsWith(":") && c.endsWith(":") -> "center"
            c.endsWith(":") -> "right"
            else -> null
        }
    }
    fun cell(tag: String, content: String, k: Int) =
        "<$tag${aligns.getOrNull(k)?.let { " style=\"text-align:$it\"" } ?: ""}>${inline(content)}</$tag>"

    out.append("<div class=\"table\"><table><thead><tr>")
    header.forEachIndexed { k, h -> out.append(cell("th", h, k)) }
    out.append("</tr></thead><tbody>")
    var i = start + 2
    while (i < lines.size && lines[i].isNotBlank() && '|' in lines[i]) {
        val row = cells(lines[i])
        out.append("<tr>")
        header.indices.forEach { k -> out.append(cell("td", row.getOrElse(k) { "" }, k)) }
        out.append("</tr>")
        i++
    }
    out.append("</tbody></table></div>")
    return i
}

private fun cells(line: String): List<String> {
    var t = line.trim().replace("\\|", "\u0001")
    if (t.startsWith("|")) t = t.drop(1)
    if (t.endsWith("|")) t = t.dropLast(1)
    return t.split('|').map { it.replace('\u0001', '|').trim() }
}

private val BOLD = Regex("""\*\*(.+?)\*\*""")
private val ITALIC = Regex("""(?<![*\w])\*(?![\s*])(.+?)(?<![\s*])\*(?![*\w])""")
private val STRIKE = Regex("""~~(.+?)~~""")
private val LINK = Regex("""\[([^\]\n]+)]\(([^)\s]+)\)""")

private fun inline(s: String): String {
    var t = escape(s)
    t = LINK.replace(t) { m -> "<a>${m.groupValues[1]}</a>" }
    t = BOLD.replace(t) { m -> "<strong>${m.groupValues[1]}</strong>" }
    t = ITALIC.replace(t) { m -> "<em>${m.groupValues[1]}</em>" }
    t = STRIKE.replace(t) { m -> "<del>${m.groupValues[1]}</del>" }
    return LINE_BREAK_TAG.replace(t, "<br>")
}
