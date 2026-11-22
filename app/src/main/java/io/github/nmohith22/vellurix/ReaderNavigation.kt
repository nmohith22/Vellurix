package io.github.nmohith22.vellurix

internal data class ReaderResourcePosition(val href: String, val position: Int?)
internal data class ReaderResourceRange(val firstPosition: Int?, val lastPosition: Int?)

internal fun readerResourceHref(href: String): String = href.substringBefore('#').substringBefore('?')

internal fun indexResourcePositions(positions: List<ReaderResourcePosition>): Map<String, ReaderResourceRange> =
    positions.groupBy { readerResourceHref(it.href) }.mapValues { (_, resourcePositions) ->
        val values = resourcePositions.mapNotNull { it.position }
        ReaderResourceRange(values.minOrNull(), values.maxOrNull())
    }

internal fun isAtResourceBoundary(
    href: String,
    currentPosition: Int?,
    currentProgression: Double?,
    positionsByResource: Map<String, ReaderResourceRange>,
    forward: Boolean,
): Boolean {
    if (currentProgression != null && (if (forward) currentProgression >= 0.985 else currentProgression <= 0.015)) return true
    val current = currentPosition ?: return false
    val resourceRange = positionsByResource[readerResourceHref(href)] ?: return false
    val boundary = if (forward) resourceRange.lastPosition else resourceRange.firstPosition
    return boundary != null && if (forward) current >= boundary else current <= boundary
}

internal fun adjacentReadingOrderIndex(href: String, readingOrderHrefs: List<String>, forward: Boolean): Int? {
    val currentHref = readerResourceHref(href)
    val currentIndex = readingOrderHrefs.indexOfLast { readerResourceHref(it) == currentHref }
    if (currentIndex == -1) return null
    return (currentIndex + if (forward) 1 else -1).takeIf { it in readingOrderHrefs.indices }
}
