package com.example.personal_studio.feature.scanner.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanEditorExitTest {
    @Test fun `discard only when new and empty`() {
        assertTrue(shouldDiscardOnExit(isNew = true, pageCount = 0))   // 新建+没拍 → 丢弃空壳
        assertFalse(shouldDiscardOnExit(isNew = true, pageCount = 1))  // 新建+已拍 → 保留
        assertFalse(shouldDiscardOnExit(isNew = false, pageCount = 0)) // 已有文档 → 永不删
        assertFalse(shouldDiscardOnExit(isNew = false, pageCount = 3))
    }
}
