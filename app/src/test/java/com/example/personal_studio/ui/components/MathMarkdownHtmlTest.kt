package com.example.personal_studio.ui.components

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
}
