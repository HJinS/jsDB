package util

/** [ErrorDetail] for storage-engine-level failures (page/disk/buffer-pool) — [exception.StorageEngineException]'s payload. */
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
