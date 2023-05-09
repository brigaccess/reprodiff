import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.apache.commons.codec.digest.DigestUtils
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import kotlin.io.path.Path
import kotlin.io.path.inputStream
import kotlin.io.path.notExists
import kotlin.system.exitProcess


fun main(args: Array<String>) {
    val parser = ArgParser("reprodiff")
    val leftArg by parser.argument(ArgType.String, "left", description = "Left file to compare")
    val rightArg by parser.argument(ArgType.String, "right", description = "Right file to compare")
    val ignoreSize by parser.option(
        ArgType.Boolean, "ignore-size", description = "Do not exit if the file size mismatches"
    ).default(false)
    parser.parse(args)

    try {
        val diff = compare(leftArg, rightArg, ignoreSize)
        if (diff.isEmpty()) {
            exitProcess(0)
        }
        diff.forEach { System.err.println(it.toHumanString()) }
        exitProcess(1)
    } catch (e: FileNotFoundException) {
        System.err.println("File does not exist: ${e.message}")
    } catch (e: java.nio.file.AccessDeniedException) {
        System.err.println("Access denied: ${e.file}")
    } catch (e: IOException) {
        System.err.println("IO exception: ${e.message}")
    }
    exitProcess(2)
}

typealias InputStreamHashFunc = (x: InputStream) -> String

fun compare(
    leftArg: String,
    rightArg: String,
    ignoreSize: Boolean,
    hashFunc: InputStreamHashFunc = { DigestUtils.sha256Hex(it) }
): List<Difference> {
    val result = mutableListOf<Difference>()

    // Check whether files exist
    val paths = listOf(leftArg, rightArg).map { Path(it) }
    paths.forEach {
        if (it.notExists()) {
            throw FileNotFoundException("$it")
        }
    }

    // Check whether file sizes match (to avoid expensive hash computations)
    paths.map { it.toFile().length() }.zipWithNext { left, right ->
        if (left != right) {
            result.add(
                Difference(
                    leftArg, rightArg, "File size mismatch", leftDiff = "$left bytes", rightDiff = "$right bytes"
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
                    Difference(leftArg, rightArg, "File hash mismatch", leftDiff = left, rightDiff = right)
                )
            }
        }
    }

    return result
}