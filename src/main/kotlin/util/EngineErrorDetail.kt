package util

class EngineErrorDetail(
    reason: String? = null,
    val pageId: Long? = null,
    val pageType: PageType? = null
) : ErrorDetail(reason) {
    override fun StringBuilder.appendExtra() {
        pageId?.let { append(" pageId=$it") }
        pageType?.let { append(" pageType=$it") }
    }
}
