package com.simplecityapps.localmediaprovider.local.repository

import android.content.Context
import android.net.Uri
import com.simplecityapps.localmediaprovider.local.data.room.dao.PlaylistDataDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.PlaylistSongJoinDao
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistData
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistSongJoin
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.playlists.comparator
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.PlaylistSong
import com.simplecityapps.shuttle.model.SmartPlaylist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import com.simplecityapps.shuttle.sorting.SongSortOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import timber.log.Timber

class LocalPlaylistRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val playlistDataDao: PlaylistDataDao,
    private val playlistSongJoinDao: PlaylistSongJoinDao
) : PlaylistRepository {
    private val playlistsRelay: StateFlow<List<Playlist>?> by lazy {
        playlistDataDao
            .getAll()
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Lazily, null)
    }

    override fun getPlaylists(query: PlaylistQuery): Flow<List<Playlist>> {
        return playlistsRelay
            .filterNotNull()
            .map { playlists ->
                playlists
                    .filter(query.predicate)
                    .toMutableList()
                    .sortedWith(query.sortOrder.comparator)
            }
    }

    override fun getSmartPlaylists(): Flow<List<SmartPlaylist>> {
        return flow {
            emit(
                listOf(
                    SmartPlaylist(com.simplecityapps.mediaprovider.R.string.playlist_title_recently_added, SongQuery.RecentlyAdded()),
                    SmartPlaylist(com.simplecityapps.mediaprovider.R.string.playlist_title_most_played, SongQuery.PlayCount(2, SongSortOrder.PlayCount))
                )
            )
        }
    }

    override suspend fun getFavoritesPlaylist(): Playlist {
        val favoritesName = context.getString(com.simplecityapps.mediaprovider.R.string.playlist_title_favorites)
        return withContext(Dispatchers.IO) {
            playlistsRelay
                .filterNotNull()
                .firstOrNull()
                ?.firstOrNull { it.name == favoritesName }
                ?: createPlaylist(
                    name = favoritesName,
                    mediaProviderType = MediaProviderType.Shuttle,
                    songs = null,
                    externalId = null
                )
        }
    }

    override suspend fun getRatingPlaylist(rating: Int): Playlist? {
        // TODO: localize?
        val favoritesName = "Favourites - $rating stars"
        return withContext(Dispatchers.IO) {
            playlistsRelay
                .filterNotNull()
                .firstOrNull()
                ?.firstOrNull { it.name == favoritesName }
        }
    }

    override suspend fun getRatingPlaylistForSong(songId: Long): Playlist? {
        Timber.w("getRatingPlaylistForSong...")
        val ret = playlistDataDao.getPlaylistIdsForSong(songId)
        Timber.w("  this song is in playlists ${ret}")
        playlistDataDao.getPlaylistIdsForSong(songId).forEach {
            val list = playlistDataDao.getPlaylist(it)
            Timber.i("Considering playlist for song: ${list.name}")
            // TODO: de-duplicate this code?
            if (list.name.startsWith("Favourites - ") && list.name.endsWith(" stars")) {
                return list
            }
        }

        Timber.w("End of getRatingPlaylistForSong")
        return null
    }

    override suspend fun getRatingForSong(songId: Long): Int? {
        Timber.i("getRatingForSong...")
        getRatingPlaylistForSong(songId)?.let{
            Timber.i("found PL=${it.name}...")
            with(it.name) {
                val r = when {
                    equals("Favourites - 1 stars") -> return 1
                    equals("Favourites - 2 stars") -> return 2
                    equals("Favourites - 3 stars") -> return 3
                    equals("Favourites - 4 stars") -> return 4
                    equals("Favourites - 5 stars") -> return 5
                    else -> return null
                }
            }
        }

        Timber.w("getRatingPlaylistForSong failed!")
        return null
    }

    override suspend fun createPlaylist(
        name: String,
        mediaProviderType: MediaProviderType,
        songs: List<Song>?,
        externalId: String?
    ): Playlist {
        val playlistId =
            playlistDataDao.insert(
                PlaylistData(
                    name = name,
                    sortOrder = PlaylistSongSortOrder.Position,
                    mediaProviderType = mediaProviderType,
                    externalId = externalId
                )
            )
        playlistSongJoinDao.insert(
            songs.orEmpty().mapIndexed { i, song ->
                PlaylistSongJoin(
                    playlistId = playlistId,
                    songId = song.id,
                    sortOrder = i.toLong()
                )
            }
        )
        val playlist = playlistDataDao.getPlaylist(playlistId)
        Timber.v("Created playlist: ${playlist.name} with ${playlist.songCount} songs}")
        return playlist
    }

    override suspend fun addToPlaylist(
        playlist: Playlist,
        songs: List<Song>
    ) {
        playlistSongJoinDao.insert(
            songs.mapIndexed { i, song ->
                PlaylistSongJoin(
                    playlistId = playlist.id,
                    songId = song.id,
                    sortOrder = (playlist.songCount + i).toLong()
                )
            }
        )
        updateM3uFile(playlist)
    }

    override suspend fun removeFromPlaylist(
        playlist: Playlist,
        playlistSongs: List<PlaylistSong>
    ) {
        playlistSongJoinDao.delete(
            playlistId = playlist.id,
            playlistSongIds = playlistSongs.map { playlistSong -> playlistSong.id }.toTypedArray()
        )
        updateM3uFile(playlist)
    }

    override suspend fun removeSongsFromPlaylist(
        playlist: Playlist,
        songs: List<Song>
    ) {
        playlistSongJoinDao.deleteSongs(
            playlistId = playlist.id,
            songIds = songs.map { it.id }.toTypedArray()
        )
        updateM3uFile(playlist)
    }

    override fun getSongsForPlaylist(playlist: Playlist): Flow<List<PlaylistSong>> {
        return playlistSongJoinDao.getSongsForPlaylist(playlist.id)
            .map { playlistSong ->
                playlistSong.sortedWith(playlist.sortOrder.comparator)
            }
    }

    override suspend fun deletePlaylist(playlist: Playlist) {
        return playlistDataDao.delete(playlist.id)
        // TODO
    }

    override suspend fun deleteAll(mediaProviderType: MediaProviderType) {
        return playlistDataDao.deleteAll(mediaProviderType)
        // TODO
    }

    override suspend fun clearPlaylist(playlist: Playlist) {
        playlistDataDao.clear(playlist.id)
        updateM3uFile(playlist)
    }

    override suspend fun renamePlaylist(
        playlist: Playlist,
        name: String
    ) {
        return playlistDataDao.update(
            PlaylistData(
                id = playlist.id,
                name = name,
                externalId = playlist.externalId,
                mediaProviderType = playlist.mediaProvider,
                sortOrder = playlist.sortOrder
            )
        )
        // TODO?
    }

    override suspend fun updateM3uFile(playlist: Playlist) {
        val outputStream = playlist.externalId?.let { path ->
            context.contentResolver.openOutputStream(Uri.parse(playlist.externalId), "wt")
        }

        if (outputStream == null) {
            Timber.w("Unable to open M3U file at ${playlist.externalId} for playlist ${playlist.name}")
        } else {
            val playlistPath = Uri.decode(playlist.externalId?: "")
            val playlistFolder = playlistPath.substringBeforeLast("/") + "/"

            getSongsForPlaylist(playlist)
                .firstOrNull()
                .orEmpty()
                .forEach { plSong ->
                    // Quick-and-dirty way to relativize the song path to the m3u folder
                    // Note that paths can be content:// URIs, for which there is no proper .relativize() method
                    // We'll use absolute values (paths or URIs, whatever is in database) for files that are not stored in a sub-folder relative to the M3U file
                    val songPath = Uri.decode(plSong.song.path)
                    val relative = songPath.substringAfter(playlistFolder)
                    val line = relative.toByteArray() + /* CRLF */ 0x0d.toByte() + 0x0A.toByte()
                    outputStream.write(line)
                }
        }
    }

    override suspend fun updatePlaylistSortOder(
        playlist: Playlist,
        sortOrder: PlaylistSongSortOrder
    ) {
        playlistDataDao.update(
            PlaylistData(
                id = playlist.id,
                name = playlist.name,
                externalId = playlist.externalId,
                mediaProviderType = playlist.mediaProvider,
                sortOrder = sortOrder
            )
        )
    }

    override suspend fun updatePlaylistSongsSortOder(
        playlist: Playlist,
        playlistSongs: List<PlaylistSong>
    ) {
        playlistSongJoinDao.updateSortOrder(
            playlistSongs.map { playlistSong ->
                PlaylistSongJoin(
                    playlistId = playlist.id,
                    songId = playlistSong.song.id,
                    sortOrder = playlistSong.sortOrder
                ).apply {
                    id = playlistSong.id
                }
            }
        )
        updateM3uFile(playlist)
    }

    override suspend fun updatePlaylistMediaProviderType(
        playlist: Playlist,
        mediaProviderType: MediaProviderType
    ) {
        playlistDataDao.update(
            PlaylistData(
                id = playlist.id,
                name = playlist.name,
                sortOrder = playlist.sortOrder,
                mediaProviderType = mediaProviderType,
                externalId = playlist.externalId
            )
        )
    }

    override suspend fun updatePlaylistExternalId(
        playlist: Playlist,
        externalId: String?
    ) {
        playlistDataDao.update(
            PlaylistData(
                id = playlist.id,
                name = playlist.name,
                sortOrder = playlist.sortOrder,
                mediaProviderType = playlist.mediaProvider,
                externalId = externalId
            )
        )
    }
}

// Note: pretty sure new playlists (added from the app) are never exported
