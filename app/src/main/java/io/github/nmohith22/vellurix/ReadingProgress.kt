package io.github.nmohith22.vellurix

internal data class BookProgress(val fraction: Float, val position: Int?, val total: Int?) {
    val percent: Int get() = (fraction.coerceIn(0f, 1f) * 100).toInt()
}

internal data class ReaderProgressSection(val title: String, val start: Float)

internal fun progressLabel(progress: BookProgress, mode: String): String =
    if (mode == "pages" && progress.position != null && progress.total != null && progress.total > 0) {
        "${progress.position.coerceIn(1, progress.total)} / ${progress.total}"
    } else "${progress.percent}%"

internal fun sectionIndexAt(progress: Float, sections: List<ReaderProgressSection>): Int? =
    if (sections.isEmpty()) null else sections.indexOfLast { it.start <= progress.coerceIn(0f, 1f) }.coerceAtLeast(0)
