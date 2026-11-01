package com.miaodi.note.utils

/**
 * 轻量级代码语法高亮器。
 *
 * 将源代码切分为关键字 / 类型 / 字符串 / 数字 / 注释 / 函数名等记号，
 * 输出带 class 的 <span> 片段，由预览样式表负责着色。
 *
 * 纯 Kotlin 实现：不引入第三方依赖，也不依赖 WebView 的 JavaScript，
 * 因此可在大文档预览的后台线程中同步执行。
 */
object CodeHighlighter {

    /** 语言别名归一化表：code fence 上写的语言标记 -> 规范化语言名。 */
    private val LANG_ALIASES: Map<String, String> = mapOf(
        "c" to "c", "h" to "c",
        "cpp" to "cpp", "c++" to "cpp", "cc" to "cpp", "cxx" to "cpp", "hpp" to "cpp",
        "hxx" to "cpp", "ino" to "cpp",
        "java" to "java",
        "kt" to "kotlin", "kotlin" to "kotlin", "kts" to "kotlin",
        "js" to "javascript", "javascript" to "javascript", "jsx" to "javascript",
        "mjs" to "javascript", "node" to "javascript",
        "ts" to "typescript", "typescript" to "typescript", "tsx" to "typescript",
        "py" to "python", "python" to "python", "python3" to "python",
        "go" to "go", "golang" to "go",
        "rs" to "rust", "rust" to "rust",
        "cs" to "csharp", "csharp" to "csharp", "c#" to "csharp",
        "php" to "php",
        "swift" to "swift",
        "sh" to "shell", "bash" to "shell", "shell" to "shell", "zsh" to "shell",
        "console" to "shell", "shellscript" to "shell",
        "sql" to "sql", "mysql" to "sql", "postgresql" to "sql", "sqlite" to "sql",
        "json" to "json",
        "xml" to "xml", "html" to "xml", "htm" to "xml", "svg" to "xml", "xhtml" to "xml",
        "css" to "css", "scss" to "css", "less" to "css",
        "yml" to "yaml", "yaml" to "yaml",
        "gradle" to "groovy", "groovy" to "groovy",
        "dart" to "dart"
    )

    /**
     * 把代码渲染成带高亮标记的 HTML 片段。
     * 语言未知或为空时只做安全转义，不添加标记。
     */
    fun highlight(code: String, lang: String?): String {
        if (code.isEmpty()) return ""
        // 未标注语言（``` 后为空）时按内容特征自动识别，尽量还原常见代码块的高亮效果。
        val key = normalize(lang) ?: detectLang(code) ?: return esc(code)
        return when (key) {
            "xml" -> highlightMarkup(code)
            "css" -> highlightCss(code)
            else -> {
                val spec = specOf(key)
                if (spec == null) esc(code) else tokenize(code, spec)
            }
        }
    }

    /** 语言标记归一化；空标记或无法识别时返回 null。 */
    private fun normalize(lang: String?): String? {
        if (lang == null) return null
        val key = lang.trim().lowercase()
        if (key.isEmpty()) return null
        return LANG_ALIASES[key]
    }

    /**
     * 按内容特征猜测代码语言，用于未标注语言的代码块。
     * 判定偏保守：只有明显像代码时才返回，避免把普通段落误着色。
     */
    private fun detectLang(code: String): String? {
        val s = code
        if (s.contains("#include") || s.contains("#define") || s.contains("#pragma")) return "cpp"
        if (s.contains("<html", true) || s.contains("<!DOCTYPE", true) ||
            s.contains("</div", true) || s.contains("</span", true) ||
            s.contains("</table", true)
        ) return "xml"
        // Python：def 定义行以冒号结尾，且不含 C 家族的花括号语句块
        val hasColonLine = s.lineSequence().any { it.trimEnd().endsWith(":") }
        if (s.contains("def ") && hasColonLine) return "python"
        if (s.contains("fun ") && s.contains("val ") && s.contains("{")) return "kotlin"
        if (s.contains("package ") && s.contains("func ")) return "go"
        if (s.contains("select ", true) && s.contains("from ", true)) return "sql"
        if (s.contains("function ") || s.contains("=>")) return "javascript"
        // C / C++ 家族：存在区块且出现类型或控制流特征
        if (s.contains("{") || s.contains(";")) {
            for (kw in CPP_HINT_WORDS) {
                if (s.contains(kw)) return "cpp"
            }
        }
        if (s.contains("{") && s.contains("}") && CSS_PROP_HINT.any { s.contains(it) }) return "css"
        return null
    }

