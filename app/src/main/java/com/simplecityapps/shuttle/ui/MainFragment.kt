package com.simplecityapps.shuttle.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.Navigation.findNavController
import androidx.navigation.ui.NavigationUI
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.simplecityappds.saf.SafDirectoryHelper
import com.simplecityapps.localmediaprovider.local.provider.mediastore.MediaStoreSongProvider
import com.simplecityapps.localmediaprovider.local.provider.taglib.TaglibSongProvider
import com.simplecityapps.mediaprovider.repository.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.AlbumRepository
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.view.BottomSheetOverlayView
import com.simplecityapps.shuttle.ui.common.view.multisheet.MultiSheetView
import com.simplecityapps.shuttle.ui.screens.playback.PlaybackFragment
import com.simplecityapps.shuttle.ui.screens.playback.mini.MiniPlaybackFragment
import com.simplecityapps.shuttle.ui.screens.queue.QueueFragment
import com.simplecityapps.taglib.FileScanner
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.lang.ref.WeakReference
import javax.inject.Inject

class MainFragment
    : Fragment(),
    Injectable,
    QueueChangeCallback,
    BottomSheetOverlayView.OnBottomSheetStateChangeListener {

    @Inject lateinit var queueManager: QueueManager
    @Inject lateinit var queueWatcher: QueueWatcher
    @Inject lateinit var playbackManager: PlaybackManager

    @Inject lateinit var songRepository: SongRepository
    @Inject lateinit var albumsRepository: AlbumRepository
    @Inject lateinit var albumArtistsRepository: AlbumArtistRepository

    @Inject lateinit var playbackPreferenceManager: PlaybackPreferenceManager
    @Inject lateinit var fileScanner: FileScanner

    private val compositeDisposable = CompositeDisposable()

    private var onBackPressCallback: OnBackPressedCallback? = null

    private lateinit var bottomSheetOverlayView: BottomSheetOverlayView


    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navController = findNavController(activity!!, R.id.navHostFragment)

        bottomSheetOverlayView = view.findViewById(R.id.bottomSheetOverlayView)
        bottomSheetOverlayView.listener = this

        val bottomNavigationView: BottomNavigationView = view.findViewById(R.id.bottomNavigationView)
        bottomNavigationView.setupWithNavController(navController) { menuItem ->
            if (menuItem.itemId == R.id.navigation_menu) {
                bottomSheetOverlayView.show()
            } else {
                bottomSheetOverlayView.hide()
            }
        }

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .add(R.id.sheet1Container, PlaybackFragment(), "PlaybackFragment")
                .add(R.id.sheet1PeekView, MiniPlaybackFragment(), "MiniPlaybackFragment")
                .add(R.id.sheet2Container, QueueFragment.newInstance(), "QueueFragment")
                .commit()
        } else {
            multiSheetView.restoreSheet(savedInstanceState.getInt(STATE_CURRENT_SHEET))
        }

        // Update visible state of mini player
        queueWatcher.addCallback(this)

        multiSheetView.addSheetStateChangeListener(object : MultiSheetView.SheetStateChangeListener {

            override fun onSheetStateChanged(sheet: Int, state: Int) {
                updateBackPressListener()
            }

            override fun onSlide(sheet: Int, slideOffset: Float) {

            }
        })

        if (queueManager.getSize() == 0) {
            multiSheetView.hide(collapse = true, animate = false)
        }

        when (playbackPreferenceManager.songProvider) {
            PlaybackPreferenceManager.SongProvider.MediaStore -> {
                songRepository.populate(MediaStoreSongProvider(context!!.applicationContext)).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeBy(
                        onComplete = { Timber.v("Scan completed") },
                        onError = { throwable -> Timber.e(throwable, "Failed to scan library") })
            }
            PlaybackPreferenceManager.SongProvider.TagLib -> {
                compositeDisposable.add(
                    Single.fromCallable {
                        context?.applicationContext?.contentResolver?.persistedUriPermissions
                            ?.filter { uriPermission -> uriPermission.isReadPermission }
                            ?.flatMap { uriPermission ->
                                SafDirectoryHelper.buildFolderNodeTree(context!!.applicationContext.contentResolver, uriPermission.uri)?.getLeaves().orEmpty().map {
                                    (it as SafDirectoryHelper.DocumentNode).uri
                                }
                            }.orEmpty()
                    }
                        .flatMapCompletable { uris ->
                            songRepository.populate(TaglibSongProvider(context!!.applicationContext, fileScanner, uris))
                        }
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeBy(
                            onComplete = { Timber.v("Scan completed") },
                            onError = { throwable -> Timber.e(throwable, "Failed to scan library") })
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()

        updateBackPressListener()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_CURRENT_SHEET, multiSheetView.currentSheet)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        queueWatcher.removeCallback(this)
        compositeDisposable.clear()
        super.onDestroyView()
    }

    // Private

    private fun updateBackPressListener() {
        onBackPressCallback?.remove()

        if (bottomSheetOverlayView.state != BottomSheetBehavior.STATE_HIDDEN) {
            // Todo: Remove activity dependency.
            onBackPressCallback = activity!!.onBackPressedDispatcher.addCallback {
                bottomSheetOverlayView.hide()
            }
            return
        }

        if (multiSheetView.currentSheet != MultiSheetView.Sheet.NONE) {
            // Todo: Remove activity dependency.
            onBackPressCallback = activity!!.onBackPressedDispatcher.addCallback {
                multiSheetView.consumeBackPress()
            }
        }
    }

    // QueueChangeCallback Implementation

    override fun onQueueChanged() {
        if (queueManager.getSize() == 0) {
            multiSheetView.hide(collapse = true, animate = false)
        } else {
            multiSheetView.unhide(true)
        }
    }

    // BottomSheetOverlayView.Listener Implementation

    override fun onStateChanged(state: Int) {
        updateBackPressListener()
    }

    // Static

    companion object {
        const val TAG = "MainFragment"
        const val STATE_CURRENT_SHEET = "current_sheet"
    }

}

fun BottomNavigationView.setupWithNavController(navController: NavController, onItemSelected: (MenuItem) -> (Unit)) {

    setOnNavigationItemSelectedListener { item ->
        val didNavigate = NavigationUI.onNavDestinationSelected(item, navController)
        onItemSelected(item)
        didNavigate
    }

    fun matchDestination(destination: NavDestination, @IdRes destId: Int): Boolean {
        var currentDestination: NavDestination? = destination
        while (currentDestination!!.id != destId && currentDestination.parent != null) {
            currentDestination = currentDestination.parent
        }
        return currentDestination.id == destId
    }

    val weakReference = WeakReference(this)
    navController.addOnDestinationChangedListener(
        object : NavController.OnDestinationChangedListener {
            override fun onDestinationChanged(
                controller: NavController,
                destination: NavDestination,
                arguments: Bundle?
            ) {
                val view = weakReference.get()
                if (view == null) {
                    navController.removeOnDestinationChangedListener(this)
                    return
                }
                val menu = view.menu
                var h = 0
                val size = menu.size()
                while (h < size) {
                    val item = menu.getItem(h)
                    if (matchDestination(destination, item.itemId)) {
                        item.isChecked = true
                    }
                    h++
                }
            }
        })
}