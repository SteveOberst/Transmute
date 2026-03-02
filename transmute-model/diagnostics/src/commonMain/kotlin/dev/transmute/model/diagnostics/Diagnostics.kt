@file:Suppress("unused")

package dev.transmute.model.diagnostics

import dev.transmute.model.core.ByteRange
import dev.transmute.model.core.StreamId

/**
 * Severity of an inspection issue.
 */
enum class IssueSeverity {
    /** Informational note - not a problem. */
    Info,

    /** Possible problem that may or may not affect playback. */
    Warning,

    /** Definite problem likely to cause issues. */
    Error,
}

/**
 * Machine-readable issue code for programmatic matching.
 */
@JvmInline
value class IssueCode(val value: String) {
    init {
        require(value.isNotBlank()) { "IssueCode must not be blank" }
    }

    override fun toString(): String = value
}

/**
 * Location context for an inspection issue.
 */
data class IssueContext(
    val range: ByteRange? = null,
    val streamId: StreamId? = null,
    val detail: String? = null,
)

/**
 * A single problem or observation discovered during inspection.
 */
data class InspectionIssue(
    val severity: IssueSeverity,
    val code: IssueCode,
    val message: String,
    val context: IssueContext? = null,
)
