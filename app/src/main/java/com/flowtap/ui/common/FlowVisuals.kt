package com.flowtap.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.flowtap.domain.model.FlowColor
import com.flowtap.domain.model.FlowIcon

val FlowIcon.vector: ImageVector
    get() = when (this) {
        FlowIcon.Bolt -> Icons.Rounded.Bolt
        FlowIcon.Moon -> Icons.Rounded.Bedtime
        FlowIcon.Sun -> Icons.Rounded.WbSunny
        FlowIcon.Car -> Icons.Rounded.DirectionsCar
        FlowIcon.Home -> Icons.Rounded.Home
        FlowIcon.Work -> Icons.Rounded.Work
        FlowIcon.Music -> Icons.Rounded.MusicNote
        FlowIcon.Headphones -> Icons.Rounded.Headphones
        FlowIcon.Coffee -> Icons.Rounded.Coffee
        FlowIcon.Fitness -> Icons.Rounded.FitnessCenter
        FlowIcon.Book -> Icons.AutoMirrored.Rounded.MenuBook
        FlowIcon.Bed -> Icons.Rounded.Hotel
        FlowIcon.Phone -> Icons.Rounded.Phone
        FlowIcon.Wifi -> Icons.Rounded.Wifi
        FlowIcon.Bluetooth -> Icons.Rounded.Bluetooth
        FlowIcon.Flashlight -> Icons.Rounded.FlashlightOn
        FlowIcon.Heart -> Icons.Rounded.Favorite
        FlowIcon.Star -> Icons.Rounded.Star
        FlowIcon.Rocket -> Icons.Rounded.RocketLaunch
        FlowIcon.Leaf -> Icons.Rounded.Spa
    }

/** Gradient endpoints for a tile. Saturated enough that white text reads on both themes. */
private val FlowColor.stops: Pair<Color, Color>
    get() = when (this) {
        FlowColor.Violet -> Color(0xFF7B5CFF) to Color(0xFF5038E0)
        FlowColor.Indigo -> Color(0xFF4F5BD5) to Color(0xFF2E2F8F)
        FlowColor.Blue -> Color(0xFF3D8BFF) to Color(0xFF1F5FD1)
        FlowColor.Teal -> Color(0xFF14B8A6) to Color(0xFF0B7F75)
        FlowColor.Green -> Color(0xFF34B36B) to Color(0xFF1E8048)
        FlowColor.Lime -> Color(0xFF8DBE2A) to Color(0xFF5E8A12)
        FlowColor.Amber -> Color(0xFFF5A623) to Color(0xFFD97A06)
        FlowColor.Orange -> Color(0xFFFF7A3D) to Color(0xFFE0501A)
        FlowColor.Coral -> Color(0xFFFF6B6B) to Color(0xFFD9425A)
        FlowColor.Pink -> Color(0xFFEC5BA6) to Color(0xFFB8327D)
        FlowColor.Slate -> Color(0xFF5E6B7E) to Color(0xFF39434F)
    }

val FlowColor.primary: Color get() = stops.first

val FlowColor.brush: Brush
    get() = stops.let { (start, end) -> Brush.linearGradient(listOf(start, end)) }
