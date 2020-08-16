package com.simplecityapps.shuttle.ui.screens.home

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.coroutineScope
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.RecyclerListener
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.repository.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.PlaylistRepository
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.common.recyclerview.clearAdapterOnDetach
import com.simplecityapps.shuttle.ui.common.view.HomeButton
import com.simplecityapps.shuttle.ui.screens.library.albumartists.AlbumArtistBinder
import com.simplecityapps.shuttle.ui.screens.library.albumartists.detail.AlbumArtistDetailFragmentArgs
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumBinder
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.AlbumDetailFragmentArgs
import com.simplecityapps.shuttle.ui.screens.library.playlists.detail.PlaylistDetailFragmentArgs
import com.simplecityapps.shuttle.ui.screens.library.playlists.smart.SmartPlaylist
import com.simplecityapps.shuttle.ui.screens.library.playlists.smart.SmartPlaylistDetailFragmentArgs
import com.simplecityapps.shuttle.ui.screens.playlistmenu.CreatePlaylistDialogFragment
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuPresenter
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

class HomeFragment :
    Fragment(),
    Injectable,
    HomeContract.View,
    CreatePlaylistDialogFragment.Listener {

    @Inject lateinit var presenter: HomePresenter

    @Inject lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    @Inject lateinit var playlistRepository: PlaylistRepository

    private var recyclerView: RecyclerView by autoCleared()

    private lateinit var adapter: RecyclerAdapter

    private var imageLoader: ArtworkImageLoader by autoCleared()

    private lateinit var playlistMenuView: PlaylistMenuView

    private var recyclerViewState: Parcelable? = null

    private val coroutineScope = CoroutineScope(Dispatchers.Main)


    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = RecyclerAdapter(lifecycle.coroutineScope)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val historyButton: HomeButton = view.findViewById(R.id.historyButton)
        val latestButton: HomeButton = view.findViewById(R.id.latestButton)
        val favoritesButton: HomeButton = view.findViewById(R.id.favoritesButton)
        val shuffleButton: HomeButton = view.findViewById(R.id.shuffleButton)

        playlistMenuView = PlaylistMenuView(requireContext(), playlistMenuPresenter, childFragmentManager)

        historyButton.setOnClickListener {
            val navController = findNavController()
            if (navController.currentDestination?.id != R.id.smartPlaylistDetailFragment) {
                navController.navigate(
                    R.id.action_homeFragment_to_smartPlaylistDetailFragment,
                    SmartPlaylistDetailFragmentArgs(SmartPlaylist.RecentlyPlayed).toBundle()
                )
            }
        }
        latestButton.setOnClickListener {
            val navController = findNavController()
            if (navController.currentDestination?.id != R.id.smartPlaylistDetailFragment) {
                navController.navigate(
                    R.id.action_homeFragment_to_smartPlaylistDetailFragment,
                    SmartPlaylistDetailFragmentArgs(SmartPlaylist.RecentlyAdded).toBundle()
                )
            }
        }
        favoritesButton.setOnClickListener { navigateToPlaylist(PlaylistQuery.PlaylistName("Favorites")) }
        shuffleButton.setOnClickListener { presenter.shuffleAll() }

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.adapter = adapter
        recyclerView.setRecyclerListener(RecyclerListener())
        recyclerView.clearAdapterOnDetach()

        val decoration = DividerItemDecoration(context, LinearLayout.VERTICAL)
        decoration.setDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.divider)!!)
        recyclerView.addItemDecoration(decoration)

        imageLoader = GlideImageLoader(this)

        savedInstanceState?.getParcelable<Parcelable>(ARG_RECYCLER_STATE)?.let { recyclerViewState = it }

        presenter.bindView(this)
        playlistMenuPresenter.bindView(playlistMenuView)
    }

    override fun onResume() {
        super.onResume()

        recyclerViewState?.let {
            recyclerView.layoutManager?.onRestoreInstanceState(recyclerViewState)
        }

        presenter.loadData()
    }

    override fun onPause() {
        super.onPause()
        recyclerViewState = recyclerView.layoutManager?.onSaveInstanceState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(ARG_RECYCLER_STATE, recyclerViewState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        presenter.unbindView()
        playlistMenuPresenter.unbindView()

        coroutineScope.cancel()
        super.onDestroyView()
    }


    // HomeContract.View Implementation

    override fun showLoadError(error: Error) {
        Toast.makeText(context, error.userDescription(), Toast.LENGTH_LONG).show()
    }

    override fun setData(data: HomeContract.HomeData) {
        val viewBinders = mutableListOf<ViewBinder>()
        if (data.recentlyPlayedAlbums.isNotEmpty()) {
            viewBinders.add(
                HorizontalAlbumListBinder("Recent", "Recently played albums", data.recentlyPlayedAlbums, imageLoader, scope = lifecycle.coroutineScope, listener = albumBinderListener)
            )
        }
        if (data.mostPlayedAlbums.isNotEmpty()) {
            viewBinders.add(
                HorizontalAlbumListBinder(
                    "Most Played",
                    "Albums in heavy rotation",
                    data.mostPlayedAlbums,
                    imageLoader,
                    showPlayCountBadge = true,
                    scope = lifecycle.coroutineScope,
                    listener = albumBinderListener
                )
            )
        }
        if (data.albumsFromThisYear.isNotEmpty()) {
            viewBinders.add(
                HorizontalAlbumListBinder(
                    "This Year",
                    "Albums released in ${Calendar.getInstance().get(Calendar.YEAR)}",
                    data.albumsFromThisYear,
                    imageLoader,
                    scope = lifecycle.coroutineScope,
                    listener = albumBinderListener
                )
            )
        }
        if (data.unplayedAlbumArtists.isNotEmpty()) {
            viewBinders.add(
                HorizontalAlbumArtistListBinder(
                    "Something Different",
                    "Artists you haven't listened to in a while",
                    data.unplayedAlbumArtists,
                    imageLoader,
                    scope = lifecycle.coroutineScope,
                    listener = albumArtistBinderListener
                )
            )
        }
        adapter.update(viewBinders, completion = {
            recyclerViewState?.let {
                recyclerView.layoutManager?.onRestoreInstanceState(recyclerViewState)
            }
        })
    }

    override fun showDeleteError(error: Error) {
        Toast.makeText(requireContext(), error.userDescription(), Toast.LENGTH_LONG).show()
    }

    override fun onAddedToQueue(albumArtist: AlbumArtist) {
        Toast.makeText(context, "${albumArtist.name} added to queue", Toast.LENGTH_SHORT).show()
    }

    override fun onAddedToQueue(album: Album) {
        Toast.makeText(context, "${album.name} added to queue", Toast.LENGTH_SHORT).show()
    }


    // Private

    private val albumArtistBinderListener = object : AlbumArtistBinder.Listener {

        override fun onAlbumArtistClicked(albumArtist: AlbumArtist, viewHolder: AlbumArtistBinder.ViewHolder) {
            findNavController().navigate(
                R.id.action_homeFragment_to_albumArtistDetailFragment,
                AlbumArtistDetailFragmentArgs(albumArtist, true).toBundle(),
                null,
                FragmentNavigatorExtras(viewHolder.imageView to viewHolder.imageView.transitionName)
            )
        }

        override fun onOverflowClicked(view: View, albumArtist: AlbumArtist) {
            val popupMenu = PopupMenu(requireContext(), view)
            popupMenu.inflate(R.menu.menu_popup_add)

            playlistMenuView.createPlaylistMenu(popupMenu.menu)

            popupMenu.setOnMenuItemClickListener { menuItem ->
                if (playlistMenuView.handleMenuItem(menuItem, PlaylistData.AlbumArtists(albumArtist))) {
                    return@setOnMenuItemClickListener true
                } else {
                    when (menuItem.itemId) {
                        R.id.play -> {
                            presenter.play(albumArtist)
                            return@setOnMenuItemClickListener true
                        }
                        R.id.queue -> {
                            presenter.addToQueue(albumArtist)
                            return@setOnMenuItemClickListener true
                        }
                        R.id.playNext -> {
                            presenter.playNext(albumArtist)
                            return@setOnMenuItemClickListener true
                        }
                        R.id.exclude -> {
                            presenter.exclude(albumArtist)
                            return@setOnMenuItemClickListener true
                        }
                    }
                }
                false
            }
            popupMenu.show()
        }
    }

    private val albumBinderListener = object : AlbumBinder.Listener {

        override fun onAlbumClicked(album: Album, viewHolder: AlbumBinder.ViewHolder) {
            findNavController().navigate(
                R.id.action_homeFragment_to_albumDetailFragment,
                AlbumDetailFragmentArgs(album, true).toBundle(),
                null,
                FragmentNavigatorExtras(viewHolder.imageView to viewHolder.imageView.transitionName)
            )
        }


        override fun onOverflowClicked(view: View, album: Album) {
            val popupMenu = PopupMenu(requireContext(), view)
            popupMenu.inflate(R.menu.menu_popup_add)

            playlistMenuView.createPlaylistMenu(popupMenu.menu)

            popupMenu.setOnMenuItemClickListener { menuItem ->
                if (playlistMenuView.handleMenuItem(menuItem, PlaylistData.Albums(album))) {
                    return@setOnMenuItemClickListener true
                } else {
                    when (menuItem.itemId) {
                        R.id.play -> {
                            presenter.play(album)
                            return@setOnMenuItemClickListener true
                        }
                        R.id.queue -> {
                            presenter.addToQueue(album)
                            return@setOnMenuItemClickListener true
                        }
                        R.id.playNext -> {
                            presenter.playNext(album)
                            return@setOnMenuItemClickListener true
                        }
                        R.id.exclude -> {
                            presenter.exclude(album)
                            return@setOnMenuItemClickListener true
                        }
                    }
                }
                false
            }
            popupMenu.show()
        }
    }

    private fun navigateToPlaylist(query: PlaylistQuery) {
        coroutineScope.launch {
            val playlist = playlistRepository.getPlaylists(query).firstOrNull().orEmpty().firstOrNull()
            if (playlist == null || playlist.songCount == 0) {
                Toast.makeText(context, "Playlist empty", Toast.LENGTH_SHORT).show()
            } else {
                if (findNavController().currentDestination?.id != R.id.playlistDetailFragment) {
                    findNavController().navigate(R.id.action_homeFragment_to_playlistDetailFragment, PlaylistDetailFragmentArgs(playlist).toBundle())
                }
            }
        }
    }


    // CreatePlaylistDialogFragment.Listener Implementation

    override fun onSave(text: String, playlistData: PlaylistData) {
        playlistMenuPresenter.createPlaylist(text, playlistData)
    }

    companion object {
        const val ARG_RECYCLER_STATE = "recycler_state"
    }
}