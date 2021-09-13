package com.guichaguri.trackplayer.service

import android.content.Intent
import android.os.*
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.doublesymmetry.kotlinaudio.models.AudioPlayerState
import com.doublesymmetry.kotlinaudio.players.QueuedAudioPlayer
import com.facebook.react.HeadlessJsTaskService
import com.facebook.react.bridge.Arguments
import com.facebook.react.jstasks.HeadlessJsTaskConfig
import com.guichaguri.trackplayer.model.State
import com.guichaguri.trackplayer.model.Track
import com.guichaguri.trackplayer.model.TrackAudioItem
import com.guichaguri.trackplayer.module_old.MusicEvents
import com.guichaguri.trackplayer.module_old.MusicEvents.Companion.EVENT_INTENT
import com.orhanobut.logger.Logger
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit

class MusicService : HeadlessJsTaskService() {
    private lateinit var player: QueuedAudioPlayer
    private val handler = Handler(Looper.getMainLooper())

    private val serviceScope = MainScope()

    val tracks: List<Track>
        get() = player.items.map { (it as TrackAudioItem).track }

    val currentTrack
        get() = (player.currentItem as TrackAudioItem).track

    var repeatMode: QueuedAudioPlayer.RepeatMode
        get() = player.repeatMode
        set(value) {
            handler.post {
                player.repeatMode = value
            }
        }

    val event get() = player.event

    override fun onCreate() {
        handler.post { player = QueuedAudioPlayer(this) }
        observeEvents()
        super.onCreate()
    }

    fun setUp() {

    }

    fun add(tracks: List<Track>, playWhenReady: Boolean = true) {
        val items = tracks.map { it.toAudioItem() }
        handler.post { player.add(items, playWhenReady) }
    }

    fun remove(indexes: List<Int>) {
        handler.post { player.remove(indexes) }
    }

    fun play() {
        handler.post { player.play() }
    }

    fun pause() {
        handler.post { player.pause() }
    }

    fun destroy() {
        handler.post { player.stop() }
        handler.removeMessages(0)
        stopSelf()
    }

    fun removeUpcomingTracks() {
        handler.post { player.removeUpcomingItems() }
    }

    fun removePreviousTracks() {
        handler.post { player.removePreviousItems() }
    }

    fun skipToNext() {
        handler.post { player.next() }
    }

    fun skipToPrevious() {
        handler.post { player.previous() }
    }

    fun getCurrentTrackIndex(callback: (Int) -> Unit) {
        handler.post {
            callback(player.currentIndex)
        }
    }

    fun getDurationInSeconds(callback: (Double) -> Unit) {
        handler.post {
            callback(TimeUnit.MILLISECONDS.toSeconds(player.duration).toDouble())
        }
    }

    fun getPositionInSeconds(callback: (Double) -> Unit) {
        handler.post {
            callback(TimeUnit.MILLISECONDS.toSeconds(player.position).toDouble())
        }
    }

    fun getBufferedPositionInSeconds(callback: (Double) -> Unit) {
        handler.post {
            callback(TimeUnit.MILLISECONDS.toSeconds(player.bufferedPosition).toDouble())
        }
    }

    private fun observeEvents() {
        serviceScope.launch {
            event.stateChange.collect {
                val bundle = Bundle()
                Logger.d(it)

                when (it) {
                    AudioPlayerState.PLAYING -> {
                        bundle.putInt(STATE_KEY, State.Playing.value)
                        emit(MusicEvents.BUTTON_PLAY, null)
                    }
                    AudioPlayerState.PAUSED -> {
                        bundle.putInt(STATE_KEY, State.Paused.value)
                        emit(MusicEvents.BUTTON_PAUSE, null)
                    }
                    AudioPlayerState.READY, AudioPlayerState.IDLE -> {
                        bundle.putInt(STATE_KEY, State.Ready.value)
                    }
                    AudioPlayerState.BUFFERING -> {
                        bundle.putInt(STATE_KEY, State.Buffering.value)
                    }
                    AudioPlayerState.ENDED -> {
                        if (player.nextItem == null) {
                            if (player.previousIndex != null) bundle.putInt(TRACK_KEY, player.previousIndex!!)
                            emit(MusicEvents.PLAYBACK_QUEUE_ENDED, null)
                        }
                    }
                }

                emit(MusicEvents.PLAYBACK_STATE, bundle)
            }
        }

        serviceScope.launch {
            event.audioItemTransition.collect {
                handler.post {
                    val bundle = Bundle().apply {
                        putDouble(POSITION_KEY, 0.0)
                    }

                    bundle.putInt(NEXT_TRACK_KEY, player.currentIndex)

                    emit(MusicEvents.PLAYBACK_TRACK_CHANGED, bundle)
                }
            }
        }
    }

    private fun emit(event: String?, data: Bundle?) {
        val intent = Intent(EVENT_INTENT)
        intent.putExtra(EVENT_KEY, event)
        if (data != null) intent.putExtra(DATA_KEY, data)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun getTaskConfig(intent: Intent?): HeadlessJsTaskConfig {
        return HeadlessJsTaskConfig(TASK_KEY, Arguments.createMap(), 0, true)
    }

    override fun onHeadlessJsTaskFinish(taskId: Int) {
        // Overridden to prevent the service from being terminated
    }

    override fun onBind(intent: Intent?): IBinder {
        return MusicBinder()
    }

    inner class MusicBinder : Binder() {
        val service = this@MusicService
    }

    companion object {
        const val STATE_KEY = "state"
        const val EVENT_KEY = "event"
        const val DATA_KEY = "data"
        const val TRACK_KEY = "track"
        const val NEXT_TRACK_KEY = "nextTrack"
        const val POSITION_KEY = "position"

        const val TASK_KEY = "TrackPlayer"
    }
}