package se.anders.athenamusic.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.launch
import se.anders.athenamusic.domain.filterArtists
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import se.anders.athenamusic.R
import se.anders.athenamusic.data.server.Album
import se.anders.athenamusic.data.server.AlbumDetail
import se.anders.athenamusic.data.server.Artist
import se.anders.athenamusic.data.server.ArtistDetail
import se.anders.athenamusic.data.server.Track
import se.anders.athenamusic.ui.Load
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import se.anders.athenamusic.ui.components.ArtImage
import se.anders.athenamusic.ui.components.LoadView
import se.anders.athenamusic.ui.components.ScreenHeader
import se.anders.athenamusic.ui.components.muted
import se.anders.athenamusic.ui.formatClock

/** FR-010 / FR-014: artists, alphabetical as the server returns them, with a search field above the list. */
@Composable
fun ArtistsScreen(
    load: Load<List<Artist>>,
    query: String,
    onQueryChange: (String) -> Unit,
    listState: LazyListState,
    onRetry: () -> Unit,
    onOpen: (Artist) -> Unit,
    onSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.library_title), action = {
            TextButton(onClick = onSettings) { Text(stringResource(R.string.library_settings)) }
        })
        LoadView(load, onRetry) { artists ->
            if (artists.isEmpty()) {
                Text(stringResource(R.string.library_empty), color = muted(), modifier = Modifier.padding(24.dp))
                return@LoadView
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { onQueryChange(it); scope.launch { listState.scrollToItem(0) } },
                    singleLine = true,
                    label = { Text(stringResource(R.string.search_artists)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    modifier = Modifier.weight(1f),
                )
                if (query.isNotEmpty()) {
                    TextButton(onClick = { onQueryChange(""); focus.clearFocus(); scope.launch { listState.scrollToItem(0) } }) { Text(stringResource(R.string.search_clear)) }
                }
            }
            val shown = filterArtists(artists, query)
            if (shown.isEmpty()) {
                Text(stringResource(R.string.search_no_match), color = muted(), modifier = Modifier.padding(24.dp))
            } else LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(shown, key = { it.id }) { artist ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .clickable(onClickLabel = stringResource(R.string.a11y_open_artist), role = Role.Button) { onOpen(artist) }
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(artist.name, style = MaterialTheme.typography.titleMedium)
                            Text(pluralStringResource(R.plurals.albums_count, artist.albumCount, artist.albumCount), style = MaterialTheme.typography.bodyMedium, color = muted())
                        }
                    }
                }
            }
        }
    }
}

/** FR-011: albums of one artist. */
@Composable
fun ArtistScreen(title: String, load: Load<ArtistDetail>, listState: LazyListState, onRetry: () -> Unit, onOpen: (Album) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, onBack)
        LoadView(load, onRetry) { detail ->
            if (detail.albums.isEmpty()) Text(stringResource(R.string.library_empty), color = muted(), modifier = Modifier.padding(24.dp))
            else LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(detail.albums, key = { it.id }) { album ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 72.dp)
                            .clickable(onClickLabel = stringResource(R.string.a11y_open_album), role = Role.Button) { onOpen(album) }
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ArtImage(album.coverArt, 56.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                            Text(album.name, style = MaterialTheme.typography.titleMedium)
                            val tracks = pluralStringResource(R.plurals.tracks_count, album.songCount, album.songCount)
                            Text(listOfNotNull(album.year.takeIf { it > 0 }?.toString(), tracks).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = muted())
                        }
                    }
                }
            }
        }
    }
}

/** FR-012 / FR-020: tracks of one album; tapping a track plays the album from there. */
@Composable
fun AlbumScreen(title: String, load: Load<AlbumDetail>, listState: LazyListState, onRetry: () -> Unit, onPlay: (List<Track>, Int) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, onBack)
        LoadView(load, onRetry) { album ->
            if (album.tracks.isEmpty()) Text(stringResource(R.string.album_empty), color = muted(), modifier = Modifier.padding(24.dp))
            else {
              // FR-040: when the songs of an album have pictures of their own, each row shows its own.
              val perSong = album.tracks.map { it.coverArt }.distinct().size > 1
              LazyColumn(Modifier.fillMaxSize(), state = listState) {
                item {
                    Row(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        ArtImage(album.coverArt, 96.dp)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            if (album.artist.isNotEmpty()) Text(album.artist, style = MaterialTheme.typography.bodyMedium, color = muted())
                            Button(onClick = { onPlay(album.tracks, 0) }, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)) { Text(stringResource(R.string.album_play)) }
                        }
                    }
                }
                itemsIndexed(album.tracks, key = { _, t -> t.id }) { index, track ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .clickable(onClickLabel = stringResource(R.string.a11y_play_track), role = Role.Button) { onPlay(album.tracks, index) }
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (track.track > 0) track.track.toString() else "", color = muted(), modifier = Modifier.widthIn(min = 40.dp).padding(end = 8.dp))
                        if (perSong) { ArtImage(track.coverArt, 40.dp); Spacer(Modifier.width(12.dp)) }
                        Column(Modifier.weight(1f)) {
                            Text(track.title, style = MaterialTheme.typography.titleMedium)
                            // FR-012: the artist only when it differs from the album artist.
                            if (track.artist.isNotEmpty() && track.artist != album.artist) {
                                Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = muted())
                            }
                        }
                        if (track.duration > 0) Text(formatClock(track.duration * 1000L), style = MaterialTheme.typography.bodyMedium, color = muted(), modifier = Modifier.padding(start = 12.dp))
                    }
                }
              }
            }
        }
    }
}
