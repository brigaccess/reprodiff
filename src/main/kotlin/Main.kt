import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.apache.commons.codec.digest.DigestUtils
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

    exitProcess(compare(leftArg, rightArg, ignoreSize))
}

fun compare(leftArg: String, rightArg: String, ignoreSize: Boolean): Int {
    // Check whether files exist
    val paths = listOf(leftArg, rightArg).map { Path(it) }
    paths.forEach {
        if (it.notExists()) {
            System.err.println("File '$it' does not exist")
            return 2
        }
    }

    // Check whether file sizes match (to avoid expensive hash computations)
    paths.map { it.toFile().length() }.zipWithNext { left, right ->
        if (left != right) {
            System.err.println("File size mismatch: $left bytes ${if (left < right) "<" else ">"} $right bytes")
            if (!ignoreSize) {
                return 1
            }
        }
    }

    // Calculate whether secure hashes match
    var failed = false
    runBlocking {
        paths.map {
            async(Dispatchers.Default) {
                DigestUtils.sha256Hex(it.inputStream())
            }
        }.map { it.await() }.zipWithNext { left, right ->
            val mismatch = left != right
            if (mismatch) {
                System.err.println("File hash mismatch: $left != $right")
            }
            failed = failed || mismatch
        }
    }

    return if (failed) 1 else 0
}
