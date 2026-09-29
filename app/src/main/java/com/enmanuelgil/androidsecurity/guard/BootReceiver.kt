package com.enmanuelgil.androidsecurity.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Al encender el móvil (o tras actualizar la app) vuelve a arrancar el guardia, si lo tenías activado. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if ((intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) &&
            GuardState.wanted(context)) {
            CamMicGuardService.start(context)
        }
    }
}
