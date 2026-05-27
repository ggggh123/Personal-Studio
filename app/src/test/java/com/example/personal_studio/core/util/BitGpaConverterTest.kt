package com.example.personal_studio.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BitGpaConverterTest {
    @Test fun `gradePoint formula and levels`() {
        assertEquals(4.0, BitGpaConverter.toGradePoint("优秀")!!, 0.001)
        assertEquals(3.25, BitGpaConverter.toGradePoint("80")!!, 0.001) // 4 - 3*400/1600
        assertEquals(0.0, BitGpaConverter.toGradePoint("不及格")!!, 0.001)
        assertNull(BitGpaConverter.toGradePoint("通过"))
    }
    @Test fun `toScore maps levels and passes numbers through`() {
        assertEquals(95.0, BitGpaConverter.toScore("优秀")!!, 0.001)
        assertEquals(85.0, BitGpaConverter.toScore("良好")!!, 0.001)
        assertEquals(75.0, BitGpaConverter.toScore("中等")!!, 0.001)
        assertEquals(65.0, BitGpaConverter.toScore("及格")!!, 0.001)
        assertEquals(40.0, BitGpaConverter.toScore("不及格")!!, 0.001)
        assertEquals(88.0, BitGpaConverter.toScore("88")!!, 0.001)
        assertNull(BitGpaConverter.toScore("通过"))   // 二级制不计入均分
        assertNull(BitGpaConverter.toScore(""))
    }
}
