package com.example.personal_studio.feature.scanner

import com.example.personal_studio.domain.model.ScanFilter

/** 滤镜中文标签,供各 scanner 屏的 `[滤镜]` chip 复用。 */
fun scanFilterLabel(f: ScanFilter): String = when (f) {
    ScanFilter.COLOR -> "彩色"
    ScanFilter.GRAYSCALE -> "灰度"
    ScanFilter.BW -> "黑白"
}
