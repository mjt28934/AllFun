package app.allfun.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The app's own monoline set: 24dp, 1.75dp stroke, round caps and joins, no filled tiles.
 * Only the glyphs the chrome actually needs live here; deeper screens can still borrow a
 * glyph from the icon library without changing the visual language.
 */
object AFIcons {

    val Clapper: ImageVector = stroke("Clapper") {
        moveTo(3f, 9f)
        lineTo(21f, 9f)
        lineTo(21f, 19f)
        arcTo(2f, 2f, 0f, false, true, 19f, 21f)
        lineTo(5f, 21f)
        arcTo(2f, 2f, 0f, false, true, 3f, 19f)
        close()
        moveTo(3f, 9f)
        lineTo(4.5f, 5.2f)
        arcTo(1.6f, 1.6f, 0f, false, true, 6.6f, 4.3f)
        lineTo(20f, 8f)
        moveTo(7.2f, 5f)
        lineTo(9.6f, 9.4f)
        moveTo(12.2f, 6.3f)
        lineTo(14.6f, 10.7f)
        moveTo(17.2f, 7.7f)
        lineTo(19.6f, 12.1f)
    }

    val Book: ImageVector = stroke("Book") {
        moveTo(12f, 6.2f)
        curveTo(10f, 4.6f, 6.4f, 4.2f, 3f, 5f)
        lineTo(3f, 19f)
        curveTo(6.4f, 18.2f, 10f, 18.6f, 12f, 20f)
        curveTo(14f, 18.6f, 17.6f, 18.2f, 21f, 19f)
        lineTo(21f, 5f)
        curveTo(17.6f, 4.2f, 14f, 4.6f, 12f, 6.2f)
        close()
        moveTo(12f, 6.2f)
        lineTo(12f, 20f)
    }

    val Manga: ImageVector = stroke("Manga") {
        moveTo(6f, 3.5f)
        lineTo(15.5f, 3.5f)
        lineTo(20f, 8f)
        lineTo(20f, 20.5f)
        lineTo(6f, 20.5f)
        close()
        moveTo(15.5f, 3.5f)
        lineTo(15.5f, 8f)
        lineTo(20f, 8f)
        moveTo(9.5f, 12.5f)
        lineTo(16.5f, 12.5f)
        moveTo(9.5f, 16.5f)
        lineTo(14f, 16.5f)
    }

    val Stack: ImageVector = stroke("Stack") {
        moveTo(12f, 3f)
        lineTo(21f, 8f)
        lineTo(12f, 13f)
        lineTo(3f, 8f)
        close()
        moveTo(3f, 12f)
        lineTo(12f, 17f)
        lineTo(21f, 12f)
        moveTo(3f, 16f)
        lineTo(12f, 21f)
        lineTo(21f, 16f)
    }

    val Sliders: ImageVector = stroke("Sliders") {
        moveTo(4f, 7f)
        lineTo(20f, 7f)
        moveTo(4f, 12f)
        lineTo(20f, 12f)
        moveTo(4f, 17f)
        lineTo(20f, 17f)
        circle(9f, 7f, 2.2f)
        circle(15f, 12f, 2.2f)
        circle(7.5f, 17f, 2.2f)
    }

    val Search: ImageVector = stroke("Search") {
        circle(10.5f, 10.5f, 6.5f)
        moveTo(15.5f, 15.5f)
        lineTo(21f, 21f)
    }

    val Filter: ImageVector = stroke("Filter") {
        moveTo(4f, 6.5f)
        lineTo(20f, 6.5f)
        moveTo(7f, 12f)
        lineTo(17f, 12f)
        moveTo(10f, 17.5f)
        lineTo(14f, 17.5f)
    }

    val More: ImageVector = stroke("More") {
        circle(5.5f, 12f, 1.3f)
        circle(12f, 12f, 1.3f)
        circle(18.5f, 12f, 1.3f)
    }

    val Play: ImageVector = filled("Play") {
        moveTo(8f, 5.5f)
        lineTo(19f, 12f)
        lineTo(8f, 18.5f)
        close()
    }

    val Pause: ImageVector = stroke("Pause") {
        moveTo(9f, 5f)
        lineTo(9f, 19f)
        moveTo(15f, 5f)
        lineTo(15f, 19f)
    }

