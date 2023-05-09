import inspectors.DiffInspectorRegistry
import inspectors.SizeAndHashInspector
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

    val registry = DiffInspectorRegistry()
    registry.register(SizeAndHashInspector(ignoreSize) { DigestUtils.sha256Hex(it) })

    try {
        val diff = registry.inspectFiles(Path(leftArg), Path(rightArg))
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