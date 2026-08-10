package ru.taskflow.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.taskflow.app.ui.theme.Accent
import ru.taskflow.app.ui.theme.Ink

@Composable
fun TaskFlowMark(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    ringColor: Color = Ink,
    accentColor: Color = Accent,
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = this.size.minDimension * .065f
        drawCircle(
            color = ringColor,
            radius = this.size.minDimension * .28f,
            style = Stroke(width = stroke),
        )
        drawCircle(
            color = accentColor,
            radius = this.size.minDimension * .065f,
            center = Offset(this.size.width * .75f, this.size.height * .33f),
        )
        val check = androidx.compose.ui.graphics.Path().apply {
            moveTo(this@Canvas.size.width * .36f, this@Canvas.size.height * .51f)
            lineTo(this@Canvas.size.width * .46f, this@Canvas.size.height * .61f)
            lineTo(this@Canvas.size.width * .66f, this@Canvas.size.height * .40f)
        }
        drawPath(
            path = check,
            color = accentColor,
            style = Stroke(width = stroke * 1.25f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
