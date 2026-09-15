package com.miaodi.note.utils

/**
 * 轻量 Markdown → HTML 渲染器（无第三方依赖）。
 * 支持：标题、粗体、斜体、行内代码、代码块、有序/无序列表、引用、水平线、链接、图片、表格、换行。
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
                } else {
                    inCodeBlock = false
                    sb.append(renderCodeBlock(codeBuf.toString()))
                }
                i++
                continue
            }
            if (inCodeBlock) {
                codeBuf.append(line).append("\n")
                i++
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
        if (inCodeBlock) sb.append(renderCodeBlock(codeBuf.toString()))
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

    private fun renderCodeBlock(code: String): String {
        val escaped = escape(code.trimEnd())
        return "<pre class=\"codeblock\"><code>$escaped</code></pre>\n"
    }

    private fun inline(text: String): String {
        var s = escape(text)
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
        return s
    }

    private fun escape(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

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
        table { border-collapse: collapse; width: 100%; margin: 12px 0; }
        th, td { border: 1px solid #444; padding: 6px 10px; text-align: left; }
        th { background: #2D2D2D; font-weight: 600; }
        a { color: #82B1FF; }
        img { max-width: 100%; border-radius: 6px; }
    """
}
