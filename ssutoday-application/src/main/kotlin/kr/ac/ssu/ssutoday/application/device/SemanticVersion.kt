package kr.ac.ssu.ssutoday.application.device

internal data class SemanticVersion(
    private val value: String,
) : Comparable<SemanticVersion> {
    private val parts = value.split(".").map(String::toInt)

    override fun compareTo(other: SemanticVersion): Int =
        parts.zip(other.parts).firstNotNullOfOrNull { (left, right) -> (left - right).takeIf { it != 0 } } ?: 0
}
