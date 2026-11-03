package com.flowtap.data.actions

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.flowtap.domain.model.FlowAction
import com.flowtap.domain.run.Fix
import com.flowtap.domain.run.RunContext
import com.flowtap.domain.run.StepResult
import com.flowtap.system.ActivityLauncher
import com.flowtap.system.SystemAccess
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume

class CommunicationActions @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val access: SystemAccess,
    private val launcher: ActivityLauncher,
) {
    private val hasTelephony: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    suspend fun sendSms(action: FlowAction.SendSms): StepResult {
        if (!hasTelephony) return StepResult.Failed("This device can't send SMS")
        if (!access.hasPermission(Manifest.permission.SEND_SMS)) {
            return StepResult.Blocked("FlowTap needs permission to send SMS", Fix.GrantPermissions(listOf(Manifest.permission.SEND_SMS)))
        }
        val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        } ?: return StepResult.Failed("SMS isn't available on this device")

        val parts = sms.divideMessage(action.message)
        val resultCode = withTimeoutOrNull(SEND_TIMEOUT_MS) { sendAndAwait(sms, action.phoneNumber, parts) }
        return when (resultCode) {
            // No report within the timeout usually just means a slow network; the radio keeps trying.
            null, Activity.RESULT_OK -> StepResult.Done
            SmsManager.RESULT_ERROR_RADIO_OFF -> StepResult.Failed("Couldn't send: airplane mode or radio is off")
            SmsManager.RESULT_ERROR_NO_SERVICE -> StepResult.Failed("Couldn't send: no mobile signal")
            else -> StepResult.Failed("Couldn't send the SMS (error $resultCode)")
        }
    }

    /** Sends and waits for the last part's "sent" report. Returns its result code. */
    private suspend fun sendAndAwait(sms: SmsManager, number: String, parts: ArrayList<String>): Int =
        suspendCancellableCoroutine { cont ->
            val actionName = "${context.packageName}.SMS_SENT.${UUID.randomUUID()}"
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    runCatching { context.unregisterReceiver(this) }
                    if (cont.isActive) cont.resume(resultCode)
                }
            }
            ContextCompat.registerReceiver(context, receiver, IntentFilter(actionName), ContextCompat.RECEIVER_NOT_EXPORTED)
            cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }

            val sent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(actionName).setPackage(context.packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
            )
            // Only the last part reports back; earlier parts get no callback.
            val sentIntents = ArrayList<PendingIntent?>(parts.size).apply {
                repeat(parts.size - 1) { add(null) }
                add(sent)
            }
            try {
                sms.sendMultipartTextMessage(number, null, parts, sentIntents, null)
            } catch (_: Exception) {
                runCatching { context.unregisterReceiver(receiver) }
                if (cont.isActive) cont.resume(SmsManager.RESULT_ERROR_GENERIC_FAILURE)
            }
        }

    suspend fun call(action: FlowAction.PhoneCall, run: RunContext): StepResult {
        if (!hasTelephony) return StepResult.Failed("This device can't make phone calls")
        if (!access.hasPermission(Manifest.permission.CALL_PHONE)) {
            return StepResult.Blocked(
                "FlowTap needs permission to make calls",
                Fix.GrantPermissions(listOf(Manifest.permission.CALL_PHONE)),
            )
        }
        val intent = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", action.phoneNumber, null))
        return launcher.launch(intent, what = "a call to ${action.phoneNumber}", run = run)
    }

    private companion object {
        const val SEND_TIMEOUT_MS = 30_000L
    }
}
