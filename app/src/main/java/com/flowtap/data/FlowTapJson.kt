package com.flowtap.data

import kotlinx.serialization.json.Json

/** Shared JSON config for Room columns and export files. Lenient on read so newer files still load. */
val FlowTapJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    coerceInputValues = true
}
