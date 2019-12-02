package com.h0tk3y.player

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLClassLoader
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

open class MusicApp(
    private val pluginClasspath: List<File>,
    private val enabledPluginClasses: Set<String>
) : AutoCloseable {
    fun pluginFile(pluginId: String): File {
        val tmp = File("tmp/").mkdir()
        return File("tmp/$pluginId")
    }
    fun init() {

        plugins.forEach { plugin ->
            val file = pluginFile(plugin.pluginId)
            if (file.exists()) {
                FileInputStream(file).use {
                    plugin.init(it)
                }
            } else
                plugin.init(null)
        }

        musicLibrary // access to initialize
        player.init()
    }

    override fun close() {
        if (isClosed) return
        isClosed = true

        plugins.forEach { plugin ->
            val file = pluginFile(plugin.pluginId)
            file.createNewFile()
            FileOutputStream(file).use {
                plugin.persist(it)
            }
        }
    }

    fun wipePersistedPluginData() {
        plugins.forEach { pluginFile(it.pluginId).delete() }
    }

    private val pluginClassLoader: ClassLoader =
        URLClassLoader(pluginClasspath.map { it.toURI().toURL() }.toTypedArray())

    private val plugins: List<MusicPlugin> by lazy {
        enabledPluginClasses.map {
            val pluginClass: KClass<*>

            try {
                pluginClass = pluginClassLoader.loadClass(it).kotlin
            } catch (_: ClassNotFoundException) {
                throw PluginClassNotFoundException(it)
            }

            if (!pluginClass.isSubclassOf(MusicPlugin::class)) {
                throw IllegalPluginException(pluginClass.java)
            }

            val primary = pluginClass.primaryConstructor
            if (primary != null) {
                if (primary.parameters.isEmpty()) {
                    val pluginObj = primary.call() as MusicPlugin

                    val property = pluginClass.memberProperties.singleOrNull { it.name == "musicAppInstance" }
                    if (property == null || property !is KMutableProperty1<*, *>) {
                        throw IllegalPluginException(pluginClass.java)
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        (property as KMutableProperty1<MusicPlugin, MusicApp>).set(pluginObj, this)
                    }

                    return@map pluginObj

                } else if (primary.parameters.size == 1 && primary.parameters[0].type.jvmErasure == MusicApp::class) {
                    return@map primary.call(this) as MusicPlugin
                } else {
                    throw IllegalPluginException(pluginClass.java)
                }
            } else {
                throw IllegalPluginException(pluginClass.java)
            }
        }
    }

    fun findSinglePlugin(pluginClassName: String): MusicPlugin? =
        plugins.singleOrNull { it::class.qualifiedName == pluginClassName }

    fun <T : MusicPlugin> getPlugins(pluginClass: Class<T>): List<T> =
        plugins.filterIsInstance(pluginClass)

    private val musicLibraryContributors: List<MusicLibraryContributorPlugin>
        get() = getPlugins(MusicLibraryContributorPlugin::class.java)

    protected val playbackListeners: List<PlaybackListenerPlugin>
        get() = getPlugins(PlaybackListenerPlugin::class.java)

    val musicLibrary: MusicLibrary by lazy {
        musicLibraryContributors
            .sortedWith(compareBy({ it.preferredOrder }, { it.pluginId }))
            .fold(MusicLibrary(mutableListOf())) { acc, it -> it.contribute(acc) }
    }

    open val player: MusicPlayer by lazy {
        JLayerMusicPlayer(
            playbackListeners
        )
    }

    fun startPlayback(playlist: Playlist, fromPosition: Int) {
        player.playbackState = PlaybackState.Playing(
            PlaylistPosition(
                playlist,
                fromPosition
            ), isResumed = false
        )
    }

    fun nextOrStop() = player.playbackState.playlistPosition?.let {
        val nextPosition = it.position + 1
        player.playbackState =
            if (nextPosition in it.playlist.tracks.indices)
                PlaybackState.Playing(
                    PlaylistPosition(
                        it.playlist,
                        nextPosition
                    ), isResumed = false
                )
            else
                PlaybackState.Stopped
    }

    @Volatile
    var isClosed = false
        private set
}