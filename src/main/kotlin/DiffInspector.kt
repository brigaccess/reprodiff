import java.nio.file.Path

interface DiffInspector {
    /** Returns a list of differences between two files (recursively if possible) */
    suspend fun diff(
        left: Path,
        right: Path,
        registry: DiffInspectorRegistry,
        depth: Int = 0,
        maxDepth: Int = 2,
        leftHumanName: String = left.fileName.toString(),
        rightHumanName: String = right.fileName.toString(),
    ): List<InspectionResult>
}