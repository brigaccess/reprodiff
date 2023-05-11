package inspectors

import DEFAULT_TEXT_SIZE_LIMIT
import DiffInspectorRegistry
import org.apache.tika.Tika
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.*
import java.nio.file.Path
import kotlin.io.path.Path

class TextDiffInspectorTest {
    private val registry = DiffInspectorRegistry()

    private val smallest = getResourcePath("/10bytes.txt")
    private val small = getResourcePath("/100bytes.txt")

    private val original = getResourcePath("/diff/original.txt")
    private val newLines = getResourcePath("/diff/new-lines.txt")
    private val changedLines = getResourcePath("/diff/changed-lines.txt")
    private val deletedLines = getResourcePath("/diff/deleted-lines.txt")

    private val mockTextTika: Tika = mock()
    private val mockNonTextTika: Tika = mock()
    init {
        whenever(mockTextTika.detect(anyString()))
            .thenReturn("text/plain")
        whenever(mockTextTika.detect(isA<Path>()))
            .thenReturn("text/plain")

        whenever(mockNonTextTika.detect(anyString()))
            .thenReturn("image/png")
        whenever(mockNonTextTika.detect(isA<Path>()))
            .thenReturn("image/png")
    }

    private val defaultTarget = TextDiffInspector(DEFAULT_TEXT_SIZE_LIMIT, mockTextTika)

    @Test
    fun testDiffChecksFileSizes() {
        val smallestTarget = TextDiffInspector(9, mockTextTika)
        val observedSmallest = smallestTarget.diff(smallest, small, registry)
        assertEquals(0, observedSmallest.size)

        val smallTarget = TextDiffInspector(50, mockTextTika)
        val observedSmall = smallTarget.diff(smallest, small, registry)
        assertEquals(0, observedSmall.size)

        verifyNoInteractions(mockTextTika)
    }

    @Test
    fun testDiffDoesNotCheckFileSizesWhenLimitIsNegative() {
        val smallestTarget = TextDiffInspector(-1, mockNonTextTika)
        val observedSmallest = smallestTarget.diff(small, small, registry)
        assertEquals(0, observedSmallest.size)
        verify(mockNonTextTika, atLeastOnce()).detect(anyString())
    }

    @Test
    fun testDiffChecksMimeTypesByNames() {
        val target = TextDiffInspector(DEFAULT_TEXT_SIZE_LIMIT, mockNonTextTika)
        val observed = target.diff(smallest, small, registry)
        assertTrue(observed.isEmpty())
        verify(mockNonTextTika).detect(anyString())
    }

    @Test
    fun testDiffChecksMimeTypesWhenNameSuggestsOctetStream() {
        val mockTika: Tika = mock()
        val target = TextDiffInspector(DEFAULT_TEXT_SIZE_LIMIT, mockTika)

        whenever(mockTika.detect(anyString()))
            .thenReturn("application/octet-stream")
        whenever(mockTika.detect(isA<Path>()))
            .thenReturn("image/png")

        val observed = target.diff(smallest, small, registry)
        assertTrue(observed.isEmpty())
        verify(mockTika).detect(anyString())
        verify(mockTika).detect(isA<Path>())
    }

    @Test
    fun testDiffReturnsNothingForEqualFiles() {
        val observed = defaultTarget.diff(original, original, registry)
        assertTrue(observed.isEmpty())
    }

    @Test
    fun testDiffNewLines() {
        val observed = defaultTarget.diff(original, newLines, registry)
        assertEquals(1, observed.size)
        val result = observed[0]
        assertEquals(TextDiffInspector.INSPECTION_RIGHT_EXTRA_LINES, result.details)
        assertEquals(7, result.leftStartPos)
        assertEquals(7, result.leftEndPos)
        assertEquals(7, result.rightStartPos)
        assertEquals(9, result.rightEndPos)
        assertTrue(result.rightDiff!!.contains("New lines might appear as well"))
    }

    @Test
    fun testDiffChangedLines() {
        val observed = defaultTarget.diff(original, changedLines, registry)
        assertEquals(1, observed.size)
        val result = observed[0]
        assertEquals(TextDiffInspector.INSPECTION_RIGHT_CHANGED_LINES, result.details)
        assertEquals(4, result.leftStartPos)
        assertEquals(6, result.leftEndPos)
        assertEquals(4, result.rightStartPos)
        assertEquals(6, result.rightEndPos)
        assertTrue(result.leftDiff!!.contains(", like this one"))
        assertTrue(result.rightDiff!!.contains(", at least in this form"))
    }

    @Test
    fun testDiffDeletedLines() {
        val observed = defaultTarget.diff(original, deletedLines, registry)
        assertEquals(1, observed.size)
        val result = observed[0]
        assertEquals(TextDiffInspector.INSPECTION_RIGHT_DELETED_LINES, result.details)
        assertEquals(5, result.leftStartPos)
        assertEquals(6, result.leftEndPos)
        assertEquals(5, result.rightStartPos)
        assertEquals(5, result.rightEndPos)
        assertTrue(result.leftDiff!!.contains("Some lines will only be in the original."))
    }

    private fun getResourcePath(s: String): Path {
        return with(javaClass) {
            getResource(s)?.let { Path(it.path) }!!
        }
    }
}
