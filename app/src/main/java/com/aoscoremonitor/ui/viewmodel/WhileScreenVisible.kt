package com.aoscoremonitor.ui.viewmodel

import kotlinx.coroutines.flow.SharingStarted

/**
 * Sharing policy for readings that cost something to gather.
 *
 * Collection runs while a screen is actually watching and stops shortly after it stops watching,
 * so backgrounding the app also stops the logcat pipe, the `dumpsys` calls and the /proc polling.
 * The grace period covers a rotation, which briefly drops the subscriber and would otherwise
 * restart everything.
 *
 * Pair it with `collectAsStateWithLifecycle()` at the call site — plain `collectAsState()`
 * subscribes for as long as the composition exists and defeats this.
 */
internal val WhileScreenVisible = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000)