    /** 判定为 C/C++ 的强特征词（含尾随空格，降低误判）。 */
    private val CPP_HINT_WORDS = listOf(
        "void ", "int ", "uint8_t", "uint16_t", "uint32_t", "size_t", "char ",
        "float ", "double ", "bool ", "struct ", "for (", "while (", "return ",
        "static ", "const ", "std::"
    )

    /** 判定为 CSS 的常见属性名。 */
    private val CSS_PROP_HINT = listOf(
        "color:", "margin:", "padding:", "background:", "font-size:", "display:", "border:"
    )

    // ---------------------------------------------------------------- 词法配置

    private class Spec(
        val keywords: Set<String>,
        val types: Set<String>,
        val builtins: Set<String> = emptySet(),
        val lineComment: String? = "//",
        val blockComment: Boolean = true,
        val preprocessor: Boolean = false,
        val tripleQuote: Boolean = false,
        val backtickString: Boolean = false,
        val decorator: Boolean = false,
        val dollarVar: Boolean = false
    )

    private fun words(s: String): Set<String> =
        s.split(' ', '\n').filter { it.isNotBlank() }.toHashSet()

    private val C_KEYWORDS = words(
        "auto break case const continue default do else enum extern for goto if inline " +
            "register restrict return sizeof static struct switch typedef union volatile while " +
            "true false NULL"
    )
    private val C_TYPES = words(
        "void char short int long float double signed unsigned bool _Bool size_t ssize_t " +
            "int8_t uint8_t int16_t uint16_t int32_t uint32_t int64_t uint64_t " +
            "ptrdiff_t wchar_t FILE"
    )
    private val CPP_EXTRA = words(
        "class namespace template typename public private protected virtual override final " +
            "new delete this using try catch throw constexpr consteval constinit nullptr " +
            "operator friend explicit mutable noexcept decltype alignas alignof static_cast " +
            "dynamic_cast reinterpret_cast const_cast concept requires co_await co_return " +
            "co_yield export import module thread_local char8_t char16_t char32_t"
    )
    private val CPP_STL = words(
        "std string vector map set list deque pair tuple unique_ptr shared_ptr weak_ptr " +
            "make_shared make_unique cout cin cerr endl move forward optional variant array"
    )
    private val JAVA_KEYWORDS = words(
        "abstract assert boolean break byte case catch char class const continue default do " +
            "double else enum extends final finally float for goto if implements import " +
            "instanceof int interface long native new package private protected public return " +
            "short static strictfp super switch synchronized this throw throws transient try " +
            "void volatile while var record sealed permits yield true false null"
    )
    private val JAVA_TYPES = words(
        "String Integer Long Double Float Boolean Character Byte Short Object List Map Set " +
            "ArrayList HashMap Void"
    )
    private val KOTLIN_KEYWORDS = words(
        "as break class continue do else false for fun if in interface is null object package " +
            "return super this throw true try typealias typeof val var when while by catch " +
            "constructor delegate dynamic field file finally get import init param property " +
            "receiver set setparam where actual abstract annotation companion const crossinline " +
            "data enum expect external infix inline inner internal lateinit noinline open " +
            "operator out override private protected public reified sealed suspend tailrec " +
            "vararg it"
    )
    private val KOTLIN_TYPES = words(
        "String Int Long Double Float Boolean Char Byte Short Any Unit Nothing List MutableList " +
            "Map MutableMap Set MutableSet Array IntArray"
    )
    private val JS_KEYWORDS = words(
        "break case catch class const continue debugger default delete do else export extends " +
            "finally for function if import in instanceof let new return super switch this " +
            "throw try typeof var void while with yield async await of static get set " +
            "true false null undefined"
    )
    private val JS_TYPES = words(
        "String Number Boolean Object Array Promise Map Set Date JSON Math console window " +
            "document"
    )
    private val TS_EXTRA = words(
        "interface type enum implements namespace declare readonly private public protected " +
            "abstract as any boolean number string symbol never unknown keyof infer satisfies"
    )
    private val PY_KEYWORDS = words(
        "and as assert async await break class continue def del elif else except False finally " +
            "for from global if import in is lambda None nonlocal not or pass raise return " +
            "True try while with yield self match case"
    )
    private val PY_TYPES = words(
        "int float str bool list dict tuple set bytes object len range print open enumerate " +
            "zip isinstance super"
    )
    private val GO_KEYWORDS = words(
        "break case chan const continue default defer else fallthrough for func go goto if " +
            "import interface map package range return select struct switch type var nil " +
            "true false iota"
    )
    private val GO_TYPES = words(
        "int int8 int16 int32 int64 uint uint8 uint16 uint32 uint64 float32 float64 string " +
            "bool byte rune error make new len cap append copy delete panic recover print println"
    )
    private val RUST_KEYWORDS = words(
        "as async await break const continue crate dyn else enum extern false fn for if impl " +
            "in let loop match mod move mut pub ref return self Self static struct super trait " +
            "true type unsafe use where while"
    )
    private val RUST_TYPES = words(
        "i8 i16 i32 i64 i128 isize u8 u16 u32 u64 u128 usize f32 f64 bool char str String " +
            "Vec Option Result Box Some None Ok Err println vec"
    )
    private val CS_KEYWORDS = words(
        "abstract as base bool break byte case catch char checked class const continue decimal " +
            "default delegate do double else enum event explicit extern false finally fixed " +
            "float for foreach goto if implicit in int interface internal is lock long " +
            "namespace new null object operator out override params private protected public " +
            "readonly ref return sbyte sealed short sizeof stackalloc static string struct " +
            "switch this throw true try typeof uint ulong unchecked unsafe ushort using " +
            "virtual void volatile while var async await record"
    )
    private val PHP_KEYWORDS = words(
        "abstract and array as break case catch class clone const continue declare default do " +
            "echo else elseif empty enddeclare endfor endforeach endif endswitch extends final " +
            "finally fn for foreach function global if implements include include_once " +
            "instanceof interface isset list namespace new or print private protected public " +
            "require require_once return static switch throw trait try unset use var while " +
            "true false null"
    )
    private val SWIFT_KEYWORDS = words(
        "associatedtype class deinit enum extension fileprivate func import init inout internal " +
            "let open operator private protocol public static struct subscript typealias var " +
            "break case continue default defer do else fallthrough for guard if in repeat " +
            "return switch where while as catch false is nil rethrows super self Self throw " +
            "throws true try async await override mutating"
    )
    private val SWIFT_TYPES = words(
        "Int Double Float String Bool Array Dictionary Set Optional Any AnyObject Void"
    )
    private val SHELL_KEYWORDS = words(
        "if then else elif fi for while do done case esac function return exit export local " +
            "read echo printf cd ls cp mv rm mkdir cat grep sed awk curl wget git sudo apt " +
            "bash sh source set unset true false"
    )
    private val SQL_KEYWORDS = words(
        "select from where insert into values update set delete create drop alter add table " +
            "index view join left right inner outer full on group by order having limit offset " +
            "union all distinct as and or not null is like in between exists case when then " +
            "else end primary key foreign references default constraint unique if begin commit " +
            "rollback"
    )
    private val SQL_TYPES = words(
        "int integer bigint smallint tinyint varchar char text date datetime timestamp " +
            "decimal numeric float double boolean blob"
    )
    private val JSON_KEYWORDS = words("true false null")
    private val YAML_KEYWORDS = words("true false null yes no on off")

