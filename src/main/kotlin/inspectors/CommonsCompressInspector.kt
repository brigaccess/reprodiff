package inspectors

import DEFAULT_ARCHIVE_SIZE_LIMIT
import DEFAULT_TOTAL_EXTRACTED_SIZE_LIMIT
import InspectionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveException
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.compressors.CompressorException
import org.apache.commons.compress.compressors.CompressorStreamFactory
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.*
import kotlin.io.path.deleteIfExists
import kotlin.io.path.fileSize

class SizeLimitExceeded(written: Long) : Exception("$written")

data class ArchiveEntryMetadata(
    val entryName: String,
    val isDirectory: Boolean,
    val timestamp: String,
    val compressedSize: Long,
    var extracted: Boolean = false,
    var uncompressedSize: Long? = null,
    val permissions: String? = null,
) {
    var entryPath: Path? = null
    var inspectionResult: InspectionResult? = null

    constructor(
        entryName: String,
        entryPath: Path? = null,
        isDirectory: Boolean,
        timestamp: String,
        compressedSize: Long,
        extracted: Boolean = false,
        uncompressedSize: Long? = null,
        permissions: String? = null,
        inspectionResult: InspectionResult? = null,
    ) : this(
        entryName, isDirectory, timestamp, compressedSize, extracted, uncompressedSize, permissions
    ) {
        this.entryPath = entryPath
        this.inspectionResult = inspectionResult
    }

    companion object {
        /**
         * Generates [ArchiveEntryMetadata] from [ZipArchiveEntry].
         */
        fun fromZip(e: ZipArchiveEntry): ArchiveEntryMetadata {
            return ArchiveEntryMetadata(
                e.name,
                null,
                e.isDirectory,
                e.lastModifiedTime.toString(),
                e.compressedSize,
                false,
                e.size,
                e.unixMode.toString()
            )
        }

        /**
         * Generates [ArchiveEntryMetadata] from any child of [ArchiveEntry].
         */
        fun fromEntry(entry: ArchiveEntry): ArchiveEntryMetadata {
            return when (entry) {
                is ZipArchiveEntry -> fromZip(entry)
                else -> ArchiveEntryMetadata(
                    entry.name, null, entry.isDirectory, entry.lastModifiedDate.toString(), -1, false, entry.size
                )
            }
        }
    }
}

/**
 * Uses Apache Commons Compress to inspect supported archives
 */
