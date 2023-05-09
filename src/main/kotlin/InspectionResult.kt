/**
 * Data class that stores the difference information
 */
data class InspectionResult(
    val details: String,
    val leftName: String,
    val rightName: String? = null,

    val leftStartPos: Int? = null,
    val leftEndPos: Int? = null,
    val leftDiff: String? = null,

    val rightStartPos: Int? = null,
    val rightEndPos: Int? = null,
    val rightDiff: String? = null,
    val suffix: String? = null,
) {
    fun toHumanString(): String {
        var result = "$details: ${diffPartString(leftName, leftDiff, leftStartPos, leftEndPos)}"
        if (rightName != null) {
            result += " vs ${diffPartString(rightName, rightDiff, rightStartPos, rightEndPos)}"
        }
        if (suffix != null) {
            result += " $suffix"
        }
        return result
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
