package com.kyant.backdrop

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertNotNull
import org.junit.Test

class LensTest {
    // @Test
    // fun testCircleShapeCornerRadii() {
    //     val dummyScope = object : BackdropEffectScopeImpl() {
    //         override var size: Size = Size(200f, 200f)
    //         override var layoutDirection: LayoutDirection = LayoutDirection.Ltr
    //         override val shape: Shape = CircleShape
    //     }
    //     val radii = dummyScope.cornerRadii
    //     assertNotNull("Radii should not be null", radii)
    //     println("LensTest - Radii: ${radii?.joinToString()}")
    // }

    @Test
    fun testOutlineCornerRadii() {
        val shape = CircleShape
        val size = Size(200f, 200f)
        val layoutDirection = LayoutDirection.Ltr
        val density = object : Density {
            override val density: Float = 1f
            override val fontScale: Float = 1f
        }
        val outline = shape.createOutline(size, layoutDirection, density)
        if (outline is Outline.Rounded) {
            val rr = outline.roundRect
            val radii = floatArrayOf(
                rr.topLeftCornerRadius.x,
                rr.topRightCornerRadius.x,
                rr.bottomRightCornerRadius.x,
                rr.bottomLeftCornerRadius.x
            )
            println("Outline Radii: ${radii.joinToString()}")
        } else {
            println("Outline is not Outline.Rounded: $outline")
        }
    }
}
