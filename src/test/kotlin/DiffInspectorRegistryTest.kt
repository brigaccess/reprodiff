import DiffInspectorRegistry.Companion.INSPECTION_DEPTH_EXCEEDED
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import kotlin.io.path.Path

class DiffInspectorRegistryTest {
    private val leftPath = Path("left")
    private val rightPath = Path("right")
    private val leftName = "humanLeft"
    private val rightName = "humanRight"

    @Test
    fun testDiffInspectorRegistryRegistersAndCallsInspectors() {
        val mockInspector: DiffInspector = mock()
        val target = DiffInspectorRegistry()

        val expectedResults = listOf(
            InspectionResult("First", "name"),
            InspectionResult("Second", "name")
        )

        whenever(mockInspector.diff(any(), any(), eq(target), any(), any(), any(), any()))
            .thenReturn(expectedResults)

        target.register(mockInspector)
        val observed = target.inspectFiles(leftPath, rightPath, leftName = leftName, rightName = rightName)

        verify(mockInspector).diff(eq(leftPath), eq(rightPath), eq(target), any(), any(), eq(leftName), eq(rightName))
        assertEquals(expectedResults, observed)
    }

    @Test
    fun testDiffInspectorRegistryReturnsResultsForMultipleInspectors() {
        val target = DiffInspectorRegistry()
        val mockInspectorOne: DiffInspector = mock()
        val mockInspectorTwo: DiffInspector = mock()

        val resultOne = InspectionResult("First", "name")
        val resultTwo = InspectionResult("Second", "name")

        val expectedResults = listOf(resultOne, resultTwo)

        listOf(
            Pair(mockInspectorOne, resultOne),
            Pair(mockInspectorTwo, resultTwo)
        ).forEach {
            whenever(it.first.diff(any(), any(), eq(target), any(), any(), any(), any()))
                .thenReturn(listOf(it.second))
            target.register(it.first)
        }
        val observed = target.inspectFiles(leftPath, rightPath, leftName = leftName, rightName = rightName)

        listOf(mockInspectorOne, mockInspectorTwo).forEach {
            verify(it).diff(eq(leftPath), eq(rightPath), eq(target), any(), any(), eq(leftName), eq(rightName))
        }
        assertEquals(expectedResults, observed)
    }

    @Test
    fun testDiffInspectorRegistryFailsOnDepthExceeded() {
        val mockInspector: DiffInspector = mock()
        val target = DiffInspectorRegistry()

        whenever(mockInspector.diff(any(), any(), eq(target), any(), any(), any(), any()))
            .thenReturn(emptyList())

        target.register(mockInspector)
        val observed = target.inspectFiles(leftPath, rightPath, 10, 5, leftName = leftName, rightName = rightName)

        verifyNoInteractions(mockInspector)
        assertEquals(1, observed.size)
        assertEquals(INSPECTION_DEPTH_EXCEEDED, observed[0].details)
    }
}