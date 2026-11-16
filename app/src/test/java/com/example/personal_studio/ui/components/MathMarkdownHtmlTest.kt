package com.example.personal_studio.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MathMarkdownHtmlTest {

    @Test fun `block math keeps delimiters together (no br inside, so KaTeX can match)`() {
        val html = toHtml("\$\$\n\\sin C=\\frac34\n\$\$")
        // bug 签名:块内换行被换成 <br> 会把 $$ 拆到不同文本节点,KaTeX 跨节点匹配不到
        assertFalse("不应出现 \$\$<br>", html.contains("\$\$<br>"))
        assertFalse("不应出现 <br>\$\$", html.contains("<br>\$\$"))
        // 应保留可被 KaTeX 匹配的 $$...$$(同一文本节点内)
        assertTrue(html.contains("\$\$"))
    }

    @Test fun `inline math preserved on one line`() {
        val html = toHtml("半径为 \$R\$ 的圆环")
        assertTrue(html.contains("\$R\$"))
    }

    @Test fun `non-math newline still becomes br`() {
        val html = toHtml("行一\n行二")
        assertTrue(html.contains("行一<br>行二"))
    }

    @Test fun `bold and code outside math still render`() {
        assertTrue(toHtml("**粗**").contains("<strong>粗</strong>"))
        assertTrue(toHtml("`x`").contains("<code>x</code>"))
    }

    @Test fun `markdown inside math is not mangled`() {
        // 公式里的 * 不应被当成 italic 处理
        val html = toHtml("\$a*b*c\$")
        assertTrue(html.contains("\$a*b*c\$"))
        assertFalse(html.contains("<em>"))
    }

    @Test fun headings() {
        assertEquals("<h2>第 1 步</h2>", toHtml("## 第 1 步"))
        assertEquals("<h3>小结</h3><p>正文</p>", toHtml("### 小结 ###\n正文"))
        assertEquals("<h4>深层</h4>", toHtml("###### 深层"))
        assertEquals("<p>#标签</p>", toHtml("#标签"))
    }

    @Test fun `a list may follow a paragraph line directly`() {
        assertEquals("<p>结论：</p><ul><li>甲</li><li>乙</li></ul>", toHtml("结论：\n- 甲\n- 乙"))
        assertEquals("<ul><li>星号</li><li>加号</li></ul>", toHtml("* 星号\n* 加号"))
    }

    @Test fun `ordered lists keep their start and survive blank lines`() {
        assertEquals("<ol start=\"3\"><li>a</li><li>b</li></ol>", toHtml("3. a\n\n4. b"))
        assertEquals("<ol><li>a<p>解释</p></li><li>b</li></ol>", toHtml("1. a\n\n   解释\n\n2. b"))
    }

    @Test fun `nested lists by indentation`() {
        assertEquals(
            "<ol><li>步骤<ul><li>细节一</li><li>细节二</li></ul></li><li>下一步</li></ol>",
            toHtml("1. 步骤\n   - 细节一\n   - 细节二\n2. 下一步"),
        )
        assertEquals("<ul><li>a<ul><li>b</li></ul></li></ul>", toHtml("- a\n\t- b"))
    }

    @Test fun `block quotes`() {
        assertEquals(
            "<blockquote><p>比较判别法：\$a_n\$<br>第二行</p></blockquote>",
            toHtml("> 比较判别法：\$a_n\$\n> 第二行"),
        )
    }

    @Test fun `tables with alignment, pipes inside formulas stay in their cell`() {
        assertEquals(
            "<div class=\"table\"><table><thead><tr><th>名称</th><th style=\"text-align:right\">值</th></tr></thead>" +
                "<tbody><tr><td>\$|x|\$</td><td style=\"text-align:right\">2</td></tr></tbody></table></div>",
            toHtml("| 名称 | 值 |\n| :-- | --: |\n| \$|x|\$ | 2 |"),
        )
    }

    @Test fun `fenced code is kept verbatim`() {
        assertEquals(
            "<pre><code>x = a*b*c  # \$5\nif a &lt; b: pass</code></pre>",
            toHtml("```python\nx = a*b*c  # \$5\nif a < b: pass\n```"),
        )
    }

    @Test fun `dollar signs in inline code are not math`() {
        assertEquals("<p>运行 <code>echo \$HOME</code> 后 \$x\$ 才是公式</p>", toHtml("运行 `echo \$HOME` 后 \$x\$ 才是公式"))
    }

    @Test fun `rules, escaping, br, links, italic and strike`() {
        assertEquals("<p>a</p><hr><p>b</p>", toHtml("a\n\n---\n\nb"))
        assertEquals("<p>&lt;script&gt;x&lt;/script&gt; 1<br>2</p>", toHtml("<script>x</script> 1<br>2"))
        assertEquals("<p><a>文档</a></p>", toHtml("[文档](https://example.com/a)"))
        assertEquals("<p>2*3*4 与 <em>强调</em></p>", toHtml("2*3*4 与 *强调*"))
        assertEquals("<p><del>旧</del></p>", toHtml("~~旧~~"))
    }

    @Test fun `a typical answer renders every block`() {
        val d = "$"
        val html = toHtml(
            "## 第 1 步\n\n考虑 ${d}p > 1${d}：\n\n$d$d\n\\int_1^\\infty x^{-p}\\,dx\n$d$d\n\n- 收敛；\n- 发散。\n\n> 比较判别法",
        )
        assertEquals(
            "<h2>第 1 步</h2><p>考虑 ${d}p &gt; 1${d}：</p><p>$d$d\n\\int_1^\\infty x^{-p}\\,dx\n$d$d</p>" +
                "<ul><li>收敛；</li><li>发散。</li></ul><blockquote><p>比较判别法</p></blockquote>",
            html,
        )
    }
}
