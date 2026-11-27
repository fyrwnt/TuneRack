package com.gitgud.tunerack.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.unit.dp

/** Icons drawn in-house on a 24-unit grid, so no SF Symbols or other licensed sets are bundled. */
object TuneIcons {
    val Home by lazy {
        icon("Home") {
            stroke {
                moveTo(3.5f, 11f); lineTo(12f, 4f); lineTo(20.5f, 11f)
                moveTo(6f, 9.6f); lineTo(6f, 19.5f); lineTo(18f, 19.5f); lineTo(18f, 9.6f)
                moveTo(10.2f, 19.5f); lineTo(10.2f, 14.8f); lineTo(13.8f, 14.8f); lineTo(13.8f, 19.5f)
            }
        }
    }

    val Charts by lazy {
        icon("Charts") {
            stroke(2.3f) {
                moveTo(4.5f, 19.5f); lineTo(4.5f, 14f)
                moveTo(9.5f, 19.5f); lineTo(9.5f, 9f)
                moveTo(14.5f, 19.5f); lineTo(14.5f, 4.5f)
                moveTo(19.5f, 19.5f); lineTo(19.5f, 11.5f)
            }
        }
    }

    val Library by lazy {
        icon("Library") {
            stroke {
                roundRect(4.5f, 8.5f, 19.5f, 20f, 2.5f)
                moveTo(6.5f, 5.8f); lineTo(17.5f, 5.8f)
                moveTo(8.5f, 3.2f); lineTo(15.5f, 3.2f)
            }
        }
    }

    val Search by lazy {
        icon("Search") {
            stroke(2f) {
                circle(10.5f, 10.5f, 6.5f)
                moveTo(15.4f, 15.4f); lineTo(20.5f, 20.5f)
            }
        }
    }

    val Playlists by lazy {
        icon("Playlists") {
            stroke {
                moveTo(4f, 6.5f); lineTo(15f, 6.5f)
                moveTo(4f, 11f); lineTo(15f, 11f)
                moveTo(4f, 15.5f); lineTo(10f, 15.5f)
                moveTo(18.5f, 17f); lineTo(18.5f, 8.5f); lineTo(21f, 9.6f)
            }
            fill { circle(16.6f, 17.2f, 2.1f) }
        }
    }

