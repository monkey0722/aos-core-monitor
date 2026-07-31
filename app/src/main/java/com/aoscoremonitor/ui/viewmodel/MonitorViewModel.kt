package com.aoscoremonitor.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/**
 * Obtains a [ViewModel] that needs a [Context], scoped to the current navigation entry.
 *
 * The context handed over is the application context, never the one from [LocalContext]. The
 * collectors outlive a single composition and hold onto whatever context they are given, so
 * passing the activity would have kept it alive past its own destruction.
 *
 * Scoping follows from where this is called: the navigation host installs a view model store per
 * back stack entry, so a screen's collectors stop when the user navigates away from it and are
 * kept across configuration changes. Previously each collector was built inside the composable
 * with `remember`, which meant a rotation threw away everything already gathered.
 */
@Composable
inline fun <reified VM : ViewModel> monitorViewModel(crossinline create: (Context) -> VM): VM {
    val applicationContext = LocalContext.current.applicationContext
    val factory = remember(applicationContext) {
        viewModelFactory { initializer { create(applicationContext) } }
    }
    return viewModel(factory = factory)
}
