package au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.lastm

import au.com.simplecityapps.shuttle.imageloading.glide.loader.common.SongArtworkProvider
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.RemoteArtworkModelLoader
import au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.RemoteArtworkProvider
import au.com.simplecityapps.shuttle.imageloading.networking.ArtworkUrlResult
import au.com.simplecityapps.shuttle.imageloading.networking.lastfm.LastFmService
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.simplecityapps.mediaprovider.model.Song
import retrofit2.Call
import java.io.InputStream

class LastFmRemoteSongArtworkModelLoader(
    private val lastFm: LastFmService.LastFm,
    private val remoteArtworkModelLoader: RemoteArtworkModelLoader
) : ModelLoader<Song, InputStream> {

    override fun buildLoadData(model: Song, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
        return remoteArtworkModelLoader.buildLoadData(LastFmSongRemoteArtworkProvider(lastFm, model), width, height, options)
    }

    override fun handles(model: Song): Boolean {
        return true
    }


    class LastFmSongRemoteArtworkProvider(
        private val lastFm: LastFmService.LastFm,
        private val song: Song
    ) : SongArtworkProvider(song),
        RemoteArtworkProvider {

        override fun getCacheKey(): String {
            return "${song.albumArtistName}_${song.albumName}"
        }

        override fun getArtworkUri(): Call<out ArtworkUrlResult> {
            return lastFm.getLastFmAlbum(song.albumArtistName, song.albumName)
        }
    }
}


class LastFmRemoteSongArtworkModelLoaderFactory(
    private val lastFm: LastFmService.LastFm
) : ModelLoaderFactory<Song, InputStream> {

    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Song, InputStream> {
        return LastFmRemoteSongArtworkModelLoader(lastFm, multiFactory.build(RemoteArtworkProvider::class.java, InputStream::class.java) as RemoteArtworkModelLoader)
    }

    override fun teardown() {

    }
}