package com.example.personal_studio.core.bigram

import org.junit.Assert.assertEquals
import org.junit.Test

class BigramTokenizerTest {

    @Test fun `index empty string yields empty`() {
        assertEquals("", BigramTokenizer.tokenizeForIndex(""))
    }

    @Test fun `index single CJK char yields the char`() {
        assertEquals("数", BigramTokenizer.tokenizeForIndex("数"))
    }

    @Test fun `index two-char CJK yields one bigram`() {
        assertEquals("数学", BigramTokenizer.tokenizeForIndex("数学"))
    }

    @Test fun `index four-char CJK yields three sliding bigrams`() {
        assertEquals("微积 积分 分极", BigramTokenizer.tokenizeForIndex("微积分极"))
    }

    @Test fun `index ASCII word is lowercased and kept whole`() {
        assertEquals("compose", BigramTokenizer.tokenizeForIndex("Compose"))
    }

    @Test fun `index mixed language splits on whitespace then per-chunk strategy`() {
        assertEquals("compose 状态 态恢 恢复", BigramTokenizer.tokenizeForIndex("Compose 状态恢复"))
    }

    @Test fun `index treats commas and full-width punctuation as separators`() {
        assertEquals("数学 物理", BigramTokenizer.tokenizeForIndex("数学，物理"))
    }

    @Test fun `query empty returns empty`() {
        assertEquals("", BigramTokenizer.tokenizeForQuery(""))
    }

    @Test fun `query single CJK char wraps quoted`() {
        assertEquals("\"数\"", BigramTokenizer.tokenizeForQuery("数"))
    }

    @Test fun `query two-char CJK is one quoted bigram`() {
        assertEquals("\"数学\"", BigramTokenizer.tokenizeForQuery("数学"))
    }

    @Test fun `query CJK chunk joins bigrams with implicit AND (space)`() {
        assertEquals("\"微积\" \"积分\"", BigramTokenizer.tokenizeForQuery("微积分"))
    }

    @Test fun `query multiple chunks join with implicit AND (space)`() {
        assertEquals("\"微积\" \"积分\" \"极限\"", BigramTokenizer.tokenizeForQuery("微积分 极限"))
    }

    @Test fun `query ASCII word is lowercased and quoted`() {
        assertEquals("\"compose\"", BigramTokenizer.tokenizeForQuery("Compose"))
    }
}
