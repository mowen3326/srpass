package app.srpass.kykfz01.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * How far past the top edge a list has to be dragged before its hidden space
 * opens. Deliberately a long pull: it must feel like a separate, intentional
 * action rather than something that happens while scrolling the list back up.
 */
internal const val PULL_THRESHOLD_DP = 170f

/**
 * Fraction of the finger's travel that is actually applied.
 *
 * Below 1 so the pull feels weighted. Moving 1:1 reads as a scrolling glitch
 * rather than as something the user is doing on purpose.
 */
private const val PULL_RESISTANCE = 0.6f

/**
 * State behind one list's pull gesture.
 *
 * Shared by all four lists because the pull has to behave identically in each:
 * a space that is easier (or harder) to reach in one module would tell anyone
 * watching *where* the user keeps things they do not want seen.
 */
internal class HiddenPull(
    val listState: LazyListState,
    /** Animated position, used only to draw the list following the finger. */
    val offset: Float,
    /**
     * Position as last commanded, with no animation lag.
     *
     * The threshold is measured against this and never against [offset]: the
     * animation is always chasing the commanded value, so accumulating the
     * animated one adds a lag to every frame of the drag and the finger then
     * has to travel several times further than the threshold to reach it.
     */
    val commanded: Float,
    private val setPullPx: (Float) -> Unit
) {
    fun pull(px: Float) = setPullPx(px)
    fun release() = setPullPx(0f)
}

@Composable
internal fun rememberHiddenPull(): HiddenPull {
    val listState = rememberLazyListState()
    var pullPx by remember { mutableFloatStateOf(0f) }
    // Animating toward the target is what lets the list settle back smoothly
    // when a drag ends short of the threshold, instead of snapping back.
    val offset by animateFloatAsState(targetValue = pullPx, label = "hidden-pull")

    /**
     * A drag can end without a fling -- letting go slowly leaves almost no
     * velocity -- and that path never reaches onPostFling, which is where the
     * pull used to be released. The list then stayed parked part-open, lock hint
     * showing, until the next gesture; worse, the leftover offset counted
     * towards the next pull and could cross the threshold on its own.
     *
     * Nested scroll has no "gesture ended" callback, so the settlement is taken
     * from the list's interactions instead: every drag they see ends in exactly
     * one Release or Cancel, whatever speed it was let go at.
     */
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            // A drag on a list emits DragInteraction, not PressInteraction --
            // listening only for the press meant a slow lift (no fling, so no
            // onPostFling either) left the row parked part-open: the next small
            // pull from there crossed the threshold on its own and opened the
            // hidden space without the user asking.
            val settled = when (interaction) {
                is PressInteraction.Release, is PressInteraction.Cancel -> true
                is DragInteraction.Stop, is DragInteraction.Cancel -> true
                else -> false
            }
            if (settled) pullPx = 0f
        }
    }
    return remember(listState, pullPx, offset) {
        HiddenPull(listState, offset, pullPx) { pullPx = it }
    }
}

/**
 * Turns "dragged past the top" into a request to open the hidden space.
 *
 * Built on [nestedScroll] rather than a hand-rolled drag: a list hands its
 * nested parent exactly the scroll it cannot consume itself, so there is no
 * race between this pull and the list's own scrolling, and no need to guess who
 * owns the gesture. It only reacts while the list is fully at its top, so
 * ordinary scrolling -- and the horizontal swipe of the pager these lists sit in
 * -- are untouched.
 *
 * Sign convention: in nested scroll, dragging *down* (which reveals content
 * above the current position) arrives as **positive** y, and dragging up --
 * whether to read on or to pull back from an unfinished gesture -- as negative.
 */
@Composable
internal fun Modifier.pullToOpenHiddenSpace(
    pull: HiddenPull,
    enabled: Boolean,
    thresholdPx: Float,
    onTrigger: () -> Unit
): Modifier {
    val enabledState by rememberUpdatedState(enabled)
    val triggerCallback by rememberUpdatedState(onTrigger)
    /**
     * The connection below is remembered across recompositions, so it would
     * otherwise keep the *first* pull instance -- whose offset is frozen at the
     * value it had then. Accumulating into a snapshot that never updates is how
     * a pull ends up unable to reach the threshold at all.
     */
    val currentPull by rememberUpdatedState(pull)

    val connection = remember(pull.listState, thresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!enabledState || source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y <= 0f) {
                    // Dragging back up: drop whatever was pulled instead of
                    // leaving the list parked part-open, or it would still be
                    // armed -- and could fire on its own -- the next time the
                    // top of the list is reached.
                    if (currentPull.commanded > 0f) currentPull.release()
                    return Offset.Zero
                }
                val listAtTop =
                    currentPull.listState.firstVisibleItemIndex == 0 &&
                        currentPull.listState.firstVisibleItemScrollOffset == 0
                if (!listAtTop) return Offset.Zero

                val total = currentPull.commanded + available.y * PULL_RESISTANCE
                if (total >= thresholdPx) {
                    currentPull.release()
                    triggerCallback()
                } else {
                    currentPull.pull(total)
                }
                // Consumed, or the list would rubber-band at the same time and
                // the user would watch two things fight over one drag.
                return Offset(0f, available.y)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // End of the drag: settle back, whether or not it went far
                // enough to open the space. Nothing here consumes the leftover
                // fling, so an ordinary flick keeps its momentum.
                currentPull.release()
                return Velocity.Zero
            }
        }
    }
    return this.nestedScroll(connection)
}

/** The pull distance in pixels that counts as "far enough". */
@Composable
internal fun rememberPullThresholdPx(): Float =
    with(LocalDensity.current) { PULL_THRESHOLD_DP.dp.toPx() }

/**
 * 只留数字。
 *
 * 卡号是带空格存的，而用户习惯照着卡面上带空格的样子搜 —— 只处理一边的话，
 * 输带空格的号码一条都搜不出来。
 */
internal fun String.digitsOnly(): String = filter(Char::isDigit)

/**
 * The discreet lock that fades in as the hidden space comes into reach.
 *
 * Deliberately a symbol and not a label: whoever is watching over the user's
 * shoulder cannot read what it means.
 */
@Composable
internal fun PullHint(progress: Float) {
    if (progress <= 0.01f) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 72.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            modifier = Modifier
                .size(40.dp)
                .alpha(progress),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Confirms moving an item in or out of a hidden space.
 *
 * Hiding is reversible, but the item then leaves both the list and search, so it
 * deserves an explicit decision rather than firing on the long press itself.
 *
 * Generic on purpose: the same confirmation covers passwords, notes, cards and
 * authenticator accounts, and the wording must not differ between them.
 */
@Composable
internal fun HiddenToggleDialog(
    name: String?,
    hidden: Boolean,
    noun: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (name == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hidden) "是否取消隐藏这个$noun？" else "是否隐藏这个$noun？") },
        text = { Text(name) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(if (hidden) "取消隐藏" else "隐藏") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/**
 * The empty state, as an *item* rather than a sibling Box: either way the
 * LazyColumn keeps owning the scroll, which is what lets the pull gesture still
 * be delivered when there is nothing to scroll -- and it must be, or a list that
 * does not pull would give away that it has nothing hidden.
 */
internal fun LazyListScope.emptyStateItem(text: String) {
    item {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillParentMaxHeight(0.75f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
