package no.njoh.pulseengine.core.shared.utils

import java.io.File
import java.io.InputStream
import java.net.JarURLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.LinkedHashMap

/**
 * Responsible for resolving paths and loading resources both from inside a JAR and from the JAR location.
 *
 * If the application archive is "C:/Application/application.jar", the path "data/example.ext" is searched
 * for in this order:
 *
 * 1. "C:/Application/data/example.ext"
 * 2. The entry "data/example.ext" inside application.jar
 *
 * A file outside the archive takes precedence when both locations contain the same path.
 * Absolute paths such as "D:/directory/example.ext" are also accepted.
 */
internal object ResourceResolver
{
    private const val RESOURCE_ROOT_PROPERTY = "pulseengine.resourceRoot"
    private val classLoader = ResourceResolver::class.java.classLoader

    /**
     * Directory used as the starting point for relative files outside the JAR.
     * If [externalRoot] is "C:/Application", then "data/example.ext" refers to "C:/Application/data/example.ext". 
     * It defaults to the directory that contains the running JAR, or the current project directory in the IDE.
     * Set -Dpulseengine.resourceRoot=D:/Application to override it.
     */
    private val externalRoot: Path by lazy()
    {
        System.getProperty(RESOURCE_ROOT_PROPERTY)
            ?.takeIf { it.isNotBlank() }
            ?.let { Paths.get(it).toAbsolutePath().normalize() }
            ?: findDefaultExternalRoot()
    }

    /**
     * Opens a resource for reading.
     * open("data/example.ext") first checks for that path below [externalRoot], then
     * for the same path inside the JAR. open("D:/directory/example.ext") reads that exact file. 
     * Returns null when neither location contains the resource.
     */
    fun open(path: String): InputStream?
    {
        resolveDiskFile(path)?.let { return Files.newInputStream(it) }
        return openClasspath(path)
    }

    /**
     * Finds a file on disk, without looking inside the JAR.
     * resolveDiskFile("data/example.ext") may return "C:/Application/data/example.ext". An absolute path is returned 
     * directly when it exists. Returns null for resources that exist only inside the JAR.
     */
    fun resolveDiskFile(path: String): Path?
    {
        val directPath = path.toPathOrNull()
        if (directPath?.isAbsolute == true)
            return directPath.normalize().takeIf { Files.isRegularFile(it) }

        val relativePath = normalizeRelativePath(path) ?: return null
        val platformPath = relativePath.toPlatformPathOrNull() ?: return null
        val externalPath = externalRoot.resolve(platformPath).normalize()
        return externalPath.takeIf { it.startsWith(externalRoot) && Files.isRegularFile(it) }
    }

    /**
     * Builds the path of a file referenced by another file.
     * - Base "directory/main.ext" and reference "related.ext" produce "directory/related.ext".
     * - Base "directory/main.ext" and reference "../shared/related.ext" produce "shared/related.ext".
     * - Base "D:/directory/main.ext" and reference "related.ext" produce the absolute path "D:/directory/related.ext".
     * Returns null when a relative reference would move above the resource root, for example base 
     * "directory/main.ext" with reference "../../file.ext".
     */
    fun resolveRelativeReferencePath(baseFile: String, reference: String): String?
    {
        val referencePath = reference.toPathOrNull()
        if (referencePath?.isAbsolute == true)
            return referencePath.normalize().toString()

        val basePath = baseFile.toPathOrNull()
        if (basePath?.isAbsolute == true)
        {
            val parent = basePath.normalize().parent ?: basePath.fileSystem.getPath("")
            return parent.resolve(reference.replace('/', File.separatorChar)).normalize().toString()
        }

        val normalizedBase = normalizeRelativePath(baseFile) ?: return null
        val parent = normalizedBase.substringBeforeLast('/', "")
        val combined = if (parent.isEmpty()) reference else "$parent/$reference"
        return normalizeRelativePath(combined)
    }

    /**
     * Lists files directly inside [directory], but not files in subdirectories.
     * For listFiles("directory"), results look like "directory/file.ext".
     * Files from "<JAR directory>/directory" and "directory" inside the JAR are combined. 
     * If both contain "file.ext", only the file outside the JAR is represented.
     */
    fun listFiles(directory: String): List<String>
    {
        val relativeDirectory = normalizeRelativePath(directory)?.trimEnd('/') ?: return emptyList()
        val filesByName = LinkedHashMap<String, String>()

        listClasspathFiles(relativeDirectory).forEach { path -> filesByName[path.substringAfterLast('/')] = path }

        val platformPath = relativeDirectory.toPlatformPathOrNull() ?: return filesByName.values.sorted()
        val externalDirectory = externalRoot.resolve(platformPath).normalize()
        if (externalDirectory.startsWith(externalRoot) && Files.isDirectory(externalDirectory))
        {
            Files.list(externalDirectory).use { paths ->
                paths.forEach()
                {
                    if (Files.isRegularFile(it)) filesByName[it.fileName.toString()] = externalRoot.relativize(it.normalize()).toResourcePath()
                }
            }
        }

        return filesByName.values.sorted()
    }

