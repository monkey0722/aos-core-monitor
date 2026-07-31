package com.aoscoremonitor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aoscoremonitor.diagnostics.jni.MountPoint
import com.aoscoremonitor.diagnostics.jni.NativeStorageInspector
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * The mount table, split into the filesystems that hold files and the kernel interfaces that do
 * not.
 *
 * The split is made once here rather than in each list: an Android mount table runs to well over a
 * hundred entries, nearly all of them cgroup and tmpfs mounts, and the screen would otherwise open
 * on a wall of them with /data somewhere below the fold.
 */
data class StorageMountsUiState(
    val filesystems: List<MountPoint> = emptyList(),
    val pseudoFilesystems: List<MountPoint> = emptyList(),
    val hasLoaded: Boolean = false
)

/** Polls the mount table every five seconds. */
class StorageMountsViewModel : ViewModel() {

    private val inspector = NativeStorageInspector()

    val uiState: StateFlow<StorageMountsUiState> = flow {
        while (true) {
            val mounts = inspector.read().orEmpty()
            emit(
                StorageMountsUiState(
                    // Fullest first, so a filesystem about to run out is the one in view.
                    filesystems = mounts.filter { it.isRealFilesystem }
                        .sortedByDescending { it.usedFraction ?: -1f },
                    pseudoFilesystems = mounts.filterNot { it.isRealFilesystem }
                        .sortedBy { it.target },
                    hasLoaded = true
                )
            )
            delay(REFRESH_INTERVAL_MS)
        }
    }.stateIn(viewModelScope, WhileScreenVisible, StorageMountsUiState())

    private companion object {
        // Free space moves in minutes, not seconds, and each poll runs a statvfs per mount.
        const val REFRESH_INTERVAL_MS = 5_000L
    }
}
