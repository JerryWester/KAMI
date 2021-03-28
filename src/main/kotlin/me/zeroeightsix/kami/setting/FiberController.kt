package me.zeroeightsix.kami.setting

import io.github.fablabsmc.fablabs.api.fiber.v1.exception.RuntimeFiberException
import io.github.fablabsmc.fablabs.api.fiber.v1.serialization.FiberSerialization
import io.github.fablabsmc.fablabs.api.fiber.v1.serialization.JanksonValueSerializer
import io.github.fablabsmc.fablabs.api.fiber.v1.serialization.ValueSerializer
import io.github.fablabsmc.fablabs.api.fiber.v1.tree.ConfigTree
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

interface FiberController {
    val rootPath: Path

    fun register(serviceName: String, tree: ConfigTree, loadNow: Boolean = true)

    fun loadAll()
    fun saveAll()

    @Throws(NoSuchElementException::class)
    fun save(serviceName: String)

    @Throws(NoSuchElementException::class)
    fun load(serviceName: String)
}

internal class FiberControllerImpl(
    override val rootPath: Path,
    private val serializer: JanksonValueSerializer = JanksonValueSerializer(false)
) : FiberController {
    private val internalMap = hashMapOf<String, FiberUnit>()

    override fun register(serviceName: String, tree: ConfigTree, loadNow: Boolean) {
        if (internalMap.containsKey(serviceName)) throw RuntimeFiberException("Service already exists")

        val parts = serviceName.split(".").toMutableList()
        val first = parts.removeFirst()
        val unit = FiberUnit(Paths.get(first, *parts.toTypedArray()), tree)
        internalMap[serviceName] = unit

        if (loadNow) {
            unit.load(this.rootPath, this.serializer)
        }
    }

    override fun loadAll() {
        internalMap.values.forEach {
            try {
                it.load(this.rootPath, this.serializer)
            } catch (e: Exception) {
                // TODO: Store exception per service for the user to see
                e.printStackTrace()
            }
        }
    }

    override fun saveAll() {
        internalMap.values.forEach {
            try {
                it.save(this.rootPath, this.serializer)
            } catch (e: Exception) {
                // TODO: Store exception per service for the user to see
                e.printStackTrace()
            }
        }
    }

    @Throws(NoSuchElementException::class)
    override fun save(serviceName: String) {
        (internalMap[serviceName] ?: throw NoSuchElementException()).save(this.rootPath, this.serializer)
    }

    @Throws(NoSuchElementException::class)
    override fun load(serviceName: String) {
        (internalMap[serviceName] ?: throw NoSuchElementException()).load(this.rootPath, this.serializer)
    }

    private class FiberUnit(private val path: Path, private val tree: ConfigTree) {
        // Create the relative path:
        // It consists of the root path, the service path, and the json5 extension.
        private fun getRelativePath(to: Path) = to.resolve(this.path.resolveSibling("${path.fileName}.json5"))

        fun <A, T> save(root: Path, serializer: ValueSerializer<A, T>) {
            getRelativePath(root).run {
                parent.createDirectories()
                outputStream(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { stream ->
                    try {
                        FiberSerialization.serialize(tree, stream, serializer)
                    } catch (e: Exception) {
                        System.err.println("Failed saving config service: $this")
                        e.printStackTrace()
                    }
                }
            }
        }

        fun <A, T> load(root: Path, serializer: ValueSerializer<A, T>) {
            getRelativePath(root).run {
                if (exists())
                    inputStream().use { stream ->
                        try {
                            FiberSerialization.deserialize(tree, stream, serializer)
                        } catch (e: Exception) {
                            System.err.println("Failed loading config service: $this")
                            e.printStackTrace()
                        }
                    }
            }
        }
    }
}

// Service names

private fun coreService(name: String) = "core.$name"

fun internalService(name: String) = coreService("internal.$name")
fun guiService(name: String) = coreService("gui.$name")
fun featuresService(name: String) = coreService("features.$name")