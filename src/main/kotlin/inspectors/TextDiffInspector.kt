package inspectors

import DiffInspector
import DiffInspectorRegistry
import InspectionResult
import com.github.difflib.DiffUtils
import com.github.difflib.patch.DeltaType
import com.github.difflib.text.DiffRowGenerator
import org.apache.commons.io.input.BoundedInputStream
import org.apache.tika.Tika
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream

/**
 * Generates diffs for text files
 */
class TextDiffInspector(private val sizeLimit: Long) : DiffInspector {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val tika = Tika()

    override fun diff(
        left: Path,
        right: Path,
        registry: DiffInspectorRegistry,
        depth: Int,
        maxDepth: Int,
        leftHumanName: String,
        rightHumanName: String
    ): List<InspectionResult> {
        val pathsAndNames = listOf(Pair(left, leftHumanName), Pair(right, rightHumanName))
        val pathsWithNamesAndSizes = pathsAndNames.map {
            Triple(it.first, it.second, it.first.fileSize())
        }

        // Check size limit
        if (!pathsWithNamesAndSizes.all {
                val fileBigger = it.third > sizeLimit
                if (fileBigger) {
                    logger.debug("Size of file ${it.first} exceeds the size limit: ${it.third} > $sizeLimit")
                }
                !fileBigger
        }) return emptyList()

        // Check file MIMEs
        if (!pathsAndNames.all {
            // First pass to filter out the files that have extensions hinting non-textual contents
            val (path, humanName) = it
            val detectedByName = tika.detect(path.toString())
            // application/octet-stream => Tika does not know what it might be, so let's check the contents
            if (!mimeIsText(detectedByName) && detectedByName != "application/octet-stream") {
                logger.debug("Guessed '$detectedByName' from $path file name, it is not text. Skipping.")
                return@all false
            }
            // Second pass to make sure the file is indeed a text file
            val detectedByContents = tika.detect(path)
            if (mimeIsText(detectedByContents)) {
                return@all true
            }
            logger.debug("Guessed '$detectedByContents' from $path contents, it is not text. Skipping.")
            false
        }) return emptyList()

        val result = mutableListOf<InspectionResult>()

        // Finally we can load the files themselves and generate a diff
        pathsWithNamesAndSizes.map {
            val (path, humanName, size) = it
            try {
                val reader = BoundedInputStream(path.inputStream(), size + 1).buffered().reader()
                reader.use { reader.readLines() }
            } catch (e: Exception) {
                logger.error("Error reading lines from file $path ($humanName): $e")
                return@diff emptyList()
            }
        }.zipWithNext().forEach {
            val patch = DiffUtils.diff(it.first, it.second)
            for (delta in patch.deltas) {
                val leftStartPosition = delta.source.position + 1
                val leftEndPosition = leftStartPosition + delta.source.lines.size
                val leftDiff = "\n======================\n" + delta.source.lines.joinToString("\n") + "\n\n---"
                val rightStartPosition = delta.target.position + 1
                val rightEndPosition = rightStartPosition + delta.target.lines.size
                val rightDiff = "---\n" + delta.target.lines.joinToString("\n") + "\n======================"
                val diffType = when(delta.type) {
                    DeltaType.INSERT -> INSPECTION_RIGHT_EXTRA_LINES
                    DeltaType.CHANGE -> INSPECTION_RIGHT_CHANGED_LINES
                    DeltaType.DELETE -> INSPECTION_RIGHT_MISSING_LINES
                    else -> "Something changed"
                }

                result.add(InspectionResult(
                    diffType,
                    leftHumanName,
                    rightHumanName,
                    leftStartPosition,
                    leftEndPosition,
                    leftDiff,
                    rightStartPosition,
                    rightEndPosition,
                    rightDiff
                ))
            }
        }

        return result
    }

    private fun mimeIsText(m: String): Boolean {
        return m.startsWith("text/") || EXTRA_TEXT_MIMES.contains(m)
    }

    companion object {
        val EXTRA_TEXT_MIMES = setOf(
            "image/svg+xml",
            "application/javascript",
            "application/ecmascript",
            "application/x-javascript",
            "application/x-ecmascript"
        )

        const val INSPECTION_RIGHT_EXTRA_LINES = "Extra lines in right"
        const val INSPECTION_RIGHT_CHANGED_LINES = "Changed lines in right"
        const val INSPECTION_RIGHT_MISSING_LINES = "Missing lines in right"
    }
}