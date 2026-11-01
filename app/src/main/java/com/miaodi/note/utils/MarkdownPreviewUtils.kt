package com.miaodi.note.utils

/**
 * 轻量 Markdown → HTML 渲染器（无第三方依赖）。
 * 支持：标题、粗体、斜体、行内代码、代码块、有序/无序列表、引用、水平线、链接、图片、表格、换行。
 * 同时支持文档内嵌原生 HTML：块级标签（div/table/details/center/style 等）整段原样输出，
 * 行内标签（span/b/font/sup/kbd 等）与 HTML 实体（&nbsp; 等）不被转义，可与 Markdown 语法混用。
 * 样式主题：默认 / 简约 / 夜间。
 */
object MarkdownPreviewUtils {

    fun render(markdown: String, style: String): String {
        val body = convert(markdown.trim())
        val css = when (style) {
            "简约" -> MINIMAL_CSS
            "夜间" -> DARK_CSS
            else -> DEFAULT_CSS
        }
        return """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>$css</style>
</head>
<body><article>$body</article></body>
</html>"""
    }

    private fun convert(md: String): String {
        val lines = md.split("\n")
        val sb = StringBuilder()
        var i = 0
        var inCodeBlock = false
        val codeBuf = StringBuilder()
        var codeLang: String? = null
        var inUl = false
        var inOl = false

        fun closeLists() {
            if (inUl) {
                sb.append("</ul>\n")
                inUl = false
            }
            if (inOl) {
                sb.append("</ol>\n")
                inOl = false
            }
        }

        while (i < lines.size) {
            val raw = lines[i]
            val line = raw.trimEnd()

            if (line.startsWith("```")) {
                if (!inCodeBlock) {
                    inCodeBlock = true
                    codeBuf.setLength(0)
                    // 围栏后的语言标记：```cpp / ```python
                    codeLang = line.removePrefix("```").trim().substringBefore(' ')
                        .ifBlank { null }
                } else {
                    inCodeBlock = false
                    sb.append(renderCodeBlock(codeBuf.toString(), codeLang))
                    codeLang = null
                }
                i++
                continue
            }
            if (inCodeBlock) {
                codeBuf.append(line).append("\n")
                i++
                continue
            }

            // 块级 HTML：整段原样输出，不再做 Markdown 解析与转义。
            val blockTag = blockHtmlTagOf(line)
            if (blockTag != null) {
                closeLists()
                val end = i + collectHtmlBlockEnd(lines, i, blockTag)
                for (k in i until end) sb.append(lines[k]).append("\n")
                i = end
                continue
            }

            val next = lines.getOrNull(i + 1)?.trimEnd()

            // 表格：当前行含 | 且下一行为分隔行（如 |---|:--:|）
            if (line.contains("|") && next != null && isTableSeparator(next)) {
                closeLists()
                val header = splitTableRow(line)
                sb.append("<table>\n<thead>\n<tr>")
                for (cell in header) sb.append("<th>").append(inline(cell)).append("</th>")
                sb.append("</tr>\n</thead>\n<tbody>\n")
                var j = i + 2
                while (j < lines.size) {
                    val row = lines[j].trimEnd()
                    if (row.isBlank() || !row.contains("|")) break
                    sb.append("<tr>")
                    for (cell in splitTableRow(row)) sb.append("<td>").append(inline(cell)).append("</td>")
                    sb.append("</tr>\n")
                    j++
                }
                sb.append("</tbody>\n</table>\n")
                i = j
                continue
            }

            when {
                line.isBlank() -> {
                    closeLists()
                    sb.append("\n")
                }
                line.startsWith("### ") -> {
                    closeLists()
                    sb.append("<h3>").append(inline(line.removePrefix("### "))).append("</h3>\n")
                }
                line.startsWith("## ") -> {
                    closeLists()
                    sb.append("<h2>").append(inline(line.removePrefix("## "))).append("</h2>\n")
                }
                line.startsWith("# ") -> {
                    closeLists()
                    sb.append("<h1>").append(inline(line.removePrefix("# "))).append("</h1>\n")
                }
                line.startsWith("> ") -> {
                    closeLists()
                    sb.append("<blockquote>").append(inline(line.removePrefix("> "))).append("</blockquote>\n")
                }
                line.startsWith("---") || line.startsWith("***") || line.startsWith("___") -> {
                    closeLists()
                    sb.append("<hr/>\n")
                }
                line.matches(Regex("""^\s*[-*+] .+""")) -> {
                    if (inOl) {
                        sb.append("</ol>\n")
                        inOl = false
                    }
                    if (!inUl) {
                        sb.append("<ul>\n")
                        inUl = true
                    }
                    sb.append("<li>").append(inline(line.replace(Regex("""^\s*[-*+]\s+"""), ""))).append("</li>\n")
                    val n = lines.getOrNull(i + 1)?.trimEnd()
                    val nextIsListItem = n != null && n.matches(Regex("""^\s*[-*+] .+"""))
                    if (!nextIsListItem) {
                        sb.append("</ul>\n")
                        inUl = false
                    }
                }
                line.matches(Regex("""^\s*\d+[.)]\s+.+""")) -> {
                    if (inUl) {
                        sb.append("</ul>\n")
                        inUl = false
                    }
                    if (!inOl) {
                        sb.append("<ol>\n")
                        inOl = true
                    }
                    sb.append("<li>").append(inline(line.replace(Regex("""^\s*\d+[.)]\s+"""), ""))).append("</li>\n")
                    val n = lines.getOrNull(i + 1)?.trimEnd()
                    val nextIsListItem = n != null && n.matches(Regex("""^\s*\d+[.)]\s+.+"""))
                    if (!nextIsListItem) {
                        sb.append("</ol>\n")
                        inOl = false
                    }
                }
                else -> {
                    closeLists()
                    sb.append("<p>").append(inline(line)).append("</p>\n")
                }
            }
            i++
        }
        closeLists()
        if (inCodeBlock) sb.append(renderCodeBlock(codeBuf.toString(), codeLang))
        return sb.toString()
    }

