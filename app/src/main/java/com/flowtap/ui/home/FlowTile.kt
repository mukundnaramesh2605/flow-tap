package com.flowtap.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowtap.domain.model.TapFlow
import com.flowtap.domain.run.RunState
import com.flowtap.ui.common.brush
import com.flowtap.ui.common.summary
import com.flowtap.ui.common.vector

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FlowTile(
    flow: TapFlow,
    runState: RunState?,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "tileScale",
    )
    val running = runState is RunState.Running

    Box(
        modifier = modifier
            .scale(scale)
            .clip(MaterialTheme.shapes.large)
            .background(flow.color.brush)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = "Run ${flow.name}",
                onLongClickLabel = "Edit ${flow.name}",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onRun()
                },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEdit()
                },
            ),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White.copy(alpha = 0.22f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(flow.icon.vector, contentDescription = null, tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit, enabled = !running, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.MoreHoriz,
                        contentDescription = "Edit ${flow.name}",
                        tint = Color.White.copy(alpha = 0.9f),
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = flow.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            TileStatus(flow = flow, runState = runState)
        }
        if (runState is RunState.Running) {
            val progress by animateFloatAsState(
                targetValue = (runState.stepIndex + 1f) / runState.totalSteps,
                label = "runProgress",
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
        }
    }
}

/** The subtitle line, which swaps between action count and live step feedback. */
@Composable
private fun TileStatus(flow: TapFlow, runState: RunState?) {
    val subtle = Color.White.copy(alpha = 0.85f)
    AnimatedContent(
        targetState = runState,
        contentKey = { state ->
            when (state) {
                is RunState.Running -> "step${state.stepIndex}"
                null -> "idle"
                else -> state::class.simpleName
            }
        },
        transitionSpec = {
            (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
        },
        label = "tileStatus",
    ) { state ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
            when (state) {
                null -> {
                    val count = flow.actions.size
                    Text(
                        text = if (count == 1) "1 action" else "$count actions",
                        style = MaterialTheme.typography.bodySmall,
                        color = subtle,
                    )
                    if (flow.triggers.any { it.enabled }) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Rounded.Schedule,
                            contentDescription = "Has an active automation",
                            tint = subtle,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                is RunState.Running -> Text(
                    // getOrNull: the flow may have been edited while this run was going.
                    text = "${state.stepIndex + 1}/${state.totalSteps}" +
                        (flow.actions.getOrNull(state.stepIndex)?.let { " · ${it.summary}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                is RunState.Finished -> when {
                    state.issues.isEmpty() -> StatusBadge(Icons.Rounded.CheckCircle, "Done")
                    state.allFailed -> StatusBadge(Icons.Rounded.Error, "Didn't run")
                    else -> StatusBadge(
                        Icons.Rounded.Info,
                        if (state.issues.size == 1) "Done · 1 note" else "Done · ${state.issues.size} notes",
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(icon: ImageVector, text: String) {
    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
    Spacer(Modifier.width(4.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
