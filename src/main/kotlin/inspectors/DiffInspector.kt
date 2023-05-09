package inspectors

import Difference
import java.nio.file.Path

interface DiffInspector {
    /** Returns a list of differences between two files (recursively if possible) */
    fun diff(
        left: Path,
        right: Path,
        depth: Int = 0,
        maxDepth: Int = 2,
        leftName: String? = null,
        rightName: String? = null
    ): List<Difference>
}