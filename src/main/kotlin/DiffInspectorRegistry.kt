import java.nio.file.Path

class DiffInspectorRegistry {
    private val inspectors = mutableListOf<DiffInspector>()

    fun register(i: DiffInspector) {
        inspectors.add(i)
    }

    fun inspectFiles(
        left: Path, right: Path, depth: Int = 0, maxDepth: Int = 2, leftName: String? = null, rightName: String? = null
    ): List<InspectionResult> {
        val leftHumanName = leftName ?: left.fileName.toString()
        val rightHumanName = rightName ?: right.fileName.toString()
        if (depth > maxDepth) {
            return listOf(
                InspectionResult(
                    INSPECTION_DEPTH_EXCEEDED,
                    leftHumanName,
                    rightHumanName
                )
            )
        }
        return inspectors.map {
            it.diff(
                left, right, this@DiffInspectorRegistry, depth, maxDepth, leftHumanName, rightHumanName
            )
        }.flatten()
    }

    companion object {
        const val INSPECTION_DEPTH_EXCEEDED = "Depth limit exceeded, will not diff"
    }
}