    /**
     * Converts a disk path below [externalRoot] into the relative path. 
     * Forward slashes are used so the result works on both Windows and Unix-like systems.
     * With an [externalRoot] of "C:/Application":
     * - "C:\Application\directory\file.ext" becomes "directory/file.ext".
     * - "directory\file.ext" becomes "directory/file.ext".
     * - "D:\Files\file.ext" stays absolute because it is outside the root.
     */
    fun toResourcePath(path: String): String
    {
        val filePath = path.toPathOrNull()
        if (filePath?.isAbsolute == true)
        {
            val normalized = filePath.normalize()
            return if (normalized.startsWith(externalRoot))
                externalRoot.relativize(normalized).toResourcePath()
            else
                normalized.toString()
        }
        return normalizeRelativePath(path) ?: path.replace('\\', '/')
    }

    /**
     * Checks whether a resource path and a reported disk path identify the same file. 
     * This lets hot reload match "directory/file.ext" against a file watcher event such 
     * as "C:\Application\directory\file.ext".
     */
    fun matchesPath(resourcePath: String, changedFilePath: String): Boolean
    {
        val changedPath = changedFilePath.toPathOrNull()?.toAbsolutePath()?.normalize() ?: return false
        if (resolveDiskFile(resourcePath)?.toAbsolutePath()?.normalize() == changedPath) 
            return true

        val resource = normalizeRelativePath(resourcePath) ?: return false
        val changed = normalizeRelativePath(toResourcePath(changedFilePath)) ?: return false

        return changed == resource || changed.endsWith("/$resource")
    }

    /**
     * Cleans a relative resource path into the form used for JAR entries.
     * - "directory\.\file.ext" becomes "directory/file.ext".
     * - "directory/subdirectory/../file.ext" becomes "directory/file.ext".
     * - "../file.ext" returns null because it leaves the resource root.
     * "normalized" here means forward slashes, no "." parts and all safe ".." parts already applied.
     */
    fun normalizeRelativePath(path: String): String?
    {
        val normalizedInput = path.replace('\\', '/').trim()
        if (normalizedInput.isEmpty()) return ""

        val parts = ArrayDeque<String>()
        for (part in normalizedInput.trimStart('/').split('/'))
        {
            when (part)
            {
                "", "." -> Unit
                ".." -> if (parts.isEmpty()) return null else parts.removeLast()
                else -> parts.addLast(part)
            }
        }
        return parts.joinToString("/")
    }

    /** 
     * Opens an entry such as "directory/file.ext" only from packaged resources. 
     */
    fun openClasspath(path: String): InputStream?
    {
        val relativePath = normalizeRelativePath(path) ?: return null
        return classLoader.getResourceAsStream(relativePath)
    }

    /** 
     * Lists entries such as "directory/file.ext" from an IDE resource directory or JAR. 
     */
    private fun listClasspathFiles(directory: String): List<String>
    {
        if (directory.isEmpty()) return emptyList()

        val files = LinkedHashMap<String, String>()
        val resources = classLoader.getResources(directory)
        while (resources.hasMoreElements())
        {
            val url = resources.nextElement()
            when (url.protocol)
            {
                "file" ->
                {
                    val path = runCatching { Paths.get(url.toURI()) }.getOrNull() ?: continue
                    if (Files.isDirectory(path))
                    {
                        Files.list(path).use { paths ->
                            paths.filter { Files.isRegularFile(it) }.forEach {
                                val relativePath = "$directory/${it.fileName}".trimStart('/')
                                files[it.fileName.toString()] = relativePath
                            }
                        }
                    }
                }
                "jar" ->
                {
                    val connection = url.openConnection() as? JarURLConnection ?: continue
                    connection.useCaches = false
                    connection.jarFile.use { jar ->
                        val prefix = "$directory/"
                        jar.entries()
                            .asSequence()
                            .filter { entry -> !entry.isDirectory && entry.name.startsWith(prefix) && '/' !in entry.name.removePrefix(prefix) }
                            .forEach { entry -> files[entry.name.substringAfterLast('/')] = entry.name }
                    }
                }
            }
        }

        return files.values.toList()
    }

    /** 
     * Finds the JAR directory, falling back to the current directory when running from the IDE. 
     * */
    private fun findDefaultExternalRoot(): Path
    {
        val codeLocation = runCatching {
            Paths.get(ResourceResolver::class.java.protectionDomain.codeSource.location.toURI()).toAbsolutePath().normalize()
        }.getOrNull()

        return if (codeLocation != null && Files.isRegularFile(codeLocation))
            codeLocation.parent ?: Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        else
            Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
    }

    /** 
     * Parses "D:/directory/file.ext" or "directory/file.ext" without throwing for invalid input. 
     */
    private fun String.toPathOrNull(): Path? = runCatching { Paths.get(this) }.getOrNull()

    /** 
     * Converts "directory/file.ext" to the separator style expected by the current operating system. 
     */
    private fun String.toPlatformPathOrNull(): Path? = runCatching { Paths.get(this.replace('/', File.separatorChar)) }.getOrNull()

    /** 
     * Converts a path such as "directory\file.ext" to "directory/file.ext". 
     */
    private fun Path.toResourcePath(): String = this.joinToString("/") { it.toString() }
}
