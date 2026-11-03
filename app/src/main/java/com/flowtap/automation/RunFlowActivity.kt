package com.flowtap.automation

import androidx.activity.ComponentActivity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.flowtap.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Singleton

/** Flow ids waiting to run once [MainActivity] is in front. */
@Singleton
class PendingRuns @Inject constructor() {
    private val ids = mutableListOf<Long>()

    @Synchronized fun add(id: Long) {
        if (id !in ids) ids += id
    }

    @Synchronized fun drain(): List<Long> = ids.toList().also { ids.clear() }
}

/**
 * Target of "Tap to run …" notifications. Not exported, so only FlowTap's own PendingIntents can
 * reach it; [MainActivity] is exported for file imports and must not accept run requests directly.
 */
@AndroidEntryPoint
class RunFlowActivity : ComponentActivity() {
    @Inject lateinit var pendingRuns: PendingRuns

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val flowId = intent.getLongExtra(EXTRA_FLOW_ID, -1L)
        if (flowId >= 0) pendingRuns.add(flowId)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        finish()
    }

    companion object {
        private const val EXTRA_FLOW_ID = "flow_id"

        fun intent(context: Context, flowId: Long): Intent =
            Intent(context, RunFlowActivity::class.java).putExtra(EXTRA_FLOW_ID, flowId)
    }
}
