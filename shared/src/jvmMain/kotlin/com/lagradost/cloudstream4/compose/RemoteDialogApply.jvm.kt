package com.lagradost.cloudstream4.compose

import androidx.compose.runtime.Composable

@Composable
actual fun isTvRemoteInput(): Boolean = false

@Composable
actual fun DialogApplyEffect(enabled: Boolean, oneShot: Boolean, apply: () -> Unit) = Unit
