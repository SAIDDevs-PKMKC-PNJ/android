package com.pkm.said.util
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

object PermissionUtils {

    fun requiredPermissions(): Array<String> {
        val list = mutableListOf<String>()
        // Mic untuk voice assistant
        list += Manifest.permission.RECORD_AUDIO
        // Kamera jika kamu butuh ambil foto profil/fitur kamera
        list += Manifest.permission.CAMERA
        // Notifikasi hanya Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        return list.toTypedArray()
    }

    fun allGranted(context: Context): Boolean {
        return requiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun splitGrantResults(
        context: Context,
        results: Map<String, Boolean>
    ): Triple<List<String>, List<String>, List<String>> {
        val granted = results.filterValues { it }.keys.toList()
        val denied = results.filterValues { !it }.keys.toList()
        val permanentlyDenied = denied.filter { perm ->
            // Permanently denied jika user pilih "Don't ask again"
            // Cek dengan shouldShowRequestPermissionRationale di Activity/Fragment.
            // Karena helper ini tidak punya Activity, kita kembalikan list denied;
            // caller yang menentukan "permanent" via shouldShowRequestPermissionRationale.
            false
        }
        return Triple(granted, denied, permanentlyDenied)
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    }
}