package com.example.personal_studio.feature.scanner

import com.example.personal_studio.domain.model.ScanFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanFilterLabelTest {
    @Test fun `maps each filter to chinese label`() {
        assertEquals("彩色", scanFilterLabel(ScanFilter.COLOR))
        assertEquals("灰度", scanFilterLabel(ScanFilter.GRAYSCALE))
        assertEquals("黑白", scanFilterLabel(ScanFilter.BW))
    }
}
