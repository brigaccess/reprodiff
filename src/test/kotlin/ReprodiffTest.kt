import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReprodiffTest {
    private fun prepareFiles(tempDir: Path, left: String, right: String): List<Path> {
        val files = listOf(left, right).map { tempDir.resolve(it) }
        files.forEach { Files.write(it, listOf(it.toString())) }
        return files
    }

    @Test
    fun testNonExistentFile(@TempDir tempDir: Path) {
        val left = tempDir.resolve("left")
        val right = tempDir.resolve("right")
        Files.write(right, listOf("Yay!"))

        val observed = compare(left.toString(), right.toString(), false)
        assertEquals(2, observed)
    }

    @Test
    fun testSizeMismatchingFiles(@TempDir tempDir: Path) {
        val files = prepareFiles(tempDir, "left", "right")

        var called = false
        val hashMock: InputStreamHashFunc = { called = true; it.toString() }

        val observed = compare(files[0].toString(), files[1].toString(), false, hashFunc = hashMock)
        assertEquals(1, observed)
        assertFalse(called)
    }

    @Test
    fun testRespectsIgnoreSize(@TempDir tempDir: Path) {
        val files = prepareFiles(tempDir, "left", "right")

        var called = false
        val hashMock: InputStreamHashFunc = { called = true; it.toString() }

        val observed = compare(files[0].toString(), files[1].toString(), true, hashFunc = hashMock)
        assertEquals(1, observed)
        assertTrue(called)
    }

    @Test
    fun testSizeMatchingDifferentFiles(@TempDir tempDir: Path) {
        val files = prepareFiles(tempDir, "left", "l3ft")

        val observed = compare(files[0].toString(), files[1].toString(), false)
        assertEquals(1, observed)
    }

    @Test
    fun testMatchingFiles(@TempDir tempDir: Path) {
        val files = prepareFiles(tempDir, "left", "left")
        val observed = compare(files[0].toString(), files[1].toString(), false)
        assertEquals(0, observed)
    }
}