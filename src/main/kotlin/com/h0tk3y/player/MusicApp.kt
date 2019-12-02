package com.h0tk3y.player

import java.io.File
import java.io.FileOutputStream
import java.net.URLClassLoader
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

open class MusicApp(
    private val pluginClasspath: List<File>,
    private val enabledPluginClasses: Set<String>
) : AutoCloseable {
    fun pluginFile(pluginId: String) = File("tmp/$pluginId")

    fun init() {

        /**
         * TODO: Инициализировать плагины с помощью функции [MusicPlugin.init],
         *       предоставив им байтовые потоки их состояния (для тех плагинов, для которых они сохранены).
         *       Обратите внимание на cлучаи, когда необходимо выбрасывать исключения
         *       [IllegalPluginException] и [PluginClassNotFoundException].
         **/

        plugins.forEach {
            val file = pluginFile(it.pluginId)
            if (file.exists()) {
                it.init(file.inputStream())
            }
            else
                it.init(null)
        }

        musicLibrary // access to initialize
        player.init()
    }

    override fun close() {
        if (isClosed) return
        isClosed = true

        /** TODO: Сохранить состояние плагинов с помощью [MusicPlugin.persist]. */
        plugins.forEach { it.persist(FileOutputStream(pluginFile(it.pluginId), true)) }
    }

    fun wipePersistedPluginData() {
        plugins.forEach { pluginFile(it.pluginId).delete() }
    }

    private val pluginClassLoader: ClassLoader = URLClassLoader(pluginClasspath.map { it.toURI().toURL() }.toTypedArray())

    private val plugins: List<MusicPlugin> by lazy {
        /**
         * TODO используя [pluginClassLoader] и следуя контракту [MusicPlugin],
         *      загрузить плагины, перечисленные в [enabledPluginClasses].
         *      Эта функция не должна вызывать [MusicPlugin.init]
         */

        enabledPluginClasses.map{
            val pluginClass: KClass<*>
            try {
                pluginClass = pluginClassLoader.loadClass(it).kotlin
            }
            catch (_: ClassNotFoundException) {
                throw PluginClassNotFoundException(it)
            }

            try {
                return@map pluginClass.primaryConstructor?.call(this) as MusicPlugin
//                pluginClass.constructors.forEach { println("constructor "); println(it); println(MusicApp::class.java); println() }
//                pluginClass.getDeclaredConstructor(MusicApp::class.java).newInstance(this);
            }
            catch (exc: Exception) {
                println(exc)
            }

            try{
                val plugin = pluginClass.primaryConstructor?.call() as MusicPlugin
                val property = plugin::class.memberProperties.singleOrNull { it.name == "musicAppInstance" }
                if (property == null || property !is KMutableProperty1<*, *>) {
                    throw IllegalPluginException(pluginClass.java)
                }
                else {
                    (property as KMutableProperty1<MusicPlugin, MusicApp>).set(plugin, this)
                    return@map plugin
                }
            }
            catch (_: Exception) { }

            throw IllegalPluginException(pluginClass.java)
        }.toList()
    }

    //        TODO("Если есть единственный плагин, принадлежащий типу по имени pluginClassName, вернуть его, иначе null.")
    fun findSinglePlugin(pluginClassName: String): MusicPlugin? = plugins.singleOrNull { it::class.qualifiedName == pluginClassName }

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
            ), isResumed = false)
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