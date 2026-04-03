package xyz.fkstrading.client.features.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.fkstrading.shared.auth.TailscaleAuthState
import xyz.fkstrading.shared.auth.TailscaleIdentity

// ─────────────────────────────────────────────────────────────────────────────
// Public entry point
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Auth gate screen shown before the main application scaffold.
 *
 * This is a **plain composable** (not a Voyager [cafe.adriel.voyager.core.screen.Screen])
 * so it can be embedded directly inside [xyz.fkstrading.client.App].
 *
 * Rendering depends on [authState]:
 * - [TailscaleAuthState.Loading]      → spinner + "Verifying…" caption
 * - [TailscaleAuthState.Connected]    → terminal "✓ Connected" card + "Enter App" button
 * - [TailscaleAuthState.NotConnected] → red "✗ Tailscale Required" card + "Retry" button
 * - [TailscaleAuthState.Error]        → same red card with the raw error message
 *
 * @param authState  The current auth state to render.
 * @param onRetry    Called when the user taps the "Retry" button in a
 *                   non-connected state.
 * @param onEnterApp Called when the user taps "Enter App" in the Connected state.
 * @param modifier   Optional [Modifier] applied to the root [Surface].
 */
@Composable
fun AuthScreen(
    authState: TailscaleAuthState,
    onRetry: () -> Unit,
    onEnterApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            // ── Terminal window chrome ──────────────────────────────────────
            TerminalChrome {
                when (authState) {
                    is TailscaleAuthState.Loading ->
                        LoadingContent()

                    is TailscaleAuthState.Connected ->
                        ConnectedContent(
                            identity = authState.identity,
                            onEnterApp = onEnterApp,
                        )

                    is TailscaleAuthState.NotConnected ->
                        NotConnectedContent(
                            reason = authState.reason,
                            isNetworkError = authState.isNetworkError,
                            onRetry = onRetry,
                        )

                    is TailscaleAuthState.Error ->
                        ErrorContent(
                            message = authState.message,
                            onRetry = onRetry,
                        )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Terminal chrome wrapper
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Draws the outer "terminal window" card that wraps every auth state.
 *
 * Renders:
 * - A dark [Card] with a subtle border matching the FKS outline colour.
 * - A fake title-bar row with three macOS-style traffic-light dots.
 * - A thin divider, then the [content] slot.
 */
@Composable
private fun TerminalChrome(content: @Composable ColumnScope.() -> Unit) {
    val borderColor = MaterialTheme.colorScheme.outline

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp),
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        // ── Fake title bar ──────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TrafficDot(Color(0xFFFF5F56)) // close
            TrafficDot(Color(0xFFFFBD2E)) // minimise
            TrafficDot(Color(0xFF27C93F)) // fullscreen

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "fks-auth — bash",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )

            Spacer(modifier = Modifier.weight(1f))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)

        // ── Dynamic content ─────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun TrafficDot(color: Color) {
    Surface(
        modifier = Modifier.size(12.dp),
        shape = RoundedCornerShape(50),
        color = color,
    ) {}
}

// ─────────────────────────────────────────────────────────────────────────────
// State renderers
// ─────────────────────────────────────────────────────────────────────────────

/** Spinner shown while the identity fetch is in-flight. */
@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        PromptLine(text = "tailscale status --json | fks-auth …")

        Spacer(modifier = Modifier.height(4.dp))

        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
            strokeWidth = 3.dp,
        )

        Text(
            text = "Verifying Tailscale connection…",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            textAlign = TextAlign.Center,
        )
    }
}

/** Shown when the peer is confirmed on the FKS tailnet. */
@Composable
private fun ConnectedContent(
    identity: TailscaleIdentity,
    onEnterApp: () -> Unit,
) {
    val green = MaterialTheme.colorScheme.primary // 0xFF00E676 in FksTheme

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── Status header ───────────────────────────────────────────────────
        PromptLine(text = "tailscale status")

        Text(
            text = "✓  Connected",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = green,
                fontSize = 18.sp,
            ),
        )

        HorizontalDivider(
            color = green.copy(alpha = 0.3f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(vertical = 2.dp),
        )

        // ── Identity fields ─────────────────────────────────────────────────
        IdentityRow(label = "user",    value = identity.identifier)
        IdentityRow(label = "name",    value = identity.friendlyName)
        identity.tailnet?.let { IdentityRow(label = "tailnet", value = it) }
        identity.device?.let  { IdentityRow(label = "device",  value = it) }
        identity.tailscaleIp?.let { IdentityRow(label = "ts-ip", value = it) }

        Spacer(modifier = Modifier.height(8.dp))

        // ── CTA ─────────────────────────────────────────────────────────────
        Button(
            onClick = onEnterApp,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = green,
                contentColor = Color.Black,
            ),
            shape = RoundedCornerShape(4.dp),
        ) {
            Text(
                text = "Enter App  →",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

/** Shown when the peer is not on the tailnet (or the backend is unreachable). */
@Composable
private fun NotConnectedContent(
    reason: String,
    isNetworkError: Boolean,
    onRetry: () -> Unit,
) {
    val red = MaterialTheme.colorScheme.secondary // 0xFFFF5252 in FksTheme

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PromptLine(text = "tailscale status")

        Text(
            text = "✗  Tailscale Required",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = red,
                fontSize = 18.sp,
            ),
        )

        HorizontalDivider(
            color = red.copy(alpha = 0.3f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(vertical = 2.dp),
        )

        // ── Reason ──────────────────────────────────────────────────────────
        Text(
            text = reason,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )

        // ── Network-error additional hint ────────────────────────────────────
        AnimatedVisibility(
            visible = isNetworkError,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
            ) {
                Text(
                    text = "⚠  Cannot reach backend. Check Tailscale is running.",
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // ── Instructions ─────────────────────────────────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                InstructionStep(step = "1", text = "Install Tailscale on this device")
                InstructionStep(step = "2", text = "Connect to the FKS tailnet")
                InstructionStep(step = "3", text = "Tap Retry below")
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── Retry ────────────────────────────────────────────────────────────
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = red,
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = red.copy(alpha = 0.7f),
            ),
            shape = RoundedCornerShape(4.dp),
        ) {
            Text(
                text = "↺  Retry",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

/** Shown on an unrecoverable / unexpected error. */
@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    // Reuse NotConnectedContent logic — display error message as the reason.
    NotConnectedContent(
        reason = message,
        isNetworkError = false,
        onRetry = onRetry,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Micro-components
// ─────────────────────────────────────────────────────────────────────────────

/** A dimmed "$ >" prompt line mimicking a terminal command. */
@Composable
private fun PromptLine(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            ),
        )
    }
}

/** A key = value row for identity fields. */
@Composable
private fun IdentityRow(label: String, value: String) {
    val labelWidth = 80.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.width(labelWidth),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        Text(
            text = "=",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.outline,
            ),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

/** A numbered instruction step. */
@Composable
private fun InstructionStep(step: String, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "[$step]",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}
