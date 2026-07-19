package com.micasong.player.ui.settings

import androidx.lifecycle.ViewModel
import com.micasong.player.data.update.UpdateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Drives the in-app update screen: check GitHub, download+install (spec: user request). */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val manager: UpdateManager,
) : ViewModel() {
    val state = manager.state
    fun check() = manager.check()
    fun downloadAndInstall() = manager.downloadAndInstall()
}
