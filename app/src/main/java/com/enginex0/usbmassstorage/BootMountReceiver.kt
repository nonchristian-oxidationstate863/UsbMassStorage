package com.enginex0.usbmassstorage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.util.Log
import com.enginex0.usbmassstorage.daemon.DaemonClient
import com.enginex0.usbmassstorage.daemon.DeviceType
import com.enginex0.usbmassstorage.data.DeviceStore
import com.topjohnwu.superuser.Shell
import java.io.FileDescriptor

private const val TAG = "UsbMsBoot"

class BootMountReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val store = DeviceStore(context)
        val saved = store.load()
        if (saved.isEmpty()) return

        Log.d(TAG, "Boot completed, mounting ${saved.size} saved devices")

        val pending = goAsync()
        Thread {
            try {
                mountDevices(saved, context)
            } catch (e: Exception) {
                Log.e(TAG, "Boot mount failed", e)
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun mountDevices(
        devices: List<com.enginex0.usbmassstorage.daemon.DeviceInfo>,
        context: Context
    ) {
        val delays = longArrayOf(2_000, 4_000)
        var lastError: Exception? = null

        for (attempt in 0..delays.size) {
            try {
                doMount(devices, context)
                return
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Mount attempt ${attempt + 1} failed", e)
                if (attempt < delays.size) Thread.sleep(delays[attempt])
            }
        }
        throw lastError!!
    }

    private fun doMount(
        devices: List<com.enginex0.usbmassstorage.daemon.DeviceInfo>,
        context: Context
    ) {
        DaemonClient().use { client ->
            val active = client.getMassStorage()
            if (active.isNotEmpty()) {
                Log.d(TAG, "${active.size} devices already mounted, skipping")
                return
            }

            val allFileScheme = devices.all { it.uri.scheme == "file" }
            val noneFileScheme = devices.none { it.uri.scheme == "file" }

            if (allFileScheme) {
                val pathDevices = devices.map { info ->
                    Triple(
                        info.uri.path!!,
                        info.type == DeviceType.CDROM,
                        info.type != DeviceType.DISK_RW,
                    )
                }
                client.setMassStoragePaths(pathDevices)
                Log.d(TAG, "Mounted ${pathDevices.size} devices at boot via path protocol")
            } else if (noneFileScheme) {
                val pfds = mutableListOf<ParcelFileDescriptor>()
                try {
                    val fdPairs = mutableListOf<Pair<FileDescriptor, DeviceType>>()
                    for (info in devices) {
                        val mode = if (info.type == DeviceType.DISK_RW) "rw" else "r"
                        val pfd = context.contentResolver.openFileDescriptor(info.uri, mode)
                        if (pfd == null) {
                            Log.w(TAG, "Failed to open FD for ${info.uri} — skipping device")
                            continue
                        }
                        pfds.add(pfd)
                        fdPairs.add(pfd.fileDescriptor to info.type)
                    }
                    if (fdPairs.isNotEmpty()) {
                        client.setMassStorage(fdPairs)
                        Log.d(TAG, "Mounted ${fdPairs.size} devices at boot via FD protocol")
                    }
                } finally {
                    pfds.forEach { try { it.close() } catch (_: Exception) {} }
                }
            } else {
                Log.e(TAG, "Cannot mix file:// and content:// URIs at boot — skipping mount")
            }
        }
    }
}