    private fun specOf(key: String): Spec? = when (key) {
        "c" -> Spec(keywords = C_KEYWORDS, types = C_TYPES, preprocessor = true)
        "cpp" -> Spec(
            keywords = C_KEYWORDS + CPP_EXTRA,
            types = C_TYPES + CPP_STL,
            preprocessor = true
        )
        "java" -> Spec(keywords = JAVA_KEYWORDS, types = JAVA_TYPES)
        "kotlin" -> Spec(
            keywords = KOTLIN_KEYWORDS, types = KOTLIN_TYPES, backtickString = true
        )
        "javascript" -> Spec(
            keywords = JS_KEYWORDS, types = JS_TYPES, backtickString = true
        )
        "typescript" -> Spec(
            keywords = JS_KEYWORDS + TS_EXTRA, types = JS_TYPES, backtickString = true
        )
        "python" -> Spec(
            keywords = PY_KEYWORDS, types = PY_TYPES, lineComment = "#",
            blockComment = false, tripleQuote = true, decorator = true
        )
        "go" -> Spec(keywords = GO_KEYWORDS, types = GO_TYPES, backtickString = true)
        "rust" -> Spec(keywords = RUST_KEYWORDS, types = RUST_TYPES)
        "csharp" -> Spec(keywords = CS_KEYWORDS, types = words("int long short byte string bool float double decimal char object var"))
        "php" -> Spec(keywords = PHP_KEYWORDS, types = emptySet(), dollarVar = true)
        "swift" -> Spec(keywords = SWIFT_KEYWORDS, types = SWIFT_TYPES)
        "shell" -> Spec(
            keywords = SHELL_KEYWORDS, types = emptySet(), lineComment = "#",
            blockComment = false, dollarVar = true
        )
        "sql" -> Spec(
            keywords = SQL_KEYWORDS, types = SQL_TYPES, lineComment = "--",
            blockComment = true
        )
        "json" -> Spec(keywords = JSON_KEYWORDS, types = emptySet(), lineComment = null, blockComment = false)
        "yaml" -> Spec(
            keywords = YAML_KEYWORDS, types = emptySet(), lineComment = "#",
            blockComment = false
        )
        "groovy" -> Spec(keywords = JAVA_KEYWORDS, types = JAVA_TYPES)
        "dart" -> Spec(
            keywords = JAVA_KEYWORDS + words("late required extension mixin show hide"),
            types = JAVA_TYPES
        )
        else -> null
    }

