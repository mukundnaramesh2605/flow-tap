package com.flowtap.domain.model

import kotlinx.serialization.Serializable

/** Stable keys for tile icons. Stored/exported by name, so never rename entries. */
@Serializable
enum class FlowIcon {
    Bolt, Moon, Sun, Car, Home, Work, Music, Headphones, Coffee, Fitness,
    Book, Bed, Phone, Wifi, Bluetooth, Flashlight, Heart, Star, Rocket, Leaf,
}

/** Stable keys for tile colors. Stored/exported by name, so never rename entries. */
@Serializable
enum class FlowColor {
    Violet, Indigo, Blue, Teal, Green, Lime, Amber, Orange, Coral, Pink, Slate,
}
