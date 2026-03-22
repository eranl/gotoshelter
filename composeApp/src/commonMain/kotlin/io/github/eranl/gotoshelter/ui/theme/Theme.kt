/*
 * Copyright 2026 Eran L.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.eranl.gotoshelter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
  primary = PrimaryRed,
  onPrimary = OnPrimaryWhite,
  primaryContainer = PrimaryContainerRed,
  onPrimaryContainer = OnPrimaryContainerRed,
  secondary = SecondaryOrange,
  onSecondary = OnSecondaryWhite,
  secondaryContainer = SecondaryContainerOrange,
  onSecondaryContainer = OnSecondaryContainerOrange,
  tertiary = TertiaryAmber,
  onTertiary = OnTertiaryAmber,
  tertiaryContainer = TertiaryContainerAmber,
  onTertiaryContainer = OnTertiaryContainerAmber,
  error = ErrorRed,
  background = BackgroundLight,
  surface = SurfaceLight,
  onBackground = OnBackgroundLight,
  onSurface = OnBackgroundLight,
)

private val DarkColorScheme = darkColorScheme(
  primary = PrimaryRed,
  onPrimary = OnPrimaryWhite,
  primaryContainer = PrimaryContainerRed,
  onPrimaryContainer = OnPrimaryContainerRed,
  secondary = SecondaryOrange,
  onSecondary = OnSecondaryWhite,
  secondaryContainer = SecondaryContainerOrange,
  onSecondaryContainer = OnSecondaryContainerOrange,
  tertiary = TertiaryAmber,
  onTertiary = OnTertiaryAmber,
  tertiaryContainer = TertiaryContainerAmber,
  onTertiaryContainer = OnTertiaryContainerAmber,
  background = BackgroundDark,
  surface = SurfaceDark,
  onBackground = OnBackgroundDark,
  onSurface = OnBackgroundDark,
)

@Composable
expect fun GoToShelterTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit
)

@Composable
fun CommonGoToShelterTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