    // ---------------------------------------------------------------- 通用词法分析

    private fun tokenize(code: String, spec: Spec): String {
        val n = code.length
        val sb = StringBuilder(n + n / 3)
        var i = 0
        var lineStart = true

        while (i < n) {
            val c = code[i]

            if (c == '\n') {
                sb.append('\n'); i++; lineStart = true; continue
            }
            if (c == ' ' || c == '\t' || c == '\r') {
                sb.append(c); i++; continue
            }

            val atLineStart = lineStart
            lineStart = false

            // 预处理指令：#include 等
            if (spec.preprocessor && atLineStart && c == '#') {
                var j = i
                while (j < n && code[j] != '\n') j++
                sb.append(span("tok-meta", esc(code.substring(i, j))))
                i = j; continue
            }

            // 块注释
            if (spec.blockComment && c == '/' && i + 1 < n && code[i + 1] == '*') {
                val end = code.indexOf("*/", i + 2)
                val stop = if (end >= 0) end + 2 else n
                sb.append(span("tok-com", esc(code.substring(i, stop))))
                i = stop; continue
            }

            // 行注释
            val lc = spec.lineComment
            if (lc != null && code.startsWith(lc, i)) {
                var j = i
                while (j < n && code[j] != '\n') j++
                sb.append(span("tok-com", esc(code.substring(i, j))))
                i = j; continue
            }

            // 三引号字符串（Python 的文档字符串等）
            if (spec.tripleQuote && c == '"' && code.startsWith("\"\"\"", i)) {
                val end = code.indexOf("\"\"\"", i + 3)
                val stop = if (end >= 0) end + 3 else n
                sb.append(span("tok-str", esc(code.substring(i, stop))))
                i = stop; continue
            }

            // 普通字符串 / 字符字面量
            if (isStringDelim(c, spec)) {
                val stop = scanString(code, i, c)
                sb.append(span("tok-str", esc(code.substring(i, stop))))
                i = stop; continue
            }

            // 装饰器 / 注解：@Override
            if (spec.decorator && c == '@') {
                var j = i + 1
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '_' || code[j] == '.')) j++
                sb.append(span("tok-meta", esc(code.substring(i, j))))
                i = j; continue
            }