class CommonsCompressInspector(
    memoryLimitKb: Int,
    private val archiveSizeLimit: Long = DEFAULT_ARCHIVE_SIZE_LIMIT,
    private val totalExtractedSizeLimit: Long = DEFAULT_TOTAL_EXTRACTED_SIZE_LIMIT,
    private val tempRootDir: Path? = null,
) : DiffInspector {
    private val csf = CompressorStreamFactory(false, memoryLimitKb)

    override fun diff(
        left: Path,
        right: Path,
        registry: DiffInspectorRegistry,
        depth: Int,
        maxDepth: Int,
        leftName: String?,
        rightName: String?
    ): List<InspectionResult> {
        val leftHumanName = leftName ?: left.fileName.toString()
        val rightHumanName = rightName ?: right.fileName.toString()

        // Check the archive types first
        val (leftType, rightType) = detectArchiveTypes(left, right)
        if (leftType.isBlank() || rightType.isBlank()) {
            // If type detection failed, assume that one of the files is not a supported archive
            return emptyList()
        } else if (leftType != rightType) {
            return listOf(
                InspectionResult(
                    INSPECTION_ARCHIVE_TYPE_MISMATCH,
                    leftHumanName,
                    rightHumanName,
                    leftDiff = leftType,
                    rightDiff = rightType
                )
            )
        }

        val result = mutableListOf<InspectionResult>()
        val pathsAndNames = listOf(Pair(left, leftHumanName), Pair(right, rightHumanName))

        // Assuming that this is the first inspection we're actually performing,
        // we should short-circuit in case the result list is not empty.
        result.addAll(inspectArchiveSize(pathsAndNames))
        if (result.isNotEmpty()) {
            return result
        }

        // Extract files and analyze them recursively
        result.addAll(runBlocking {
            pathsAndNames.map {
                val (path, humanName) = it
                async(Dispatchers.IO) { extractArchiveFilesFlat(path, humanName) }
            }.map { it.await() }.zipWithNext().map {
                val (metadataInspectionResults, intersection) = compareMetadataLists(
                    it.first,
                    it.second,
                    leftHumanName,
                    rightHumanName
                )
                result.addAll(metadataInspectionResults)
                intersection.map { toInspect ->
                    val (leftPath, rightPath, name) = toInspect
                    // TODO This should be parallelized too?
                    registry.inspectFiles(
                        leftPath, rightPath, depth + 1, maxDepth, "$leftHumanName#/$name", "$rightHumanName#/$name"
                    )
                }
            }.flatten().flatten()
        })
        return result
    }

    private fun detectArchiveTypes(left: Path, right: Path): Pair<String, String> {
        val result = runBlocking {
            listOf(left, right).map {
                async(Dispatchers.IO) {
                    try {
                        ArchiveStreamFactory.detect(provideInputStream(it))
                    } catch (e: ArchiveException) {
                        ""
                    }
                }
            }.map { it.await() }
        }
        return Pair(result[0], result[1])
    }

    private fun inspectArchiveSize(
        paths: List<Pair<Path, String>>
    ): List<InspectionResult> {
        return paths.mapNotNull {
            val size = it.first.fileSize()
            if (archiveSizeLimit in 1 until size) {
                InspectionResult(
                    INSPECTION_ARCHIVE_TOO_BIG_TO_ANALYZE,
                    it.second,
                    "limit",
                    leftDiff = "$size bytes",
                    rightDiff = "$archiveSizeLimit bytes"
                )
            } else null
        }
    }

    /**
     * Extracts the files from the archive to a temporary folder, omitting the hierarchy
     * and respecting the total extracted size limit (if positive).
     *
     * Returns a list of [ArchiveEntryMetadata] for each file and folder that was found
     * in the archive, regardless of whether it was extracted or not (to compare archive
     * structures later).
     */
    private fun extractArchiveFilesFlat(p: Path, humanName: String): List<ArchiveEntryMetadata> {
        // TODO Testability
        val ais = ArchiveStreamFactory().createArchiveInputStream(provideInputStream(p))

        val rootFolderPrefix = "reprodiff-${Instant.now().epochSecond}-"
        val rootFolder = if (tempRootDir != null) {
            Files.createTempDirectory(tempRootDir, rootFolderPrefix)
        } else {
            Files.createTempDirectory(rootFolderPrefix)
        }
        rootFolder.toFile().deleteOnExit()

        val sizeBudget = totalExtractedSizeLimit
        var remainingBudget: Long = sizeBudget
        val useSizeLimit = remainingBudget > 0

        val entries = mutableListOf<ArchiveEntryMetadata>()

        while (true) {
            val archiveEntry: ArchiveEntry? = ais.nextEntry
            archiveEntry ?: break
            // To prevent possible ZipSlips, unsupported file names and so on,
            // all the files will get the random UUIDs instead. These are not
            // for humans to investigate anyway, humans have archivers.
            val uuid = UUID.randomUUID().toString()

            val metadata = ArchiveEntryMetadata.fromEntry(archiveEntry)

            // We're only extracting files, but we also need to keep track of directories
            if (metadata.isDirectory) {
                entries.add(metadata)
                continue
            }

            // TODO Might be a good idea to retain the extensions?
            val path: Path = rootFolder.resolve(uuid)

            // Try to extract while also respecting the size limits (if necessary)
            val extracted: Boolean
            if (useSizeLimit && remainingBudget <= 0) {
                extracted = false
                metadata.inspectionResult = InspectionResult(
                    INSPECTION_ARCHIVE_EXTRACTED_SIZE_LIMIT_EXCEEDED,
                    humanName,
                    suffix = "(more than $sizeBudget bytes written)"
                )
            } else {
                extracted = try {
                    val fos = FileOutputStream(path.toFile())
                    fos.use {
                        val count = copyWithLimit(ais, fos, remainingBudget)
                        if (useSizeLimit) {
                            remainingBudget -= count
                        }
                    }
                    path.toFile().deleteOnExit()
                    true
                } catch (e: IOException) {
                    // TODO Log the error
                    false
                } catch (e: SizeLimitExceeded) {
                    metadata.inspectionResult = InspectionResult(
                        INSPECTION_ARCHIVE_EXTRACTION_FAILED_SIZE_LIMIT_EXCEEDED,
                        "$humanName#/${metadata.entryName}",
                        suffix = "(more than $sizeBudget bytes written)"
                    )
                    path.deleteIfExists()
                    false
                }
            }

            metadata.extracted = extracted
            if (extracted) {
                metadata.entryPath = path
            }
            entries.add(metadata)
        }
        return entries
    }

    /**
     * Processes two metadata lists from different archives. Returns:
     * - a list of inspection results
     * - a list of files to analyze recursively
     */
    internal fun compareMetadataLists(
        left: List<ArchiveEntryMetadata>,
        right: List<ArchiveEntryMetadata>,
        leftArchiveName: String,
        rightArchiveName: String
    ): Pair<List<InspectionResult>, List<Triple<Path, Path, String>>> {
        val result = mutableListOf<InspectionResult>()

        val listToMapExtractInspectionResults = { x: ArchiveEntryMetadata ->
            val inspectionResult = x.inspectionResult
            if (inspectionResult != null) {
                result.add(inspectionResult)
            }

            x.entryName to x
        }

        val resultIntersection = listOf(left, right).map {
            it.associate { x -> listToMapExtractInspectionResults(x) }
        }.zipWithNext().map {
            val (leftMap, rightMap) = it

            // Build an intersection while also doing other stuff
            val intersection = mutableSetOf<Triple<Path, Path, String>>()
            val addToIntersection = { l: ArchiveEntryMetadata, r: ArchiveEntryMetadata ->
                val lp = l.entryPath
                val rp = r.entryPath
                if (l.extracted && r.extracted && lp != null && rp != null) {
                    intersection.add(Triple(lp, rp, l.entryName))
                }
                // TODO Debug logging?
            }

            val leftOnly = mutableListOf<ArchiveEntryMetadata>()
            val rightOnly = mutableListOf<ArchiveEntryMetadata>()

            val leftIterator = left.iterator()
            val rightIterator = right.iterator()
            val handledWrongOrder = mutableSetOf<String>()
            var leftItem: ArchiveEntryMetadata? = null
            var rightItem: ArchiveEntryMetadata? = null
            var skipped = false

            do {
                if (!skipped) {
                    leftItem = if (leftIterator.hasNext()) leftIterator.next() else null
                    rightItem = if (rightIterator.hasNext()) rightIterator.next() else null
                } else {
                    skipped = false
                }


                if (leftItem != null && rightItem != null) {
                    // Simple case: everything important is equal. Nothing to do here.
                    if (leftItem == rightItem) {
                        addToIntersection(leftItem, rightItem)
                        continue
                    }

                    // Names are equal but metadata is not
                    if (leftItem.entryName == rightItem.entryName) {
                        result.addAll(diffMetadata(leftItem, rightItem, leftArchiveName, rightArchiveName))
                        addToIntersection(leftItem, rightItem)
                        continue
                    }

                    // Names are not equal: either left or right has extra elements that we need to skip
                    while (leftItem != null && !rightMap.containsKey(leftItem.entryName)) {
                        skipped = true
                        leftOnly.add(leftItem)
                        leftItem = if (leftIterator.hasNext()) leftIterator.next() else null
                    }

                    while (rightItem != null && !leftMap.containsKey(rightItem.entryName)) {
                        skipped = true
                        rightOnly.add(rightItem)
                        rightItem = if (rightIterator.hasNext()) rightIterator.next() else null
                    }

                    if (skipped) {
                        continue
                    }

                    // Never should have NPE here
                    leftItem!!
                    rightItem!!

                    // Names are not equal: check if it is the ordering issue
                    val leftContains =
                        leftMap.containsKey(rightItem.entryName) && !handledWrongOrder.contains(rightItem.entryName)
                    val rightContains =
                        rightMap.containsKey(leftItem.entryName) && !handledWrongOrder.contains(leftItem.entryName)
                    if (leftContains || rightContains) {
                        // No need to spam with "wrong order" messages.
                        if (handledWrongOrder.isEmpty()) {
                            result.add(
                                InspectionResult(
                                    INSPECTION_ARCHIVE_WRONG_ORDER,
                                    leftArchiveName,
                                    rightArchiveName,
                                    leftDiff = leftItem.entryName,
                                    rightDiff = rightItem.entryName
                                )
                            )
                        }

                        if (leftContains) {
                            handledWrongOrder.add(rightItem.entryName)
                            val e = leftMap[rightItem.entryName]!!
                            result.addAll(diffMetadata(e, rightItem, leftArchiveName, rightArchiveName))
                            addToIntersection(e, rightItem)
                        }

                        if (rightContains) {
                            handledWrongOrder.add(leftItem.entryName)
                            val e = rightMap[leftItem.entryName]!!
                            result.addAll(diffMetadata(leftItem, e, leftArchiveName, rightArchiveName))
                            // This is intentional: we always add the left item to the intersection
                            addToIntersection(leftItem, e)
                        }
                    }
                } else if (leftItem != null && rightItem == null) {
                    // We reached the end of the right list
                    leftOnly.add(leftItem)
                } else if (rightItem != null) {
                    // We reached the end of the left list
                    rightOnly.add(rightItem)
                }
            } while (leftItem != null || rightItem != null)

            leftOnly.forEach { metadata ->
                result.add(
                    InspectionResult(
                        INSPECTION_FILE_MISSING_FROM_RIGHT,
                        leftArchiveName,
                        rightArchiveName,
                        leftDiff = metadata.entryName,
                        rightDiff = "N/A"
                    )
                )
            }

            rightOnly.forEach { metadata ->
                result.add(
                    InspectionResult(
                        INSPECTION_FILE_MISSING_FROM_LEFT,
                        leftArchiveName,
                        rightArchiveName,
                        leftDiff = "N/A",
                        rightDiff = metadata.entryName
                    )
                )
            }

            // Check for duplicate entries
            if (left.size > leftMap.size) {
                result.add(
                    InspectionResult(
                        INSPECTION_DUPLICATE_ENTRIES_FOUND, leftArchiveName
                    )
                )
            }

            if (right.size > rightMap.size) {
                result.add(
                    InspectionResult(
                        INSPECTION_DUPLICATE_ENTRIES_FOUND, rightArchiveName
                    )
                )
            }

            intersection
        }.flatten()

        return Pair(result, resultIntersection)
    }

    private fun diffMetadata(
        left: ArchiveEntryMetadata, right: ArchiveEntryMetadata, leftArchiveName: String, rightArchiveName: String
    ): List<InspectionResult> {
        val result = mutableListOf<InspectionResult>()
        if (left.entryName != right.entryName) {
            throw IllegalArgumentException("Tried to diff metadata of files with different names")
        }
        val leftEntryName = "$leftArchiveName#/${left.entryName}"
        val rightEntryName = "$rightArchiveName#/${right.entryName}"

        if (left.isDirectory != right.isDirectory) {
            result.add(
                InspectionResult(
                    INSPECTION_ENTRY_TYPE_MISMATCH,
                    leftEntryName,
                    rightEntryName,
                    leftDiff = if (left.isDirectory) "directory" else "file",
                    rightDiff = if (right.isDirectory) "directory" else "file"
                )
            )
        }

        if (left.timestamp != right.timestamp) {
            result.add(
                InspectionResult(
                    INSPECTION_TIMESTAMP_MISMATCH,
                    leftEntryName,
                    rightEntryName,
                    leftDiff = left.timestamp,
                    rightDiff = right.timestamp
                )
            )
        }

        if (left.compressedSize != right.compressedSize) {
            result.add(
                InspectionResult(
                    INSPECTION_COMPRESSED_SIZE_MISMATCH,
                    leftEntryName,
                    rightEntryName,
                    leftDiff = "${left.compressedSize} bytes",
                    rightDiff = "${right.compressedSize} bytes",
                )
            )
        }

        if (left.uncompressedSize != right.uncompressedSize) {
            result.add(
                InspectionResult(
                    INSPECTION_UNCOMPRESSED_SIZE_MISMATCH,
                    leftEntryName,
                    rightEntryName,
                    leftDiff = "${left.uncompressedSize} bytes",
                    rightDiff = "${right.uncompressedSize} bytes",
                )
            )
        }

        if (left.permissions != right.permissions) {
            result.add(
                InspectionResult(
                    INSPECTION_PERMISSIONS_MISMATCH,
                    leftEntryName,
                    rightEntryName,
                    leftDiff = "${left.permissions}",
                    rightDiff = "${right.permissions}",
                )
            )
        }

        return result
    }

    private fun provideInputStream(p: Path): InputStream {
        val stream = BufferedInputStream(Files.newInputStream(p))
        return try {
            BufferedInputStream(
                csf.createCompressorInputStream(stream)
            )
        } catch (e: CompressorException) {
            stream
        }
    }

    @Throws(IOException::class, SizeLimitExceeded::class)
    private fun copyWithLimit(input: InputStream, output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(8192)
        var n: Int
        var count: Long = 0
        while (-1 != input.read(buffer).also { n = it }) {
            output.write(buffer, 0, n)
            count += n.toLong()
            if (limit in 0 until count) {
                throw SizeLimitExceeded(count)
            }
        }
        return count
    }

    companion object {
        const val INSPECTION_ARCHIVE_EXTRACTED_SIZE_LIMIT_EXCEEDED = "Archive extracted files size limit exceeded"
        const val INSPECTION_ARCHIVE_EXTRACTION_FAILED_SIZE_LIMIT_EXCEEDED =
            "Archive extracted files size limit exceeded while extracting this file"
        const val INSPECTION_ARCHIVE_TOO_BIG_TO_ANALYZE = "Archive size exceeds the analysis limit"
        const val INSPECTION_ARCHIVE_TYPE_MISMATCH = "Archive type mismatch"
        const val INSPECTION_ARCHIVE_WRONG_ORDER = "Archive file order differs. Expected equal paths, but got"
        const val INSPECTION_COMPRESSED_SIZE_MISMATCH = "Mismatched compressed sizes"
        const val INSPECTION_DUPLICATE_ENTRIES_FOUND = "Duplicate entries found"
        const val INSPECTION_ENTRY_TYPE_MISMATCH = "Mismatched entry types"
        const val INSPECTION_FILE_MISSING_FROM_LEFT = "File missing from the left archive"
        const val INSPECTION_FILE_MISSING_FROM_RIGHT = "File missing from the right archive"
        const val INSPECTION_PERMISSIONS_MISMATCH = "Mismatched permissions"
        const val INSPECTION_TIMESTAMP_MISMATCH = "Mismatched timestamps"
        const val INSPECTION_UNCOMPRESSED_SIZE_MISMATCH = "Mismatched uncompressed sizes"
    }
}