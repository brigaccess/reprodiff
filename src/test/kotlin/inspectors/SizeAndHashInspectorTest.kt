package inspectors

import DiffInspectorRegistry
import kotlinx.coroutines.runBlocking
import org.apache.commons.codec.digest.DigestUtils
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SizeAndHashInspectorTest {
    private val sha256HashFunc: InputStreamHashFunc = { DigestUtils.sha256Hex(it) }
    private val registry = DiffInspectorRegistry()

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

        assertThrows<FileNotFoundException> {
            runBlocking {
                SizeAndHashInspector(false, sha256HashFunc).diff(left, right, registry)
            }
        }
    }

    @Test
    fun testSizeMismatchingFiles(@TempDir tempDir: Path) = runBlocking {
        val files = prepareFiles(tempDir, "left", "right")

        var called = false
        val hashMock: InputStreamHashFunc = { called = true; it.toString() }

        val observed = SizeAndHashInspector(false, hashMock).diff(
            files[0],
            files[1],
            registry
        )
        assertTrue(observed.any { it.details == "File size mismatch" })
        assertFalse(called)
    }

    @Test
    fun testRespectsIgnoreSize(@TempDir tempDir: Path) = runBlocking {
        val files = prepareFiles(tempDir, "left", "right")

        var called = false
        val hashMock: InputStreamHashFunc = { called = true; it.toString() }

        val observed = SizeAndHashInspector(true, hashMock).diff(files[0], files[1], registry)
        assertTrue(observed.isNotEmpty())
        assertTrue(called)
    }

    @Test
    fun testSizeMatchingDifferentFiles(@TempDir tempDir: Path) = runBlocking {
        val files = prepareFiles(tempDir, "left", "l3ft")

        val observed = SizeAndHashInspector(false, sha256HashFunc).diff(files[0], files[1], registry)
        assertTrue(observed.size == 1)
        assertTrue(observed.any { it.details == "File hash mismatch" })
    }

    @Test
    fun testMatchingFiles(@TempDir tempDir: Path) = runBlocking {
        val files = prepareFiles(tempDir, "left", "left")
        val observed = SizeAndHashInspector(false, sha256HashFunc).diff(files[0], files[1], registry)
        assertTrue(observed.isEmpty())
    }
}