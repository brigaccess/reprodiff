package inspectors

import DEFAULT_COMPRESS_MEMORY_LIMIT
import DiffInspectorRegistry
import InspectionResult
import inspectors.CommonsCompressInspector.Companion.INSPECTION_ARCHIVE_EXTRACTED_SIZE_LIMIT_EXCEEDED
import inspectors.CommonsCompressInspector.Companion.INSPECTION_ARCHIVE_EXTRACTION_FAILED_SIZE_LIMIT_EXCEEDED
import inspectors.CommonsCompressInspector.Companion.INSPECTION_ARCHIVE_TOO_BIG_TO_ANALYZE
import inspectors.CommonsCompressInspector.Companion.INSPECTION_ARCHIVE_TYPE_MISMATCH
import inspectors.CommonsCompressInspector.Companion.INSPECTION_ARCHIVE_WRONG_ORDER
import inspectors.CommonsCompressInspector.Companion.INSPECTION_COMPRESSED_SIZE_MISMATCH
import inspectors.CommonsCompressInspector.Companion.INSPECTION_ENTRY_TYPE_MISMATCH
import inspectors.CommonsCompressInspector.Companion.INSPECTION_FILE_MISSING_FROM_LEFT
import inspectors.CommonsCompressInspector.Companion.INSPECTION_FILE_MISSING_FROM_RIGHT
import inspectors.CommonsCompressInspector.Companion.INSPECTION_PERMISSIONS_MISMATCH
import inspectors.CommonsCompressInspector.Companion.INSPECTION_TIMESTAMP_MISMATCH
import inspectors.CommonsCompressInspector.Companion.INSPECTION_UNCOMPRESSED_SIZE_MISMATCH
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class CommonsCompressInspectorTest {
    private val target = CommonsCompressInspector(DEFAULT_COMPRESS_MEMORY_LIMIT)
    private val inspectorRegistry = DiffInspectorRegistry()

    private val txt = getResourcePath("/10bytes.txt")
    private val tgz = getResourcePath("/100bytes-x3-and-10bytes-x2.tar.gz")
    private val zip = getResourcePath("/100bytes-x3-and-10bytes-x2.zip")

    init {
        inspectorRegistry.register(target)
    }

    private val commonItem1 = ArchiveEntryMetadata(
        "alpha", Path("alpha"), false, "23", 42, true
    )
    private val commonItem2 = ArchiveEntryMetadata(
        "bravo", Path("bravo"), false, "11", 248, true
    )
    private val commonItem3Left = ArchiveEntryMetadata(
        "charlie", Path("charlie-left"), true, "11", 248, true, uncompressedSize = 1234, permissions = "2"
    )
    private val commonItem3Right = ArchiveEntryMetadata(
        "charlie", Path("charlie-right"), false, "12", 249, true, uncompressedSize = 2345, permissions = "3"
    )
    private val leftOnlyItem1 = ArchiveEntryMetadata(
        "LEFT1", Path("left-1"), false, "LEFT1", 444, true
    )
    private val leftOnlyItem2 = ArchiveEntryMetadata(
        "LEFT2", Path("left-2"), false, "LEFT2", 888, true
    )
    private val rightOnlyItem1 = ArchiveEntryMetadata(
        "RIGHT1", Path("right-1"), false, "RIGHT1", 2222, true
    )
    private val rightOnlyItem2 = ArchiveEntryMetadata(
        "RIGHT2", Path("right-2"), false, "RIGHT2", 3333, true
    )
    private val commonIntersection = listOf(
        Triple(commonItem1.entryPath, commonItem1.entryPath, commonItem1.entryName),
        Triple(commonItem2.entryPath, commonItem2.entryPath, commonItem2.entryName)
    )
    private val commonIntersectionWithThree = listOf(
        Triple(commonItem1.entryPath, commonItem1.entryPath, commonItem1.entryName),
        Triple(commonItem2.entryPath, commonItem2.entryPath, commonItem2.entryName),
        Triple(commonItem3Left.entryPath, commonItem3Right.entryPath, commonItem3Left.entryName)
    )

    @Test
    fun testCompareMetadataListsCommonFirst() {
        val leftMetadata = listOf(
            commonItem1, commonItem2, leftOnlyItem1, leftOnlyItem2,
        )
        val rightMetadata = listOf(
            commonItem1, commonItem2, rightOnlyItem1, rightOnlyItem2
        )
        val (inspections, intersection) = target.compareMetadataLists(leftMetadata, rightMetadata, "left", "right")
        assertEquals(commonIntersection, intersection)
        assertEquals(4, inspections.size)
        assertMissingFilesOneAndTwo(inspections)
    }

    @Test
    fun testCompareMetadataListsCommonMiddle() {
        val leftMetadata = listOf(
            leftOnlyItem1, commonItem1, commonItem2, leftOnlyItem2,
        )
        val rightMetadata = listOf(
            rightOnlyItem1, commonItem1, commonItem2, rightOnlyItem2
        )
        val (inspections, intersection) = target.compareMetadataLists(leftMetadata, rightMetadata, "left", "right")
        assertEquals(commonIntersection, intersection)
        assertEquals(4, inspections.size)
        assertMissingFilesOneAndTwo(inspections)
    }

    @Test
    fun testCompareMetadataListsCommonLast() {
        val leftMetadata = listOf(
            leftOnlyItem1, leftOnlyItem2, commonItem1, commonItem2,
        )
        val rightMetadata = listOf(
            rightOnlyItem1, rightOnlyItem2, commonItem1, commonItem2,
        )
        val (inspections, intersection) = target.compareMetadataLists(leftMetadata, rightMetadata, "left", "right")
        assertEquals(commonIntersection, intersection)
        assertEquals(4, inspections.size)
        assertMissingFilesOneAndTwo(inspections)
    }

    @Test
    fun testCompareMetadataListsLeftSkip() {
        val leftMetadata = listOf(
            commonItem1, leftOnlyItem1, leftOnlyItem2, commonItem2
        )
        val rightMetadata = listOf(
            commonItem1, rightOnlyItem1, commonItem2, rightOnlyItem2
        )
        val (inspections, intersection) = target.compareMetadataLists(leftMetadata, rightMetadata, "left", "right")
        assertEquals(commonIntersection, intersection)
        assertEquals(4, inspections.size)
        assertMissingFilesOneAndTwo(inspections)
    }

    @Test
    fun testCompareMetadataListsRightSkip() {
        val leftMetadata = listOf(
            commonItem1, commonItem2, leftOnlyItem1, leftOnlyItem2
        )
        val rightMetadata = listOf(
            commonItem1, rightOnlyItem1, commonItem2, rightOnlyItem2
        )
        val (inspections, intersection) = target.compareMetadataLists(leftMetadata, rightMetadata, "left", "right")
        assertEquals(commonIntersection, intersection)
        assertEquals(4, inspections.size)
        assertMissingFilesOneAndTwo(inspections)
    }

    @Test
    fun testCompareMetadataListsNoIntersection() {
        val leftMetadata = listOf(
            leftOnlyItem1, leftOnlyItem2
        )
        val rightMetadata = listOf(
            rightOnlyItem1, rightOnlyItem2
        )
        val (inspections, intersection) = target.compareMetadataLists(leftMetadata, rightMetadata, "left", "right")
        assertEquals(emptyList(), intersection)
        assertEquals(4, inspections.size)
        assertMissingFilesOneAndTwo(inspections)
    }

    @Test
    fun testCompareMetadataListsHandlesMetadataMismatch() {
        val leftMetadata = listOf(
            commonItem1, leftOnlyItem1, leftOnlyItem2, commonItem2, commonItem3Left
        )
        val rightMetadata = listOf(
            commonItem1, rightOnlyItem1, commonItem2, rightOnlyItem2, commonItem3Right
        )
        val (inspections, intersection) = target.compareMetadataLists(leftMetadata, rightMetadata, "left", "right")
        assertEquals(commonIntersectionWithThree, intersection)
        assertEquals(9, inspections.size)
        assertMismatchingMetadataOfCommonThree(inspections)
        assertMissingFilesOneAndTwo(inspections)
    }

    @Test
    fun testCompareMetadataListsDetectsDuplicateEntries() {
        val leftMetadata = listOf(
            commonItem1, commonItem1
        )
        val rightMetadata = listOf(
            commonItem1, commonItem1
        )
        val (inspections, intersection) = target.compareMetadataLists(leftMetadata, rightMetadata, "left", "right")
        assertEquals(listOf(Triple(commonItem1.entryPath, commonItem1.entryPath, commonItem1.entryName)), intersection)
        assertTrue(inspections.all { it.details == "Duplicate entries found" })
    }

    @Test
    fun testCompareMetadataListsDetectsWrongOrder() {
        val leftMetadata = listOf(
            commonItem1, commonItem2
        )
        val rightMetadata = listOf(
            commonItem2, commonItem1
        )
        val (inspections, intersection) = target.compareMetadataLists(leftMetadata, rightMetadata, "left", "right")
        assertEquals(commonIntersection.toSet(), intersection.toSet())
        assertEquals(1, inspections.size)
        assertTrue(inspections.all { it.details == INSPECTION_ARCHIVE_WRONG_ORDER })
    }

    @Test
    fun testCompareMetadataListsProperlyInspectsItemsInWrongOrder() {
        val leftMetadata = listOf(
            commonItem1, commonItem3Left, commonItem2
        )
        val rightMetadata = listOf(
            commonItem2, commonItem1, commonItem3Right
        )
        val (inspections, intersection) = target.compareMetadataLists(leftMetadata, rightMetadata, "left", "right")
        assertEquals(commonIntersectionWithThree.toSet(), intersection.toSet())
        assertEquals(6, inspections.size)
        assertEquals(
            1, inspections.filter { it.details == INSPECTION_ARCHIVE_WRONG_ORDER }.size
        )
        assertMismatchingMetadataOfCommonThree(inspections)
    }

    @Test
    fun testCompareMetadataListsLeftIsLonger() {
        val leftMetadata = listOf(
            commonItem1, commonItem2, leftOnlyItem1
        )
        val rightMetadata = listOf(
            commonItem1, commonItem2
        )
        val (inspections, intersection) = target.compareMetadataLists(leftMetadata, rightMetadata, "left", "right")
        assertEquals(commonIntersection, intersection)
        assertTrue(inspections.all { it.details == INSPECTION_FILE_MISSING_FROM_RIGHT })
    }

    @Test
    fun testCompareMetadataListsRightIsLonger() {
        val leftMetadata = listOf(
            commonItem1, commonItem2
        )
        val rightMetadata = listOf(
            commonItem1, commonItem2, rightOnlyItem1
        )
        val (inspections, intersection) = target.compareMetadataLists(leftMetadata, rightMetadata, "left", "right")
        assertEquals(commonIntersection, intersection)
        assertTrue(inspections.all { it.details == INSPECTION_FILE_MISSING_FROM_LEFT })
    }

    @Test
    fun testDiffFailsForDifferentArchiveTypes() = runBlocking {
        val observed = target.diff(zip, tgz, inspectorRegistry)
        assertEquals(1, observed.size)
        val result = observed[0]
        assertEquals(INSPECTION_ARCHIVE_TYPE_MISMATCH, result.details)
        assertEquals("zip", result.leftDiff)
        assertEquals("tar", result.rightDiff)
    }

    @Test
    fun testDiffFailsForNonArchives() = runBlocking {
        assertTrue(target.diff(txt, zip, inspectorRegistry).isEmpty())
        assertTrue(target.diff(zip, txt, inspectorRegistry).isEmpty())
    }

    @Test
    fun testDiffChecksSizeLimits() = runBlocking {
        val smallTarget = CommonsCompressInspector(DEFAULT_COMPRESS_MEMORY_LIMIT, 1)
        val observed = smallTarget.diff(zip, zip, inspectorRegistry)
        assertTrue(observed.isNotEmpty())
        assertTrue(observed.all { it.details == INSPECTION_ARCHIVE_TOO_BIG_TO_ANALYZE })
    }

    @Test
    fun testDiffDoesNotLimitSizeWhenLimitIsNegative() = runBlocking  {
        val unlimitedTarget = CommonsCompressInspector(DEFAULT_COMPRESS_MEMORY_LIMIT, -1)
        val observed = unlimitedTarget.diff(zip, zip, inspectorRegistry)
        assertTrue(observed.isEmpty())
    }

    @Test
    fun testDiffChecksExtractionLimits(): Unit = runBlocking {
        // We expect that the target will:
        // - unpack `100bytes-1.txt` successfully;
        // - fail for `100bytes-2.txt` with EXTRACTION_FAILED
        // - unpack `10bytes-1.txt` (since the budget will have 10 bytes left)
        // - fail for `10bytes-2.txt` with EXTRACTED_SIZE_LIMIT_EXCEEDED
        val limitedTarget = CommonsCompressInspector(DEFAULT_COMPRESS_MEMORY_LIMIT, -1, 110)

        // This will return two entries because we use the same file on both sides
        val observed = limitedTarget.diff(zip, zip, inspectorRegistry)
        assertEquals(4, observed.size)
        observed.map { Pair(it.details, it.leftDiff!!) }.forEach {
            val (details, fileName) = it
            when (details) {
                INSPECTION_ARCHIVE_EXTRACTION_FAILED_SIZE_LIMIT_EXCEEDED -> assertTrue(fileName.endsWith("100bytes-2.txt"))
                INSPECTION_ARCHIVE_EXTRACTED_SIZE_LIMIT_EXCEEDED -> assertTrue(fileName.endsWith("10bytes-2.txt"))
                else -> fail("Unexpected inspection result: $details")
            }
        }
    }

    @Test
    fun testDiffReturnsNoResultsForTheSameFile(): Unit = runBlocking {
        val observed = target.diff(zip, zip, inspectorRegistry)
        assertTrue(observed.isEmpty())
    }

    /**
     * Asserts 4 inspections about missing files
     */
    private fun assertMissingFilesOneAndTwo(inspections: List<InspectionResult>) {
        assertTrue(inspections.any { it.details == INSPECTION_FILE_MISSING_FROM_RIGHT && it.leftDiff == leftOnlyItem1.entryName })
        assertTrue(inspections.any { it.details == INSPECTION_FILE_MISSING_FROM_RIGHT && it.leftDiff == leftOnlyItem2.entryName })
        assertTrue(inspections.any { it.details == INSPECTION_FILE_MISSING_FROM_LEFT && it.rightDiff == rightOnlyItem1.entryName })
        assertTrue(inspections.any { it.details == INSPECTION_FILE_MISSING_FROM_LEFT && it.rightDiff == rightOnlyItem2.entryName })
    }

    /**
     * Asserts 5 inspections about the metadata
     */
    private fun assertMismatchingMetadataOfCommonThree(inspections: List<InspectionResult>) {
        assertTrue(inspections.any {
            it.details == INSPECTION_ENTRY_TYPE_MISMATCH && it.leftName.endsWith(commonItem3Left.entryName) && it.rightName!!.endsWith(
                commonItem3Left.entryName
            ) && it.leftDiff != it.rightDiff
        })
        assertTrue(inspections.any {
            it.details == INSPECTION_TIMESTAMP_MISMATCH && it.leftName.endsWith(commonItem3Left.entryName) && it.rightName!!.endsWith(
                commonItem3Left.entryName
            ) && it.leftDiff == commonItem3Left.timestamp && it.rightDiff == commonItem3Right.timestamp
        })
        assertTrue(inspections.any {
            it.details == INSPECTION_COMPRESSED_SIZE_MISMATCH && it.leftName.endsWith(commonItem3Left.entryName) && it.rightName!!.endsWith(
                commonItem3Left.entryName
            ) && it.leftDiff!!.startsWith(
                "${commonItem3Left.compressedSize}"
            ) && it.rightDiff!!.startsWith("${commonItem3Right.compressedSize}")
        })
        assertTrue(inspections.any {
            it.details == INSPECTION_UNCOMPRESSED_SIZE_MISMATCH && it.leftName.endsWith(commonItem3Left.entryName) && it.rightName!!.endsWith(
                commonItem3Left.entryName
            ) && it.leftDiff!!.startsWith(
                "${commonItem3Left.uncompressedSize}"
            ) && it.rightDiff!!.startsWith("${commonItem3Right.uncompressedSize}")
        })
        assertTrue(inspections.any {
            it.details == INSPECTION_PERMISSIONS_MISMATCH && it.leftName.endsWith(commonItem3Left.entryName) && it.rightName!!.endsWith(
                commonItem3Left.entryName
            ) && it.leftDiff!! == commonItem3Left.permissions && it.rightDiff!! == commonItem3Right.permissions
        })
    }

    private fun getResourcePath(s: String): Path {
        return with(javaClass) {
            getResource(s)?.let { Path(it.path) }!!
        }
    }
}