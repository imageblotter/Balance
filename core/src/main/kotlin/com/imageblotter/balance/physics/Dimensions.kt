package com.imageblotter.balance.physics

/** World layout, in the same units as [com.imageblotter.balance.model.PieceShape]. y = 0 is the table top. */
object Dimensions {
    const val PLATE_RADIUS = 7f
    const val PLATE_THICKNESS = 0.4f
    const val PLATE_MASS = 4f
    const val PLATE_CENTER_Y = 7f

    /** Height of the point the plate hangs from, above the plate centre. A higher pivot is more forgiving. */
    const val PIVOT_HEIGHT = 7.5f
    const val PIVOT_Y = PLATE_CENTER_Y + PIVOT_HEIGHT

    const val GRAVITY = 30f
    const val TABLE_HALF_SIZE = 30f
}
