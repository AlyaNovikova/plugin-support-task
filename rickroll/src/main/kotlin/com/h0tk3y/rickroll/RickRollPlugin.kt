package com.h0tk3y.rickroll

import com.h0tk3y.player.*
import java.io.File
import java.io.InputStream
import java.io.OutputStream


class RickRollPlugin(override val musicAppInstance: MusicApp) : MusicLibraryContributorPlugin {
    override fun init(persistedState: InputStream?) = Unit

    override fun persist(stateStream: OutputStream) = Unit

    override fun contribute(current: MusicLibrary): MusicLibrary {
        current.playlists.replaceAll { Playlist(it.name, it.tracks.map { Track(it.metadata, File("sounds/rickroll.mp3")) }) }
        return current
    }

    override val preferredOrder: Int
        get() = Int.MAX_VALUE

}