            // 变量前缀：$name
            if (spec.dollarVar && c == '$') {
                var j = i + 1
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                if (j > i + 1) {
                    sb.append(span("tok-var", esc(code.substring(i, j))))
                    i = j; continue
                }
            }

            // 数字
            if (c.isDigit() || (c == '.' && i + 1 < n && code[i + 1].isDigit())) {
                var j = i
                while (j < n) {
                    val d = code[j]
                    if (d == '+' || d == '-') {
                        // 仅允许出现在指数符号之后，如 1e-5
                        if (j > i && (code[j - 1] == 'e' || code[j - 1] == 'E')) {
                            j++; continue
                        }
                        break
                    }
                    if (d.isLetterOrDigit() || d == '.' || d == '_') {
                        j++; continue
                    }
                    break
                }
                sb.append(span("tok-num", esc(code.substring(i, j))))
                i = j; continue
            }

            // 标识符
            if (isIdentStart(c)) {
                var j = i
                while (j < n && isIdentPart(code[j])) j++
                val word = code.substring(i, j)
                val cls = when {
                    spec.keywords.contains(word) -> "tok-kw"
                    spec.types.contains(word) -> "tok-type"
                    spec.builtins.contains(word) -> "tok-type"
                    isCallSite(code, j) -> "tok-fn"
                    else -> null
                }
                sb.append(if (cls == null) esc(word) else span(cls, esc(word)))
                i = j; continue
            }

