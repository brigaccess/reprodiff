package inspectors

import DiffInspector
import DiffInspectorRegistry
import InspectionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import kotlin.io.path.notExists

typealias InputStreamHashFunc = (x: InputStream) -> String

class SizeAndHashInspector(private val ignoreSize: Boolean, private val hashFunc: InputStreamHashFunc) : DiffInspector {
    override fun diff(
        left: Path,
        right: Path,
        registry: DiffInspectorRegistry,
        depth: Int,
        maxDepth: Int,
        leftHumanName: String,
        rightHumanName: String
    ): List<InspectionResult> {
        val result = mutableListOf<InspectionResult>()

        // Check whether files exist
        val paths = listOf(left, right)
        paths.forEach {
            if (it.notExists()) {
                throw FileNotFoundException("$it")
            }
        }

        // Check whether file sizes match (to avoid expensive hash computations)
        paths.map { it.fileSize() }.zipWithNext { leftSize, rightSize ->
            if (leftSize != rightSize) {
                result.add(
                    InspectionResult(
                        "File size mismatch",
                        leftHumanName,
                        rightHumanName,
                        leftDiff = "$leftSize bytes",
                        rightDiff = "$rightSize bytes"
                    )
                )
                if (!ignoreSize) {
                    return result
                }
            }
        }

        // Calculate whether secure hashes match
        runBlocking {
            paths.map {
                async(Dispatchers.Default) {
                    hashFunc(it.inputStream())
                }
            }.map { it.await() }.zipWithNext { left, right ->
                if (left != right) {
                    result.add(
                        InspectionResult(
                            "File hash mismatch",
                            leftHumanName,
                            rightHumanName,
                            leftDiff = left,
                            rightDiff = right
                        )
                    )
                }
            }
        }

        return result
    }
}