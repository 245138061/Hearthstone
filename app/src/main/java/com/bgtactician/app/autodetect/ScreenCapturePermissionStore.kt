package com.bgtactician.app.autodetect

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ScreenCapturePermission(
    val resultCode: Int,
    val data: Intent
)

object ScreenCapturePermissionStore {
    private val _isGranted = MutableStateFlow(false)
    val isGranted: StateFlow<Boolean> = _isGranted

    @Volatile
    private var permission: ScreenCapturePermission? = null
    @Volatile
    private var sessionActive: Boolean = false

    private fun syncGrantedState() {
        _isGranted.value = permission != null || sessionActive
    }

    @Synchronized
    fun update(resultCode: Int, data: Intent) {
        permission = ScreenCapturePermission(
            resultCode = resultCode,
            data = Intent(data)
        )
        syncGrantedState()
    }

    @Synchronized
    fun clear() {
        permission = null
        sessionActive = false
        syncGrantedState()
    }

    @Synchronized
    fun current(): ScreenCapturePermission? {
        val snapshot = permission ?: return null
        return ScreenCapturePermission(
            resultCode = snapshot.resultCode,
            data = Intent(snapshot.data)
        )
    }

    @Synchronized
    fun consume(): ScreenCapturePermission? {
        val snapshot = permission ?: return null
        permission = null
        syncGrantedState()
        return ScreenCapturePermission(
            resultCode = snapshot.resultCode,
            data = Intent(snapshot.data)
        )
    }

    @Synchronized
    fun markSessionActive(active: Boolean) {
        sessionActive = active
        syncGrantedState()
    }
}
