/**
 * Data class that stores the difference information
 */
data class Difference(
    val leftName: String,
    val rightName: String,
    val details: String,

    val leftStartPos: Int? = null,
    val leftEndPos: Int? = null,
    val leftDiff: String? = null,

    val rightStartPos: Int? = null,
    val rightEndPos: Int? = null,
    val rightDiff: String? = null,
) {
    fun toHumanString(): String {
        return "$details: ${diffPartString(leftName, leftDiff, leftStartPos, leftEndPos)} vs ${
            diffPartString(
                rightName,
                rightDiff,
                rightStartPos,
                rightEndPos
            )
        }"
    }

    private fun posString(startPos: Int? = null, endPos: Int? = null): String {
        if (startPos == null && endPos == null) {
            return ""
        }

        val isRange = startPos != null && endPos != null
        return "${startPos ?: ""}${if (isRange) ":" else ""}${endPos ?: ""}"
    }

    private fun diffPartString(name: String, diff: String? = null, startPos: Int? = null, endPos: Int? = null): String {
        val pos = posString(startPos, endPos)
        return when (Pair(diff != null, pos.isNotBlank())) {
            Pair(true, true) -> "$diff ($name, $pos)"
            Pair(true, false) -> "$diff ($name)"
            Pair(false, true) -> "($name, $pos)"
            else -> name
        }
    }
}