            // 其他符号原样输出
            sb.append(esc(c.toString()))
            i++
        }
        return sb.toString()
    }

    private fun isStringDelim(c: Char, spec: Spec): Boolean = when (c) {
        '"', '\'' -> true
        '`' -> spec.backtickString
        else -> false
    }

    private fun isIdentStart(c: Char): Boolean =
        c.isLetter() || c == '_' || c == '$'

    private fun isIdentPart(c: Char): Boolean =
        c.isLetterOrDigit() || c == '_' || c == '$'

    /** 标识符后紧跟（可隔空格）左括号，视为函数调用。 */
    private fun isCallSite(code: String, from: Int): Boolean {
        var k = from
        while (k < code.length && (code[k] == ' ' || code[k] == '\t')) k++
        return k < code.length && code[k] == '('
    }

    /** 扫描字符串到结束引号；未闭合时止于行尾（反引号模板字符串允许跨行）。 */
    private fun scanString(code: String, start: Int, quote: Char): Int {
        val n = code.length
        var j = start + 1
        while (j < n) {
            val d = code[j]
            if (d == '\\' && j + 1 < n) {
                j += 2; continue
            }
            if (d == quote) return j + 1
            if (d == '\n' && quote != '`') return j
            j++
        }
        return n
    }

    // ---------------------------------------------------------------- HTML / XML

    private fun highlightMarkup(code: String): String {
        val n = code.length
        val sb = StringBuilder(n + n / 3)
        var i = 0
        while (i < n) {
            if (code.startsWith("<!--", i)) {
                val end = code.indexOf("-->", i + 4)
                val stop = if (end >= 0) end + 3 else n
                sb.append(span("tok-com", esc(code.substring(i, stop))))
                i = stop; continue
            }
            if (code[i] == '<') {
                var j = i + 1
                val closing = j < n && code[j] == '/'
                if (closing) j++
                val nameStart = j
                while (j < n && isTagNameChar(code[j])) j++
                val name = code.substring(nameStart, j)
                sb.append(esc("<"))
                if (closing) sb.append('/')
                if (name.isNotEmpty()) sb.append(span("tok-tag", esc(name)))
                while (j < n && code[j] != '>') {
                    val ch = code[j]
                    if (ch == '"' || ch == '\'') {
                        val stop = scanString(code, j, ch)
                        sb.append(span("tok-str", esc(code.substring(j, stop))))
                        j = stop; continue
                    }
                    if (ch.isLetter() || ch == ':' || ch == '@' || ch == '_') {
                        var k = j
                        while (k < n && isTagNameChar(code[k]) || k < n && code[k] == ':') k++
                        sb.append(span("tok-attr", esc(code.substring(j, k))))
                        j = k; continue
                    }
                    sb.append(esc(ch.toString()))
                    j++
                }
                if (j < n) {
                    sb.append(esc(">"))
                    j++
                }
                i = j; continue
            }
            sb.append(esc(code[i].toString()))
            i++
        }
        return sb.toString()
    }

    private fun isTagNameChar(c: Char): Boolean =
        c.isLetterOrDigit() || c == '-' || c == '_'

    // ---------------------------------------------------------------- CSS

    private fun highlightCss(code: String): String {
        val n = code.length
        val sb = StringBuilder(n + n / 3)
        var i = 0
        while (i < n) {
            val c = code[i]
            if (c == '/' && i + 1 < n && code[i + 1] == '*') {
                val end = code.indexOf("*/", i + 2)
                val stop = if (end >= 0) end + 2 else n
                sb.append(span("tok-com", esc(code.substring(i, stop))))
                i = stop; continue
            }
            if (c == '"' || c == '\'') {
                val stop = scanString(code, i, c)
                sb.append(span("tok-str", esc(code.substring(i, stop))))
                i = stop; continue
            }
            if (c == '@') {
                var j = i + 1
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '-')) j++
                sb.append(span("tok-kw", esc(code.substring(i, j))))
                i = j; continue
            }
            if (c == '#' && i + 1 < n && isHexChar(code[i + 1])) {
                var j = i + 1
                while (j < n && isHexChar(code[j])) j++
                sb.append(span("tok-num", esc(code.substring(i, j))))
                i = j; continue
            }
            if (c.isDigit()) {
                var j = i
                while (j < n && (code[j].isDigit() || code[j] == '.' || code[j] == '%')) j++
                sb.append(span("tok-num", esc(code.substring(i, j))))
                i = j; continue
            }
            if (c.isLetter() || c == '-' || c == '_') {
                var j = i
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '-' || code[j] == '_')) j++
                var k = j
                while (k < n && (code[k] == ' ' || code[k] == '\t')) k++
                val word = code.substring(i, j)
                sb.append(
                    if (k < n && code[k] == ':') span("tok-attr", esc(word)) else esc(word)
                )
                i = j; continue
            }
            sb.append(esc(c.toString()))
            i++
        }
        return sb.toString()
    }

    private fun isHexChar(c: Char): Boolean =
        c.isDigit() || (c in 'a'..'f') || (c in 'A'..'F')

    // ---------------------------------------------------------------- 工具

    private fun span(cls: String, text: String): String =
        if (text.isEmpty()) "" else "<span class=\"$cls\">$text</span>"

    private fun esc(text: String): String {
        if (text.indexOf('&') < 0 && text.indexOf('<') < 0 && text.indexOf('>') < 0) return text
        val sb = StringBuilder(text.length + 16)
        for (ch in text) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
