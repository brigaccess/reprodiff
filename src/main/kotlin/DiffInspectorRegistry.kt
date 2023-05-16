import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.nio.file.Path

class DiffInspectorRegistry {
    private val inspectors = mutableListOf<DiffInspector>()

    fun register(i: DiffInspector) {
        inspectors.add(i)
    }

    suspend fun inspectFiles(
        left: Path, right: Path, depth: Int = 0, maxDepth: Int = 2, leftName: String? = null, rightName: String? = null
    ): List<InspectionResult> = coroutineScope {
        val leftHumanName = leftName ?: left.fileName.toString()
        val rightHumanName = rightName ?: right.fileName.toString()
        if (depth > maxDepth) {
            return@coroutineScope listOf(
                InspectionResult(
                    INSPECTION_DEPTH_EXCEEDED,
                    leftHumanName,
                    rightHumanName
                )
            )
        }

        return@coroutineScope inspectors.map {
            return@map async (Dispatchers.Default) {
                it.diff(
                    left, right, this@DiffInspectorRegistry, depth, maxDepth, leftHumanName, rightHumanName
                )
            }
        }.map { it.await() }.flatten()
    }

    companion object {
        const val INSPECTION_DEPTH_EXCEEDED = "Depth limit exceeded, will not diff"
    }
}