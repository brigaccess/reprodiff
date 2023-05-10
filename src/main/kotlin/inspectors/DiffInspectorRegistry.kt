package inspectors

import InspectionResult
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

class DiffInspectorRegistry {
    private val inspectors = mutableListOf<DiffInspector>()

    fun register(i: DiffInspector) {
        inspectors.add(i)
    }

    fun inspectFiles(
        left: Path, right: Path, depth: Int = 0, maxDepth: Int = 2, leftName: String? = null, rightName: String? = null
    ): List<InspectionResult> {
        if (depth > maxDepth) {
            return listOf(
                InspectionResult(
                    "Depth limit exceeded, will not diff",
                    leftName ?: left.fileName.toString(),
                    rightName ?: right.fileName.toString()
                )
            )
        }
        return runBlocking {
            inspectors.map {
                it.diff(
                    left, right, this@DiffInspectorRegistry, depth, maxDepth, leftName, rightName
                )
            }.flatten()
        }
    }
}