    /** 判断是否为表格分隔行，例如 | --- | :--: | */
    private fun isTableSeparator(line: String): Boolean {
        val t = line.trim()
        if (!t.contains("-") || !t.contains("|")) return false
        val cells = t.trim('|').split("|")
        if (cells.isEmpty()) return false
        return cells.all { cell ->
            val c = cell.trim()
            c.isNotEmpty() && c.all { it == '-' || it == ':' }
        }
    }

    /** 拆分为表格单元格，去掉首尾竖线 */
    private fun splitTableRow(line: String): List<String> {
        var t = line.trim()
        if (t.startsWith("|")) t = t.substring(1)
        if (t.endsWith("|")) t = t.substring(0, t.length - 1)
        return t.split("|").map { it.trim() }
    }

    /**
     * 渲染代码块：按围栏标注的语言做语法高亮，未标注或语言未知时保持纯文本。
     */
    private fun renderCodeBlock(code: String, lang: String?): String {
        val trimmed = code.trimEnd()
        val body = CodeHighlighter.highlight(trimmed, lang)
        val langClass = lang?.trim()?.lowercase().orEmpty()
        val cls = if (langClass.isEmpty()) "codeblock" else "codeblock lang-$langClass"
        return "<pre class=\"$cls\"><code>$body</code></pre>\n"
    }

    private fun inline(text: String): String {
        // 先摘出行内 HTML 标签与 HTML 实体（占位符保存），避免被下面的转义逻辑破坏，
        // 其余文本仍按 Markdown 规则解析，因此「HTML 标签 + Markdown 语法」可混用。
        val holders = ArrayList<String>()
        var s = HTML_TAG_REGEX.replace(text) { m ->
            holders.add(m.value)
            "\u0000" + (holders.size - 1) + "\u0000"
        }
        s = HTML_ENTITY_REGEX.replace(s) { m ->
            holders.add(m.value)
            "\u0000" + (holders.size - 1) + "\u0000"
        }
        s = escape(s)
        // 行内代码 `code`
        s = s.replace(Regex("`([^`]+)`")) { match ->
            "<code class=\"inline\">${match.groupValues[1]}</code>"
        }
        // 粗体 **text**
        s = s.replace(Regex("""\*\*([^*]+)\*\*""")) { match ->
            "<strong>${match.groupValues[1]}</strong>"
        }
        // 斜体 *text*
        s = s.replace(Regex("""\*([^*]+)\*""")) { match ->
            "<em>${match.groupValues[1]}</em>"
        }
        // 图片 ![alt](url)
        s = s.replace(Regex("""!\[([^\]]*)\]\(([^)\s]+)\)""")) { match ->
            "<img src=\"${match.groupValues[2]}\" alt=\"${match.groupValues[1]}\"/>"
        }
        // 链接 [text](url)
        s = s.replace(Regex("""\[([^\]]+)\]\(([^)\s]+)\)""")) { match ->
            "<a href=\"${match.groupValues[2]}\">${match.groupValues[1]}</a>"
        }
        // 还原被保护的行内 HTML 标签 / 实体
        if (holders.isNotEmpty()) {
            s = HTML_PLACEHOLDER_REGEX.replace(s) { m ->
                val idx = m.groupValues[1].toIntOrNull() ?: -1
                if (idx in holders.indices) holders[idx] else ""
            }
        }
        return s
    }

