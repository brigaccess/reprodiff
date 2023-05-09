package inspectors

import Difference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

class DiffInspectorRegistry {
    private val inspectors = mutableListOf<DiffInspector>()

    fun register(i: DiffInspector) {
        inspectors.add(i)
    }

    fun inspectFiles(left: Path, right: Path, depth: Int = 0, maxDepth: Int = 2): List<Difference> {

        return runBlocking {
            inspectors.map {
                async (Dispatchers.Default) { it.diff(left, right, depth, maxDepth) }
            }
            .map { it.await() }
            .flatten()
        }
    }
}