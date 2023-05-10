import inspectors.CommonsCompressInspector
import inspectors.DiffInspectorRegistry
import inspectors.SizeAndHashInspector
import inspectors.TextDiffInspector
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.ParsingException
import kotlinx.cli.default
import org.apache.commons.codec.digest.DigestUtils
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.io.path.Path
import kotlin.system.exitProcess

object ArgTypeLong : ArgType<kotlin.Long>(true) {
    override val description: kotlin.String
        get() = "{ Long }"

    override fun convert(value: kotlin.String, name: kotlin.String): kotlin.Long =
        value.toLongOrNull()
            ?: throw ParsingException("Option $name is expected to be long integer number. $value is provided.")
}

const val DEFAULT_COMPRESS_MEMORY_LIMIT = 10240000
const val DEFAULT_ARCHIVE_SIZE_LIMIT = 256000000L
const val DEFAULT_TOTAL_EXTRACTED_SIZE_LIMIT = 512000000L
const val DEFAULT_TEXT_SIZE_LIMIT = 262144L

fun main(args: Array<String>) {
    val parser = ArgParser("reprodiff")
    val leftArg by parser.argument(ArgType.String, "left", description = "Left file to compare")
    val rightArg by parser.argument(ArgType.String, "right", description = "Right file to compare")
    val ignoreSize by parser.option(
        ArgType.Boolean, "ignore-size", description = "Do not exit if the file size mismatches"
    ).default(false)
    val maxDepth by parser.option(
        ArgType.Int, "max-depth", description = "Maximum depth of recursive analysis"
    ).default(3)
    val compressMemoryLimit by parser.option(
        ArgType.Int, "archive-compress-memlimit", description = "Memory limit for in-memory archive operations (in bytes)"
    ).default(DEFAULT_COMPRESS_MEMORY_LIMIT)
    val archiveSizeLimit by parser.option(
        ArgTypeLong, "archive-max-size", description = "Limit for the size of analyzed archives (in bytes)"
    ).default(DEFAULT_ARCHIVE_SIZE_LIMIT)
    val extractedSizeLimit by parser.option(
        ArgTypeLong, "archive-max-extracted-size", description = "Limit for the total size of files extracted from the archive (in bytes)"
    ).default(DEFAULT_TOTAL_EXTRACTED_SIZE_LIMIT)
    val textMaxSize by parser.option(
        ArgTypeLong, "text-max-size", description = "Limit for the size of text files to perform diffs to (in bytes)"
    ).default(DEFAULT_TEXT_SIZE_LIMIT)
    val debug by parser.option(
        ArgType.Boolean, "debug", description = "Enables debug logging"
    ).default(false)
    parser.parse(args)

    if (debug) {
        System.setProperty("log.level", "debug")
    }

    val registry = DiffInspectorRegistry()
    with(registry) {
        register(SizeAndHashInspector(ignoreSize) { DigestUtils.sha256Hex(it) })
        register(CommonsCompressInspector(
            memoryLimitKb = compressMemoryLimit,
            archiveSizeLimit = archiveSizeLimit,
            totalExtractedSizeLimit = extractedSizeLimit
        ))
        register(TextDiffInspector(textMaxSize))
    }

    try {
        val diff = registry.inspectFiles(Path(leftArg), Path(rightArg), maxDepth = maxDepth)
        if (diff.isEmpty()) {
            exitProcess(0)
        }
        diff.forEach { System.err.println("${it.toHumanString()}\n") }
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