    /** 若该行以块级 HTML 的起止标签开头，返回小写标签名；否则返回 null。 */
    private fun blockHtmlTagOf(line: String): String? {
        val m = BLOCK_HTML_TAG_REGEX.find(line) ?: return null
        val tag = m.groupValues[1].lowercase()
        return if (tag in BLOCK_HTML_TAGS) tag else null
    }

    /** 该行是否只由一个 HTML 闭合标签构成（如 </div>）。 */
    private fun isClosingHtmlLine(line: String): Boolean =
        line.trimStart().startsWith("</")

    /** 判断文本中是否包含指定标签的闭合形式（如 </div）。 */
    private fun containsClosingTag(text: String, tag: String): Boolean =
        text.contains("</$tag", ignoreCase = true)

    /**
     * 计算一段块级 HTML 的结束偏移量（相对 start，至少为 1）。
     * 参照 CommonMark 的块级规则：
     * - 单独一行即可闭合（自闭合、空元素、同行含闭合标签、本身就是闭合标签）；
     * - pre/style/svg 等原样内容标签需读到闭合标签，允许内部含空行；
     * - 其余块级标签遇到空行或独立闭合标签即结束，使块内的 Markdown 语法仍可被解析。
     */
    private fun collectHtmlBlockEnd(lines: List<String>, start: Int, tag: String): Int {
        val first = lines[start].trimEnd()
        if (isClosingHtmlLine(first)) return 1
        if (first.endsWith("/>") || tag in VOID_HTML_TAGS) return 1
        if (containsClosingTag(first, tag)) return 1
        var k = start + 1
        while (k < lines.size) {
            val cur = lines[k]
            if (containsClosingTag(cur, tag)) return k - start + 1
            if (tag !in RAW_HTML_TAGS) {
                // 普通块级标签：空行或独立闭合标签即视为本块结束
                if (cur.isBlank() || isClosingHtmlLine(cur)) return k - start
            }
            k++
        }
        return lines.size - start
    }

    private fun escape(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    /** 支持原样透传的块级 HTML 标签。 */
    private val BLOCK_HTML_TAGS = setOf(
        "address", "article", "aside", "audio", "blockquote", "canvas", "center", "details",
        "dialog", "div", "dl", "fieldset", "figcaption", "figure", "footer", "form", "h1",
        "h2", "h3", "h4", "h5", "h6", "header", "hr", "iframe", "main", "nav", "ol", "p",
        "pre", "section", "style", "summary", "svg", "table", "tbody", "td", "tfoot", "th",
        "thead", "tr", "ul", "video"
    )

    /** 空元素（无闭合标签）。 */
    private val VOID_HTML_TAGS = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta",
        "param", "source", "track", "wbr"
    )

    /** 内容需原样保留、可跨空行并必须以闭合标签结束的标签。 */
    private val RAW_HTML_TAGS = setOf(
        "pre", "style", "svg", "video", "audio", "canvas", "textarea"
    )

    /** 匹配行首的 HTML 起止标签，如 <div class="x"> 、<center> 、</div> */
    private val BLOCK_HTML_TAG_REGEX = Regex("""^\s*</?([a-zA-Z][a-zA-Z0-9]*)\b""")

    /** 行内 HTML 标签：<b> </span> <br/> <font color="red"> 等。 */
    private val HTML_TAG_REGEX = Regex("""</?[a-zA-Z][a-zA-Z0-9]*\b[^<>]*?/?>""")

    /** HTML 实体：&nbsp; &#160; &#xA0; */
    private val HTML_ENTITY_REGEX = Regex("""&(?:[a-zA-Z][a-zA-Z0-9]{1,31}|#\d{1,7}|#[xX][0-9a-fA-F]{1,6});""")

    /** 行内 HTML 占位符，如 \u0000 3 \u0000 */
    private val HTML_PLACEHOLDER_REGEX = Regex("""\u0000(\d+)\u0000""")