    val Download: ImageVector = stroke("Download") {
        moveTo(12f, 3.5f)
        lineTo(12f, 15f)
        moveTo(7.5f, 10.5f)
        lineTo(12f, 15f)
        lineTo(16.5f, 10.5f)
        moveTo(4f, 17f)
        lineTo(4f, 19f)
        arcTo(2f, 2f, 0f, false, false, 6f, 21f)
        lineTo(18f, 21f)
        arcTo(2f, 2f, 0f, false, false, 20f, 19f)
        lineTo(20f, 17f)
    }

    val Heart: ImageVector = stroke("Heart") {
        moveTo(12f, 20.2f)
        curveTo(12f, 20.2f, 3f, 14.6f, 3f, 8.6f)
        curveTo(3f, 5.6f, 5.5f, 3.2f, 8.5f, 3.2f)
        curveTo(10.2f, 3.2f, 11.4f, 4f, 12f, 5f)
        curveTo(12.6f, 4f, 13.8f, 3.2f, 15.5f, 3.2f)
        curveTo(18.5f, 3.2f, 21f, 5.6f, 21f, 8.6f)
        curveTo(21f, 14.6f, 12f, 20.2f, 12f, 20.2f)
        close()
    }

    val HeartFilled: ImageVector = filled("HeartFilled") {
        moveTo(12f, 20.2f)
        curveTo(12f, 20.2f, 3f, 14.6f, 3f, 8.6f)
        curveTo(3f, 5.6f, 5.5f, 3.2f, 8.5f, 3.2f)
        curveTo(10.2f, 3.2f, 11.4f, 4f, 12f, 5f)
        curveTo(12.6f, 4f, 13.8f, 3.2f, 15.5f, 3.2f)
        curveTo(18.5f, 3.2f, 21f, 5.6f, 21f, 8.6f)
        curveTo(21f, 14.6f, 12f, 20.2f, 12f, 20.2f)
        close()
    }

    val Check: ImageVector = stroke("Check") {
        moveTo(4.5f, 12.5f)
        lineTo(9.5f, 17.5f)
        lineTo(19.5f, 6.5f)
    }

    val Close: ImageVector = stroke("Close") {
        moveTo(6f, 6f)
        lineTo(18f, 18f)
        moveTo(18f, 6f)
        lineTo(6f, 18f)
    }

    val Back: ImageVector = stroke("Back") {
        moveTo(20f, 12f)
        lineTo(4f, 12f)
        moveTo(10f, 6f)
        lineTo(4f, 12f)
        lineTo(10f, 18f)
    }

    val Forward: ImageVector = stroke("Forward") {
        moveTo(4f, 12f)
        lineTo(20f, 12f)
        moveTo(14f, 6f)
        lineTo(20f, 12f)
        lineTo(14f, 18f)
    }

    val ChevronRight: ImageVector = stroke("ChevronRight") {        moveTo(9.5f, 5f)
        lineTo(16.5f, 12f)
        lineTo(9.5f, 19f)
    }

    val ChevronDown: ImageVector = stroke("ChevronDown") {
        moveTo(5f, 9.5f)
        lineTo(12f, 16.5f)
        lineTo(19f, 9.5f)
    }

    val Refresh: ImageVector = stroke("Refresh") {
        moveTo(20f, 12f)
        arcTo(8f, 8f, 0f, true, true, 16.2f, 5.5f)
        moveTo(16.2f, 2.4f)
        lineTo(16.2f, 6.2f)
        lineTo(20f, 6.2f)
    }

    val Sort: ImageVector = stroke("Sort") {
        moveTo(7f, 4f)
        lineTo(7f, 20f)
        moveTo(3.8f, 7.2f)
        lineTo(7f, 4f)
        lineTo(10.2f, 7.2f)
        moveTo(17f, 20f)
        lineTo(17f, 4f)
        moveTo(13.8f, 16.8f)
        lineTo(17f, 20f)
        lineTo(20.2f, 16.8f)
    }

    val Grid: ImageVector = stroke("Grid") {
        roundRect(4f, 4f, 10f, 10f, 2f)
        roundRect(14f, 4f, 20f, 10f, 2f)
        roundRect(4f, 14f, 10f, 20f, 2f)
        roundRect(14f, 14f, 20f, 20f, 2f)
    }

    val List: ImageVector = stroke("List") {
        circle(5f, 7f, 1.1f)
        circle(5f, 12f, 1.1f)
        circle(5f, 17f, 1.1f)
        moveTo(9f, 7f)
        lineTo(20f, 7f)
        moveTo(9f, 12f)
        lineTo(20f, 12f)
        moveTo(9f, 17f)
        lineTo(20f, 17f)
    }

