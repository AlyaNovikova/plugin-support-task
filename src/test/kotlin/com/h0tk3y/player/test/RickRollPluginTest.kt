package com.h0tk3y.player.test

import com.h0tk3y.player.*
import java.io.File
import kotlin.test.*

private val thirdPartyPluginClasses: List<File> =
    System.getProperty("third-party-plugin-classes").split(File.pathSeparator).map { File(it) }

private val rickrollClasses: List<File> =
    System.getProperty("rickroll-classes").split(File.pathSeparator).map { File(it) }

private val rickrollName = "com.h0tk3y.rickroll.RickRollPlugin"

private val usageStatsPluginName = "com.h0tk3y.third.party.plugin.UsageStatsPlugin"
private val pluginWithAppPropertyName = "com.h0tk3y.third.party.plugin.PluginWithAppProperty"

class RickRollPluginTest {

    private val defaultEnabledPlugins = setOf(
        StaticPlaylistsLibraryContributor::class.java.canonicalName,
        usageStatsPluginName,
        pluginWithAppPropertyName
    )

    private fun withApp(
        wipePersistedData: Boolean = false,
        pluginClasspath: List<File> = thirdPartyPluginClasses,
        enabledPlugins: Set<String> = defaultEnabledPlugins,
        doTest: TestableMusicApp.() -> Unit
    ) {
        val app = TestableMusicApp(pluginClasspath, enabledPlugins)
        if (wipePersistedData) {
            app.wipePersistedPluginData()
        }
        app.use {
            it.init()
            it.doTest()
        }
    }

    @Test
    fun rickrollTest() {
        lateinit var defaultMusicLibrary : MusicLibrary
        withApp {
            defaultMusicLibrary = musicLibrary
        }
        val rickroll = File("sounds/rickroll.mp3").inputStream().readAllBytes()
        withApp (
            pluginClasspath = thirdPartyPluginClasses + rickrollClasses,
            enabledPlugins = defaultEnabledPlugins + setOf(rickrollName)
        ) {
            musicLibrary.playlists.zip(defaultMusicLibrary.playlists).forEach{
                assertEquals(it.first.name, it.second.name)
                it.first.tracks.zip(it.second.tracks).forEach {

                    assertEquals(it.first.metadata, it.second.metadata)

                    assertTrue (rickroll.contentEquals( it.first.byteStreamProvider().readAllBytes()))
                }
            }
        }
    }

}