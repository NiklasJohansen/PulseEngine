package game.example
import engine.PulseEngine
import engine.data.Mouse

fun main()
{
    // Box data class
    data class Box(var x: Float, var y: Float, val w: Float, val h: Float, var vel: Float, val shade: Float)

    // List of boxes
    val boxes = mutableListOf(Box(500f, 100f,50f, 50f, 1f, 1f))

    // Main draw loop
    PulseEngine.draw {

        // Add new boxes on mouse click
        if(input.wasClicked(Mouse.LEFT))
            boxes.add(Box(input.xMouse - 25, input.yMouse, 50f, 50f, 1f, Math.random().toFloat()))

        boxes.forEach { box ->

            // Update velocity and position of each box
            box.y += (0.1f * box.vel++)

            // Bounce
            box.vel *= if (box.y >= window.height) { box.y = window.height.toFloat(); -0.95f } else 1f

            // Set color and draw box
            gfx.setColor(box.shade, box.shade, box.shade)
            gfx.drawQuad(box.x, box.y, box.w, -box.h)
        }
    }
}


