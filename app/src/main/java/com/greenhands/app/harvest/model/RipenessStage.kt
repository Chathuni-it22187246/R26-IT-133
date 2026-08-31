package com.greenhands.app.harvest.model

/**
 * Visual ripeness classification for tomato fruit.
 * HSV and feature analysis will map into these stages later.
 */
enum class RipenessStage {
    GREEN,
    BREAKER,
    TURNING,
    PINK,
    LIGHT_RED,
    RED,
    UNKNOWN
}