    val Clock: ImageVector = stroke("Clock") {
        circle(12f, 12f, 8.5f)
        moveTo(12f, 7f)
        lineTo(12f, 12f)
        lineTo(15.5f, 14f)
    }

    val Globe: ImageVector = stroke("Globe") {
        circle(12f, 12f, 8.5f)
        moveTo(3.5f, 12f)
        lineTo(20.5f, 12f)
        moveTo(12f, 3.5f)
        curveTo(8.5f, 6.5f, 8.5f, 17.5f, 12f, 20.5f)
        curveTo(15.5f, 17.5f, 15.5f, 6.5f, 12f, 3.5f)
        close()
    }

    val Bookmark: ImageVector = stroke("Bookmark") {
        moveTo(6f, 4f)
        lineTo(18f, 4f)
        lineTo(18f, 21f)
        lineTo(12f, 16.8f)
        lineTo(6f, 21f)
        close()
    }

    val Plus: ImageVector = stroke("Plus") {
        moveTo(12f, 5f)
        lineTo(12f, 19f)
        moveTo(5f, 12f)
        lineTo(19f, 12f)
    }

    val Trash: ImageVector = stroke("Trash") {
        moveTo(4f, 6.5f)
        lineTo(20f, 6.5f)
        moveTo(9f, 6.5f)
        lineTo(9f, 3.5f)
        lineTo(15f, 3.5f)
        lineTo(15f, 6.5f)
        moveTo(6f, 6.5f)
        lineTo(7f, 20f)
        arcTo(1.5f, 1.5f, 0f, false, false, 8.5f, 21.5f)
        lineTo(15.5f, 21.5f)
        arcTo(1.5f, 1.5f, 0f, false, false, 17f, 20f)
        lineTo(18f, 6.5f)
        moveTo(10f, 10.5f)
        lineTo(10.4f, 17.5f)
        moveTo(14f, 10.5f)
        lineTo(13.6f, 17.5f)
    }

    val Share: ImageVector = stroke("Share") {
        circle(6f, 12f, 2.5f)
        circle(18f, 6f, 2.5f)
        circle(18f, 18f, 2.5f)
        moveTo(8.2f, 10.8f)
        lineTo(15.8f, 7.2f)
        moveTo(8.2f, 13.2f)
        lineTo(15.8f, 16.8f)
    }

    val Info: ImageVector = stroke("Info") {
        circle(12f, 12f, 8.5f)
        moveTo(12f, 11f)
        lineTo(12f, 16.5f)
        moveTo(12f, 7.6f)
        lineTo(12f, 7.7f)
    }

    val Folder: ImageVector = stroke("Folder") {
        moveTo(3f, 8f)
        arcTo(2f, 2f, 0f, false, true, 5f, 6f)
        lineTo(9.5f, 6f)
        lineTo(11.5f, 8.5f)
        lineTo(19f, 8.5f)
        arcTo(2f, 2f, 0f, false, true, 21f, 10.5f)
        lineTo(21f, 18f)
        arcTo(2f, 2f, 0f, false, true, 19f, 20f)
        lineTo(5f, 20f)
        arcTo(2f, 2f, 0f, false, true, 3f, 18f)
        close()
    }

    val Sparkle: ImageVector = filled("Sparkle") {
        moveTo(12f, 2.5f)
        curveTo(12.7f, 8.2f, 14.3f, 9.8f, 20f, 10.5f)
        curveTo(14.3f, 11.2f, 12.7f, 12.8f, 12f, 18.5f)
        curveTo(11.3f, 12.8f, 9.7f, 11.2f, 4f, 10.5f)
        curveTo(9.7f, 9.8f, 11.3f, 8.2f, 12f, 2.5f)
        close()
        moveTo(18.5f, 15.5f)
        curveTo(18.8f, 18f, 19.5f, 18.7f, 22f, 19f)
        curveTo(19.5f, 19.3f, 18.8f, 20f, 18.5f, 22.5f)
        curveTo(18.2f, 20f, 17.5f, 19.3f, 15f, 19f)
        curveTo(17.5f, 18.7f, 18.2f, 18f, 18.5f, 15.5f)
        close()
    }