    private const val DEFAULT_CSS = """
        body { margin: 0; padding: 16px; background: #FFFFFF; color: #212121;
               font-size: 16px; line-height: 1.7; font-family: -apple-system, "PingFang SC", "Noto Sans CJK SC", sans-serif; }
        h1, h2, h3 { color: #3F51B5; margin: 16px 0 8px; }
        h1 { font-size: 22px; } h2 { font-size: 19px; } h3 { font-size: 16px; }
        p { margin: 8px 0; }
        ul, ol { margin: 8px 0; padding-left: 24px; }
        li { margin: 4px 0; }
        blockquote { margin: 8px 0; padding: 6px 14px; border-left: 4px solid #3F51B5;
                     background: #F5F6FA; color: #555; }
        hr { border: none; border-top: 1px solid #E0E0E0; margin: 16px 0; }
        code.inline { background: #F0F2F5; padding: 2px 6px; border-radius: 4px;
                      font-size: 14px; color: #C0392B; }
        pre.codeblock { background: #F5F6FA; padding: 12px; border-radius: 8px;
                        overflow-x: auto; margin: 8px 0; }
        pre.codeblock code { font-family: "SFMono-Regular", Consolas, monospace; font-size: 14px; color: #333; }
        .tok-kw { color: #C678DD; font-weight: 600; }
        .tok-type { color: #E5A23B; }
        .tok-str { color: #50A14F; }
        .tok-num { color: #986801; }
        .tok-com { color: #A0A1A7; font-style: italic; }
        .tok-fn { color: #4078F2; }
        .tok-var { color: #E45649; }
        .tok-meta { color: #C18401; }
        .tok-tag { color: #E45649; }
        .tok-attr { color: #986801; }
        table { border-collapse: collapse; width: 100%; margin: 12px 0; }
        th, td { border: 1px solid #E0E0E0; padding: 6px 10px; text-align: left; }
        th { background: #F5F6FA; font-weight: 600; }
        a { color: #3F51B5; }
        img { max-width: 100%; border-radius: 6px; }
    """

    private const val MINIMAL_CSS = """
        body { margin: 0; padding: 16px; background: #FAFAF7; color: #333;
               font-size: 16px; line-height: 1.8; font-family: serif; }
        h1, h2, h3 { color: #111; font-weight: 600; margin: 16px 0 8px; }
        p { margin: 8px 0; } li { margin: 4px 0; }
        ul, ol { padding-left: 24px; }
        blockquote { margin: 8px 0; padding: 4px 14px; border-left: 3px solid #999;
                     color: #666; background: none; }
        hr { border: none; border-top: 1px solid #DDD; margin: 16px 0; }
        code.inline { background: #EFEFEA; padding: 1px 5px; border-radius: 3px; font-size: 14px; }
        pre.codeblock { background: #EFEFEA; padding: 12px; border-radius: 4px; overflow-x: auto; }
        pre.codeblock code { font-family: "SFMono-Regular", Consolas, monospace; font-size: 14px; }
        .tok-kw { color: #7B4EA3; font-weight: 600; }
        .tok-type { color: #9A6A00; }
        .tok-str { color: #3F7A3F; }
        .tok-num { color: #8A5A00; }
        .tok-com { color: #8C8C8C; font-style: italic; }
        .tok-fn { color: #2A5DA8; }
        .tok-var { color: #A33B32; }
        .tok-meta { color: #8A6A00; }
        .tok-tag { color: #A33B32; }
        .tok-attr { color: #8A5A00; }
        table { border-collapse: collapse; width: 100%; margin: 12px 0; }
        th, td { border: 1px solid #DDD; padding: 6px 10px; text-align: left; }
        th { background: #EFEFEA; font-weight: 600; }
        a { color: #333; text-decoration: underline; }
        img { max-width: 100%; }
    """

    private const val DARK_CSS = """
        body { margin: 0; padding: 16px; background: #1E1E1E; color: #DDDDDD;
               font-size: 16px; line-height: 1.7; font-family: -apple-system, "PingFang SC", "Noto Sans CJK SC", sans-serif; }
        h1, h2, h3 { color: #82B1FF; margin: 16px 0 8px; }
        p { margin: 8px 0; } li { margin: 4px 0; }
        ul, ol { padding-left: 24px; }
        blockquote { margin: 8px 0; padding: 6px 14px; border-left: 4px solid #82B1FF;
                     background: #2A2A2A; color: #AAAAAA; }
        hr { border: none; border-top: 1px solid #444; margin: 16px 0; }
        code.inline { background: #2D2D2D; padding: 2px 6px; border-radius: 4px;
                      font-size: 14px; color: #F78C6C; }
        pre.codeblock { background: #2D2D2D; padding: 12px; border-radius: 8px;
                        overflow-x: auto; margin: 8px 0; }
        pre.codeblock code { color: #E8E8E8; font-size: 14px; }
        .tok-kw { color: #C792EA; font-weight: 600; }
        .tok-type { color: #FFCB6B; }
        .tok-str { color: #C3E88D; }
        .tok-num { color: #F78C6C; }
        .tok-com { color: #7A7F87; font-style: italic; }
        .tok-fn { color: #82AAFF; }
        .tok-var { color: #F07178; }
        .tok-meta { color: #FFCB6B; }
        .tok-tag { color: #F07178; }
        .tok-attr { color: #FFCB6B; }
        table { border-collapse: collapse; width: 100%; margin: 12px 0; }
        th, td { border: 1px solid #444; padding: 6px 10px; text-align: left; }
        th { background: #2D2D2D; font-weight: 600; }
        a { color: #82B1FF; }
        img { max-width: 100%; border-radius: 6px; }
    """
}
