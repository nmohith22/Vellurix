package io.github.nmohith22.vellurix

internal data class BookProgress(val fraction: Float, val position: Int?, val total: Int?) {
    val percent: Int get() = (fraction.coerceIn(0f, 1f) * 100).toInt()
}

internal data class ReaderProgressSection(
    val title: String,
    val start: Float,
    val firstPosition: Int? = null,
)

internal data class ChapterPageProgress(val page: Int, val total: Int)

internal fun progressLabel(progress: BookProgress, mode: String): String =
    if (mode == "pages" && progress.position != null && progress.total != null && progress.total > 0) {
        "${progress.position.coerceIn(1, progress.total)} / ${progress.total}"
    } else "${progress.percent}%"

internal fun sectionIndexAt(progress: Float, sections: List<ReaderProgressSection>): Int? =
    if (sections.isEmpty()) null else sections.indexOfLast { it.start <= progress.coerceIn(0f, 1f) }.coerceAtLeast(0)

internal fun chapterPageProgress(position: Int?, total: Int?, sections: List<ReaderProgressSection>): ChapterPageProgress? {
    if (position == null || total == null || position !in 1..total) return null
    val starts = sections.asSequence().mapNotNull { it.firstPosition }.filter { it in 1..total }
    val first = starts.filter { it <= position }.maxOrNull() ?: return null
    val next = starts.filter { it > position }.minOrNull()
    val last = next?.minus(1) ?: total
    return ChapterPageProgress(position - first + 1, last - first + 1)
}
