package io.github.nmohith22.vellurix

internal data class BookProgress(val fraction: Float, val position: Int?, val total: Int?) {
    val percent: Int get() = (fraction.coerceIn(0f, 1f) * 100).toInt()
}

internal data class ReaderProgressSection(
    val title: String,
    val start: Float,
    val firstPosition: Int? = null,
    val lastPosition: Int? = null,
)

internal data class ChapterPageProgress(val page: Int, val total: Int)

internal fun progressLabel(progress: BookProgress, mode: String): String =
    if (mode == "pages" && progress.position != null && progress.total != null && progress.total > 0) {
        "${progress.position.coerceIn(1, progress.total)} / ${progress.total}"
    } else "${progress.percent}%"

internal fun sectionIndexAt(progress: Float, sections: List<ReaderProgressSection>): Int? =
    if (sections.isEmpty()) null else sections.indexOfLast { it.start <= progress.coerceIn(0f, 1f) }.coerceAtLeast(0)

internal fun chapterPageProgress(position: Int?, total: Int?, sections: List<ReaderProgressSection>): ChapterPageProgress? {
    if (position == null || total == null || total <= 0) return null
    val index = sectionIndexAt((position - 1).toFloat() / total, sections) ?: return null
    val section = sections[index]
    val first = section.firstPosition ?: ((section.start * total).toInt() + 1).coerceIn(1, total)
    val nextSection = sections.drop(index + 1).firstOrNull { next ->
        if (section.firstPosition != null && next.firstPosition != null) next.firstPosition > section.firstPosition
        else next.start > section.start
    }
    val last = section.lastPosition
        ?: nextSection?.firstPosition?.minus(1)
        ?: nextSection?.let { (it.start * total).toInt() }
        ?: total
    val chapterTotal = (last - first + 1).coerceAtLeast(1)
    return ChapterPageProgress((position - first + 1).coerceIn(1, chapterTotal), chapterTotal)
}
