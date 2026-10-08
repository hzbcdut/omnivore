package app.omnivore.omnivore.core.data

// Plain data classes for the JSON payloads the web reader posts over its
// javascript bridge. Mirrors the shape of the old GraphQL input types, but with
// nullable fields instead of Apollo's Optional wrappers.

data class ReadingProgressParams(
    val id: String?,
    val readingProgressPercent: Double?,
    val readingProgressAnchorIndex: Int?,
    val force: Boolean?
)

data class CreateHighlightParams(
    val type: String?,
    val shortId: String?,
    val id: String?,
    val quote: String?,
    val patch: String?,
    val articleId: String?,
    val annotation: String?,
    val color: String? = null,
    val highlightPositionAnchorIndex: Int,
    val highlightPositionPercent: Double
)

data class UpdateHighlightParams(
    val highlightId: String?,
    val libraryItemId: String?,
    val annotation: String?,
    val sharedAt: String?
)

data class MergeHighlightsParams(
    val shortId: String?,
    val id: String?,
    val quote: String?,
    val patch: String?,
    val articleId: String?,
    val prefix: String?,
    val suffix: String?,
    val overlapHighlightIdList: List<String>?,
    val annotation: String?,
    val color: String? = null
)

data class DeleteHighlightParams(
    val highlightId: String,
    val libraryItemId: String
)
