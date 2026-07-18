package android.zero.studio.qstiles.data.provider

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

val Context.tileDataStore by preferencesDataStore(name = "tile_prefs")