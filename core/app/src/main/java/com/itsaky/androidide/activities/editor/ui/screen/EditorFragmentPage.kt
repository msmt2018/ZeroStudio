package com.itsaky.androidide.activities.editor.ui.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.Fragment
import androidx.fragment.compose.AndroidFragment
import com.itsaky.androidide.activities.editor.ui.state.EditorPage

/** Official Fragment interoperability; creation, restoration and removal belong to AndroidX. */
@Composable
fun EditorFragmentPage(
    page: EditorPage.FragmentPage,
    onReady: (Fragment) -> Unit = {},
    onReleased: (Fragment) -> Unit = {},
) {
    var instance by remember(page.id) { mutableStateOf<Fragment?>(null) }
    val release by rememberUpdatedState(onReleased)
    AndroidFragment(
        clazz = page.fragmentClass,
        arguments = page.arguments,
        modifier = Modifier.fillMaxSize(),
        onUpdate = { instance = it; onReady(it) },
    )
    DisposableEffect(instance) {
        val fragment = instance
        onDispose { fragment?.let(release) }
    }
}
