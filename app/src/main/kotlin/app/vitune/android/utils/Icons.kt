/*
 * Copyright (C) 2026 twixxty
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package app.vitune.android.utils

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import app.vitune.android.R
import app.vitune.android.preferences.AppearancePreferences

@DrawableRes
fun resolveAppIcon(@DrawableRes id: Int, useMaterial: Boolean = AppearancePreferences.useMaterialSymbols): Int {
    if (!useMaterial) return id
    return when (id) {
        R.drawable.search -> R.drawable.ms_search
        R.drawable.heart -> R.drawable.ms_favorite
        R.drawable.heart_outline -> R.drawable.ms_favorite_border
        R.drawable.play -> R.drawable.ms_play
        R.drawable.pause -> R.drawable.ms_pause
        R.drawable.play_skip_forward -> R.drawable.ms_skip_next
        R.drawable.play_skip_back -> R.drawable.ms_skip_prev
        R.drawable.shuffle -> R.drawable.ms_shuffle
        R.drawable.repeat -> R.drawable.ms_repeat
        R.drawable.repeat_on -> R.drawable.ms_repeat_on
        R.drawable.settings -> R.drawable.ms_settings
        R.drawable.musical_notes -> R.drawable.ms_music_note
        R.drawable.library -> R.drawable.ms_library
        R.drawable.trending, R.drawable.trending_up -> R.drawable.ms_trending_up
        R.drawable.history -> R.drawable.ms_history
        R.drawable.playlist -> R.drawable.ms_playlist
        R.drawable.person -> R.drawable.ms_person
        R.drawable.disc -> R.drawable.ms_disc
        R.drawable.lyrics -> R.drawable.ms_lyrics
        R.drawable.color_palette -> R.drawable.ms_palette
        R.drawable.chevron_back -> R.drawable.ms_arrow_back
        R.drawable.chevron_forward -> R.drawable.ms_chevron_forward
        R.drawable.arrow_forward -> R.drawable.ms_arrow_forward
        R.drawable.arrow_up -> R.drawable.ms_arrow_up
        R.drawable.chevron_down -> R.drawable.ms_chevron_down
        R.drawable.chevron_up -> R.drawable.ms_chevron_up
        R.drawable.share_social -> R.drawable.ms_share
        R.drawable.trash, R.drawable.delete -> R.drawable.ms_delete
        R.drawable.download -> R.drawable.ms_download
        R.drawable.ellipsis_horizontal -> R.drawable.ms_more_horiz
        R.drawable.close -> R.drawable.ms_close
        R.drawable.server -> R.drawable.ms_server
        R.drawable.sync -> R.drawable.ms_sync
        R.drawable.group -> R.drawable.ms_group
        R.drawable.information, R.drawable.information_circle_outline -> R.drawable.ms_info
        R.drawable.equalizer -> R.drawable.ms_equalizer
        R.drawable.pencil -> R.drawable.ms_edit
        R.drawable.star -> R.drawable.ms_star
        R.drawable.radio -> R.drawable.ms_radio
        R.drawable.time -> R.drawable.ms_schedule
        R.drawable.list -> R.drawable.ms_list
        R.drawable.enqueue -> R.drawable.ms_queue_music
        R.drawable.reorder -> R.drawable.ms_reorder
        R.drawable.volume_up -> R.drawable.ms_volume_up
        R.drawable.volume_mute -> R.drawable.ms_volume_off
        R.drawable.expand -> R.drawable.ms_fullscreen
        R.drawable.contract -> R.drawable.ms_fullscreen_exit
        else -> id
    }
}

@Composable
@ReadOnlyComposable
@DrawableRes
fun getAppIcon(@DrawableRes id: Int, useMaterial: Boolean = AppearancePreferences.useMaterialSymbols): Int =
    resolveAppIcon(id, useMaterial)
