import inspectors.CommonsCompressInspector
import inspectors.DiffInspectorRegistry
import inspectors.SizeAndHashInspector
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
        ArgType.Int, "compress-memlimit", description = "Memory limit for in-memory archive operations (in bytes)"
    )
    val archiveSizeLimit by parser.option(
        ArgTypeLong, "max-archive-size", description = "Limit for the size of analyzed archives (in bytes)"
    )
    val extractedSizeLimit by parser.option(
        ArgTypeLong, "max-extracted-size", description = "Limit for the total size of files extracted from the archive (in bytes)"
    )
    parser.parse(args)

    val registry = DiffInspectorRegistry()
    registry.register(SizeAndHashInspector(ignoreSize) { DigestUtils.sha256Hex(it) })
    registry.register(CommonsCompressInspector(
        memoryLimitKb = compressMemoryLimit ?: 10240000,
        archiveSizeLimit = archiveSizeLimit,
        totalExtractedSizeLimit = extractedSizeLimit
    ))

    try {
        val diff = registry.inspectFiles(Path(leftArg), Path(rightArg), maxDepth = maxDepth)
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