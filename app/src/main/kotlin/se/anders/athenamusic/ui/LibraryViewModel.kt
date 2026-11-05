package se.anders.athenamusic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import se.anders.athenamusic.data.server.AlbumDetail
import se.anders.athenamusic.data.server.Artist
import se.anders.athenamusic.data.server.ArtistDetail
import se.anders.athenamusic.data.server.ServerErrorKind
import se.anders.athenamusic.data.server.ServerException
import se.anders.athenamusic.data.server.SubsonicClient

/** A list that is loading, failed, or ready (FR-013). */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Failed(val kind: ServerErrorKind) : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
}

/** Loads library pages on demand and keeps them in memory only (FR-013). */
class LibraryViewModel : ViewModel() {
    private val _artists = MutableStateFlow<Load<List<Artist>>>(Load.Loading)
    val artists: StateFlow<Load<List<Artist>>> = _artists.asStateFlow()

    private val artistPages = mutableMapOf<String, MutableStateFlow<Load<ArtistDetail>>>()
    private val albumPages = mutableMapOf<String, MutableStateFlow<Load<AlbumDetail>>>()

    fun artists(client: SubsonicClient) {
        if (_artists.value is Load.Ready) return
        load(_artists) { client.artists() }
    }

    fun artist(client: SubsonicClient, id: String): StateFlow<Load<ArtistDetail>> {
        val flow = artistPages.getOrPut(id) { MutableStateFlow(Load.Loading) }
        if (flow.value !is Load.Ready) load(flow) { client.artist(id) }
        return flow
    }

    fun album(client: SubsonicClient, id: String): StateFlow<Load<AlbumDetail>> {
        val flow = albumPages.getOrPut(id) { MutableStateFlow(Load.Loading) }
        if (flow.value !is Load.Ready) load(flow) { client.album(id) }
        return flow
    }

    fun retryArtists(client: SubsonicClient) { _artists.value = Load.Loading; load(_artists) { client.artists() } }
    fun retryArtist(client: SubsonicClient, id: String) { artistPages[id]?.let { it.value = Load.Loading; load(it) { client.artist(id) } } }
    fun retryAlbum(client: SubsonicClient, id: String) { albumPages[id]?.let { it.value = Load.Loading; load(it) { client.album(id) } } }

    /** Forget everything (sign-out): another account must not see this one's library. */
    fun clear() {
        _artists.value = Load.Loading
        artistPages.clear()
        albumPages.clear()
    }

    private fun <T> load(target: MutableStateFlow<Load<T>>, block: suspend () -> T) {
        viewModelScope.launch {
            target.value = try {
                Load.Ready(block())
            } catch (e: ServerException) {
                Load.Failed(e.kind)
            }
        }
    }
}
