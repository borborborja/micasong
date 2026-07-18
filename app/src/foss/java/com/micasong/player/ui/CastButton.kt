package com.micasong.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * FOSS build ships no Cast stack, so the cast button renders nothing. The `full` flavor provides a
 * real MediaRouteButton via an identically-named composable in its own source set (spec §45).
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) = Unit