    val Artists by lazy {
        icon("Artists") {
            stroke {
                roundRect(9f, 3f, 15f, 13.5f, 3f)
                moveTo(6f, 11f)
                arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 18f, y1 = 11f)
                moveTo(12f, 17f); lineTo(12f, 20.5f)
                moveTo(9f, 20.5f); lineTo(15f, 20.5f)
            }
        }
    }

    val Albums by lazy {
        icon("Albums") {
            stroke {
                roundRect(3.5f, 3.5f, 20.5f, 20.5f, 3f)
                circle(12f, 12f, 4.3f)
            }
            fill { circle(12f, 12f, 1.1f) }
        }
    }

    val Songs by lazy {
        icon("Songs") {
            stroke {
                moveTo(14.5f, 16.3f); lineTo(14.5f, 4f); lineTo(19.5f, 5.6f); lineTo(19.5f, 8.6f); lineTo(14.5f, 7f)
                circle(11.9f, 16.3f, 2.6f)
            }
        }
    }

    val Downloaded by lazy {
        icon("Downloaded") {
            stroke {
                circle(12f, 12f, 9f)
                moveTo(12f, 7.5f); lineTo(12f, 16f)
                moveTo(8.5f, 12.6f); lineTo(12f, 16.1f); lineTo(15.5f, 12.6f)
            }
        }
    }

    val ChevronRight by lazy {
        icon("ChevronRight") { stroke(2.2f) { moveTo(9f, 5.5f); lineTo(15.5f, 12f); lineTo(9f, 18.5f) } }
    }

    val Person by lazy {
        icon("Person") {
            stroke {
                circle(12f, 8.5f, 3.8f)
                moveTo(4.8f, 20f)
                quadTo(5.6f, 14.3f, 12f, 14.3f)
                quadTo(18.4f, 14.3f, 19.2f, 20f)
            }
        }
    }

    val Help by lazy {
        icon("Help") {
            stroke { circle(12f, 12f, 9f) }
            stroke(2f) {
                moveTo(9.3f, 9.4f)
                quadTo(9.6f, 7f, 12f, 7f)
                quadTo(14.7f, 7f, 14.7f, 9.5f)
                quadTo(14.7f, 11f, 12.9f, 11.9f)
                quadTo(12f, 12.4f, 12f, 13.8f)
            }
            fill { circle(12f, 16.9f, 1.15f) }
        }
    }

    val Checkmark by lazy {
        icon("Checkmark") { stroke(2.4f) { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) } }
    }

    val More by lazy {
        icon("More") {
            fill {
                circle(5.5f, 12f, 1.7f)
                circle(12f, 12f, 1.7f)
                circle(18.5f, 12f, 1.7f)
            }
        }
    }

    val Play by lazy {
        icon("Play") { fill { moveTo(7f, 4.5f); lineTo(19.5f, 12f); lineTo(7f, 19.5f); close() } }
    }

    val Pause by lazy {
        icon("Pause") {
            fill {
                roundRect(6f, 4.5f, 10f, 19.5f, 1.2f)
                roundRect(14f, 4.5f, 18f, 19.5f, 1.2f)
            }
        }
    }

    val Forward by lazy {
        icon("Forward") {
            fill {
                moveTo(2.5f, 6f); lineTo(11.5f, 12f); lineTo(2.5f, 18f); close()
                moveTo(12f, 6f); lineTo(21f, 12f); lineTo(12f, 18f); close()
            }
        }
    }

    val Previous by lazy {
        icon("Previous") {
            fill {
                moveTo(21.5f, 6f); lineTo(12.5f, 12f); lineTo(21.5f, 18f); close()
                moveTo(12f, 6f); lineTo(3f, 12f); lineTo(12f, 18f); close()
            }
        }
    }

    val Shuffle by lazy {
        icon("Shuffle") {
            stroke(2f) {
                moveTo(3f, 7f); lineTo(7.5f, 7f); lineTo(15.5f, 17f); lineTo(20f, 17f)
                moveTo(3f, 17f); lineTo(7.5f, 17f); lineTo(10.2f, 13.6f)
                moveTo(12.8f, 10.4f); lineTo(15.5f, 7f); lineTo(20f, 7f)
                moveTo(17.5f, 4.5f); lineTo(20f, 7f); lineTo(17.5f, 9.5f)
                moveTo(17.5f, 14.5f); lineTo(20f, 17f); lineTo(17.5f, 19.5f)
            }
        }
    }

    val Repeat by lazy { icon("Repeat") { stroke(2f) { repeatArrows() } } }

    val RepeatOne by lazy {
        icon("RepeatOne") {
            stroke(2f) { repeatArrows() }
            stroke(1.6f) { moveTo(11f, 10.6f); lineTo(12.4f, 9.6f); lineTo(12.4f, 14.4f) }
        }
    }

    val Queue by lazy {
        icon("Queue") {
            stroke(2f) {
                moveTo(4f, 6f); lineTo(20f, 6f)
                moveTo(4f, 12f); lineTo(20f, 12f)
                moveTo(4f, 18f); lineTo(13f, 18f)
            }
            fill { moveTo(16f, 15f); lineTo(21f, 18f); lineTo(16f, 21f); close() }
        }
    }

    val Heart by lazy { icon("Heart") { stroke(1.9f) { heart() } } }

    val HeartFilled by lazy { icon("HeartFilled") { fill { heart() } } }

    val Plus by lazy {
        icon("Plus") { stroke(2f) { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) } }
    }

    val RemoveCircle by lazy {
        icon("RemoveCircle") { stroke { circle(12f, 12f, 9f); moveTo(8f, 12f); lineTo(16f, 12f) } }
    }

    val ChevronLeft by lazy {
        icon("ChevronLeft") { stroke(2.4f) { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) } }
    }

    val Pencil by lazy {
        icon("Pencil") {
            stroke {
                moveTo(4f, 20f); lineTo(4.8f, 16.2f); lineTo(15.6f, 5.4f)
                quadTo(16.8f, 4.2f, 18f, 5.4f); lineTo(18.6f, 6f)
                quadTo(19.8f, 7.2f, 18.6f, 8.4f); lineTo(7.8f, 19.2f); close()
                moveTo(13.8f, 7.2f); lineTo(16.8f, 10.2f)
            }
        }
    }

    val Trash by lazy {
        icon("Trash") {
            stroke {
                moveTo(4.5f, 6.5f); lineTo(19.5f, 6.5f)
                moveTo(9.5f, 6.5f); lineTo(9.5f, 4.5f); lineTo(14.5f, 4.5f); lineTo(14.5f, 6.5f)
                moveTo(6.5f, 6.5f); lineTo(7.4f, 20f); lineTo(16.6f, 20f); lineTo(17.5f, 6.5f)
            }
        }
    }

    val PlayNext by lazy {
        icon("PlayNext") {
            stroke {
                moveTo(4f, 7f); lineTo(12.5f, 7f); moveTo(4f, 12f); lineTo(12.5f, 12f); moveTo(4f, 17f); lineTo(10f, 17f)
                moveTo(17.5f, 19f); lineTo(17.5f, 7.5f); moveTo(14.5f, 10.5f); lineTo(17.5f, 7.5f); lineTo(20.5f, 10.5f)
            }
        }
    }

    val PlayLast by lazy {
        icon("PlayLast") {
            stroke {
                moveTo(4f, 7f); lineTo(12.5f, 7f); moveTo(4f, 12f); lineTo(12.5f, 12f); moveTo(4f, 17f); lineTo(10f, 17f)
                moveTo(17.5f, 5f); lineTo(17.5f, 16.5f); moveTo(14.5f, 13.5f); lineTo(17.5f, 16.5f); lineTo(20.5f, 13.5f)
            }
        }
    }

    /** Solid circle with a cut-out arrow: the "downloaded" mark on song rows. */
    val DownloadedSmall by lazy {
        icon("DownloadedSmall") {
            fill(evenOdd = true) {
                circle(12f, 12f, 10f)
                moveTo(10.8f, 6.5f); lineTo(13.2f, 6.5f); lineTo(13.2f, 12.6f); lineTo(15.6f, 10.2f); lineTo(17.3f, 11.9f)
                lineTo(12f, 17.2f); lineTo(6.7f, 11.9f); lineTo(8.4f, 10.2f); lineTo(10.8f, 12.6f); close()
            }
        }
    }

    val Alert by lazy {
        icon("Alert") {
            fill(evenOdd = true) {
                circle(12f, 12f, 10f)
                moveTo(10.9f, 6.5f); lineTo(13.1f, 6.5f); lineTo(13.1f, 13.5f); lineTo(10.9f, 13.5f); close()
                circle(12f, 16.6f, 1.3f)
            }
        }
    }

    private fun PathBuilder.heart() {
        moveTo(12f, 20.3f)
        curveTo(5f, 15.4f, 2.5f, 12f, 2.5f, 8.6f)
        curveTo(2.5f, 5.8f, 4.7f, 3.7f, 7.3f, 3.7f)
        curveTo(9.2f, 3.7f, 11f, 4.8f, 12f, 6.5f)
        curveTo(13f, 4.8f, 14.8f, 3.7f, 16.7f, 3.7f)
        curveTo(19.3f, 3.7f, 21.5f, 5.8f, 21.5f, 8.6f)
        curveTo(21.5f, 12f, 19f, 15.4f, 12f, 20.3f)
        close()
    }

    private fun PathBuilder.repeatArrows() {
        moveTo(4f, 12f); lineTo(4f, 10f); quadTo(4f, 7f, 7f, 7f); lineTo(19f, 7f)
        moveTo(16.5f, 4.5f); lineTo(19f, 7f); lineTo(16.5f, 9.5f)
        moveTo(20f, 12f); lineTo(20f, 14f); quadTo(20f, 17f, 17f, 17f); lineTo(5f, 17f)
        moveTo(7.5f, 14.5f); lineTo(5f, 17f); lineTo(7.5f, 19.5f)
    }

    /** Solid note used on empty artwork. */
    val Note by lazy {
        icon("Note") {
            fill {
                moveTo(14.2f, 4f); lineTo(16f, 4f); lineTo(16f, 16.4f); lineTo(14.2f, 16.4f); close()
                moveTo(16f, 4f); lineTo(20.2f, 5.5f); lineTo(20.2f, 8.6f); lineTo(16f, 7.2f); close()
                circle(12.9f, 16.4f, 3f)
            }
        }
    }

    private class IconScope(val builder: ImageVector.Builder) {
        fun stroke(width: Float = 1.8f, path: PathBuilder.() -> Unit) {
            builder.addPath(
                pathData = PathBuilder().apply(path).nodes,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }

        fun fill(evenOdd: Boolean = false, path: PathBuilder.() -> Unit) {
            builder.addPath(
                pathData = PathBuilder().apply(path).nodes,
                fill = SolidColor(Color.Black),
                pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
            )
        }
    }

    private fun icon(name: String, block: IconScope.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).also { IconScope(it).block() }.build()

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx + r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx - r, y1 = cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx + r, y1 = cy)
        close()
    }

    private fun PathBuilder.roundRect(l: Float, t: Float, r: Float, b: Float, rad: Float) {
        moveTo(l + rad, t)
        lineTo(r - rad, t); arcTo(rad, rad, 0f, false, true, r, t + rad)
        lineTo(r, b - rad); arcTo(rad, rad, 0f, false, true, r - rad, b)
        lineTo(l + rad, b); arcTo(rad, rad, 0f, false, true, l, b - rad)
        lineTo(l, t + rad); arcTo(rad, rad, 0f, false, true, l + rad, t)
        close()
    }
}