    val Eye: ImageVector = stroke("Eye") {
        moveTo(2.5f, 12f)
        curveTo(5f, 7f, 8.5f, 5f, 12f, 5f)
        curveTo(15.5f, 5f, 19f, 7f, 21.5f, 12f)
        curveTo(19f, 17f, 15.5f, 19f, 12f, 19f)
        curveTo(8.5f, 19f, 5f, 17f, 2.5f, 12f)
        close()
        circle(12f, 12f, 3f)
    }

    val ArrowUp: ImageVector = stroke("ArrowUp") {
        moveTo(12f, 20f)
        lineTo(12f, 4f)
        moveTo(6f, 10f)
        lineTo(12f, 4f)
        lineTo(18f, 10f)
    }

    val ArrowDown: ImageVector = stroke("ArrowDown") {
        moveTo(12f, 4f)
        lineTo(12f, 20f)
        moveTo(6f, 14f)
        lineTo(12f, 20f)
        lineTo(18f, 14f)
    }

    val Wifi: ImageVector = stroke("Wifi") {
        moveTo(3f, 9f)
        curveTo(8f, 4.5f, 16f, 4.5f, 21f, 9f)
        moveTo(6.5f, 12.5f)
        curveTo(9.5f, 9.8f, 14.5f, 9.8f, 17.5f, 12.5f)
        moveTo(10f, 16f)
        curveTo(11.2f, 15f, 12.8f, 15f, 14f, 16f)
        circle(12f, 19f, 1.1f)
    }

    val Lock: ImageVector = stroke("Lock") {
        moveTo(5f, 10.5f)
        lineTo(19f, 10.5f)
        lineTo(19f, 20f)
        lineTo(5f, 20f)
        close()
        moveTo(8f, 10.5f)
        lineTo(8f, 7.5f)
        arcTo(4f, 4f, 0f, false, true, 16f, 7.5f)
        lineTo(16f, 10.5f)
    }

    val Link: ImageVector = stroke("Link") {
        moveTo(10f, 13.5f)
        curveTo(11.5f, 15f, 14f, 15f, 15.5f, 13.5f)
        lineTo(19f, 10f)
        curveTo(20.5f, 8.5f, 20.5f, 6f, 19f, 4.5f)
        curveTo(17.5f, 3f, 15f, 3f, 13.5f, 4.5f)
        lineTo(11.5f, 6.5f)
        moveTo(14f, 10.5f)
        curveTo(12.5f, 9f, 10f, 9f, 8.5f, 10.5f)
        lineTo(5f, 14f)
        curveTo(3.5f, 15.5f, 3.5f, 18f, 5f, 19.5f)
        curveTo(6.5f, 21f, 9f, 21f, 10.5f, 19.5f)
        lineTo(12.5f, 17.5f)
    }

    val Server: ImageVector = stroke("Server") {
        roundRect(3.5f, 4f, 20.5f, 10.5f, 2.5f)
        roundRect(3.5f, 13.5f, 20.5f, 20f, 2.5f)
        circle(7.5f, 7.2f, 1f)
        circle(7.5f, 16.8f, 1f)
        moveTo(12f, 7.2f)
        lineTo(17f, 7.2f)
        moveTo(12f, 16.8f)
        lineTo(17f, 16.8f)
    }

    val Code: ImageVector = stroke("Code") {
        moveTo(8.5f, 7f)
        lineTo(3.5f, 12f)
        lineTo(8.5f, 17f)
        moveTo(15.5f, 7f)
        lineTo(20.5f, 12f)
        lineTo(15.5f, 17f)
        moveTo(13.5f, 4.5f)
        lineTo(10.5f, 19.5f)
    }
}

private fun stroke(name: String, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            block()
        }
    }.build()

private fun filled(name: String, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            block()
        }
    }.build()

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    val k = r * 0.5523f
    moveTo(cx, cy - r)
    curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
    curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
    curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
    curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
    close()
}

private fun PathBuilder.roundRect(l: Float, t: Float, r: Float, b: Float, radius: Float) {
    moveTo(l + radius, t)
    lineTo(r - radius, t)
    arcTo(radius, radius, 0f, false, true, r, t + radius)
    lineTo(r, b - radius)
    arcTo(radius, radius, 0f, false, true, r - radius, b)
    lineTo(l + radius, b)
    arcTo(radius, radius, 0f, false, true, l, b - radius)
    lineTo(l, t + radius)
    arcTo(radius, radius, 0f, false, true, l + radius, t)
    close()
}
