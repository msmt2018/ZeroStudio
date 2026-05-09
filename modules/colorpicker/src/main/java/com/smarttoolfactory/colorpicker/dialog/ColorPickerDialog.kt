package com.smarttoolfactory.colorpicker.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.smarttoolfactory.colorpicker.picker.*
import com.smarttoolfactory.colorpicker.ui.Blue400
import com.smarttoolfactory.extendedcolors.util.ColorUtil
import kotlin.math.roundToInt

private enum class HexOutputFormat(val label: String) {
  HEX_LOWER("#d9d9d9"),
  HEX_UPPER("#D9D9D9"),
  SRGB_PERCENT("color(srgb 85% 85% 85%)"),
  SRGB_DECIMAL("color(srgb 0.85 0.85 0.85)"),
  RGB_PERCENT("rgb(85% 85% 85%)"),
  RGB_INT("rgb(217 217 217)"),
  HSL("hsl(none 0% 85%)"),
  HWB("hwb(none 85% 15%)"),
}

@Composable
fun ColorPickerRingDiamondHSLDialog(
    initialColor: Color,
    ringOuterRadiusFraction: Float = .9f,
    ringInnerRadiusFraction: Float = .6f,
    ringBackgroundColor: Color = Color.Transparent,
    ringBorderStrokeColor: Color = Color.Black,
    ringBorderStrokeWidth: Dp = 4.dp,
    selectionRadius: Dp = 8.dp,
    onDismiss: (Color, String) -> Unit,
) {

  var color by remember { mutableStateOf(initialColor.copy()) }
  var hexString by remember { mutableStateOf(ColorUtil.colorToHexAlpha(color)) }

  Dialog(onDismissRequest = { onDismiss(color, hexString) }) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      ColorPickerRingDiamondHSL(
          modifier =
              Modifier.fillMaxWidth()
                  .weight(1f)
                  .background(Color(0xcc212121), shape = RoundedCornerShape(5.dp))
                  .padding(horizontal = 10.dp, vertical = 2.dp),
          initialColor = initialColor,
          ringOuterRadiusFraction = ringOuterRadiusFraction,
          ringInnerRadiusFraction = ringInnerRadiusFraction,
          ringBackgroundColor = ringBackgroundColor,
          ringBorderStrokeColor = ringBorderStrokeColor,
          ringBorderStrokeWidth = ringBorderStrokeWidth,
          selectionRadius = selectionRadius,
      ) { colorChange, hexChange ->
        color = colorChange
        hexString = hexChange
      }

      FloatingActionButton(
          onClick = { onDismiss(color, hexString) },
          backgroundColor = Color.Black,
      ) {
        Icon(imageVector = Icons.Filled.Close, contentDescription = null, tint = Blue400)
      }
    }
  }
}

@Composable
fun ColorPickerRingDiamondHEXDialog(
    initialColor: Color,
    ringOuterRadiusFraction: Float = .9f,
    ringInnerRadiusFraction: Float = .6f,
    ringBackgroundColor: Color = Color.Transparent,
    ringBorderStrokeColor: Color = Color.Black,
    ringBorderStrokeWidth: Dp = 4.dp,
    selectionRadius: Dp = 8.dp,
    initialQueryText: String = "",
    onDismiss: (Color, String) -> Unit,
) {

  var color by remember { mutableStateOf(initialColor.copy()) }
  var hexString by remember { mutableStateOf(ColorUtil.colorToHexAlpha(color)) }
  var queryText by remember { mutableStateOf(initialQueryText.ifBlank { hexString }) }
  val pickerModes = listOf("Ring HEX", "Ring HSL", "Rect HSV", "Rect HSL")
  val colorSpaces =
      listOf(
          "sRGB",
          "Linear sRGB",
          "Adobe RGB",
          "Display P3",
          "Rec.2020",
          "ProPhoto RGB",
          "CIE LCH",
          "OK LCH",
          "CIE LAB",
          "OK LAB",
          "CIE XYZ D50",
          "CIE XYZ D65",
      )
  val codeFormats = HexOutputFormat.entries
  var modeExpanded by remember { mutableStateOf(false) }
  var spaceExpanded by remember { mutableStateOf(false) }
  var formatExpanded by remember { mutableStateOf(false) }
  var selectedMode by remember { mutableStateOf(pickerModes.first()) }
  var selectedSpace by remember { mutableStateOf(colorSpaces.first()) }
  var selectedFormat by remember { mutableStateOf(codeFormats.first()) }

  Dialog(onDismissRequest = { onDismiss(color, hexString) }) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      OutlinedTextField(value = queryText, onValueChange = { queryText = it }, label = { Text("Color Query") })
      Row {
        Text(selectedMode, modifier = Modifier.padding(6.dp).clickable { modeExpanded = true }, color = Color.White)
        DropdownMenu(expanded = modeExpanded, onDismissRequest = { modeExpanded = false }) {
          pickerModes.forEach { mode ->
            DropdownMenuItem(onClick = { selectedMode = mode; modeExpanded = false }) { Text(mode) }
          }
        }
        Text(selectedSpace, modifier = Modifier.padding(6.dp).clickable { spaceExpanded = true }, color = Color.White)
        DropdownMenu(expanded = spaceExpanded, onDismissRequest = { spaceExpanded = false }) {
          colorSpaces.forEach { mode ->
            DropdownMenuItem(onClick = { selectedSpace = mode; spaceExpanded = false }) { Text(mode) }
          }
        }
      }
      val formatText =
          when (selectedFormat) {
            HexOutputFormat.HEX_LOWER -> hexString.lowercase()
            HexOutputFormat.HEX_UPPER -> hexString.uppercase()
            HexOutputFormat.SRGB_PERCENT -> {
              val r = (color.red * 100).roundToInt()
              val g = (color.green * 100).roundToInt()
              val b = (color.blue * 100).roundToInt()
              "color(srgb $r% $g% $b%)"
            }
            HexOutputFormat.SRGB_DECIMAL -> {
              "color(srgb %.2f %.2f %.2f)".format(color.red, color.green, color.blue)
            }
            HexOutputFormat.RGB_PERCENT -> {
              val r = (color.red * 100).roundToInt()
              val g = (color.green * 100).roundToInt()
              val b = (color.blue * 100).roundToInt()
              "rgb($r% $g% $b%)"
            }
            HexOutputFormat.RGB_INT -> {
              val r = (color.red * 255).roundToInt()
              val g = (color.green * 255).roundToInt()
              val b = (color.blue * 255).roundToInt()
              "rgb($r $g $b)"
            }
            HexOutputFormat.HSL -> "hsl(none 0% ${(color.red * 100).roundToInt()}%)"
            HexOutputFormat.HWB -> "hwb(none ${(color.red * 100).roundToInt()}% ${(100 - color.red * 100).roundToInt()}%)"
          }
      Text(formatText, modifier = Modifier.padding(6.dp).clickable { formatExpanded = true }, color = Color.White)
      DropdownMenu(expanded = formatExpanded, onDismissRequest = { formatExpanded = false }) {
        codeFormats.forEach { mode ->
          DropdownMenuItem(onClick = { selectedFormat = mode; formatExpanded = false }) { Text(mode.label) }
        }
      }
      when (selectedMode) {
        "Ring HSL" ->
            ColorPickerRingDiamondHSL(
                modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xcc212121), shape = RoundedCornerShape(5.dp)).padding(horizontal = 10.dp, vertical = 2.dp),
                initialColor = initialColor,
            ) { c, h -> color = c; hexString = h }
        "Rect HSV" ->
            ColorPickerRingRectHSV(
                modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xcc212121), shape = RoundedCornerShape(5.dp)).padding(horizontal = 10.dp, vertical = 2.dp),
                initialColor = initialColor,
            ) { c, h -> color = c; hexString = h }
        "Rect HSL" ->
            ColorPickerRingRectHSL(
                modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xcc212121), shape = RoundedCornerShape(5.dp)).padding(horizontal = 10.dp, vertical = 2.dp),
                initialColor = initialColor,
            ) { c, h -> color = c; hexString = h }
        else ->
            ColorPickerRingDiamondHEX(
                modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xcc212121), shape = RoundedCornerShape(5.dp)).padding(horizontal = 10.dp, vertical = 2.dp),
                initialColor = initialColor,
                ringOuterRadiusFraction = ringOuterRadiusFraction,
                ringInnerRadiusFraction = ringInnerRadiusFraction,
                ringBackgroundColor = ringBackgroundColor,
                ringBorderStrokeColor = ringBorderStrokeColor,
                ringBorderStrokeWidth = ringBorderStrokeWidth,
                selectionRadius = selectionRadius,
            ) { c, h -> color = c; hexString = h }
      }

      FloatingActionButton(
          onClick = { onDismiss(color, hexString) },
          backgroundColor = Color.Black,
      ) {
        Icon(imageVector = Icons.Filled.Close, contentDescription = null, tint = Blue400)
      }
    }
  }
}

@Composable
fun ColorPickerRingRectHSLDialog(
    initialColor: Color,
    ringOuterRadiusFraction: Float = .9f,
    ringInnerRadiusFraction: Float = .6f,
    ringBackgroundColor: Color = Color.Transparent,
    ringBorderStrokeColor: Color = Color.Black,
    ringBorderStrokeWidth: Dp = 4.dp,
    selectionRadius: Dp = 8.dp,
    onDismiss: (Color, String) -> Unit,
) {

  var color by remember { mutableStateOf(initialColor.copy()) }
  var hexString by remember { mutableStateOf(ColorUtil.colorToHexAlpha(color)) }

  Dialog(onDismissRequest = { onDismiss(color, hexString) }) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      ColorPickerRingRectHSL(
          modifier =
              Modifier.fillMaxWidth()
                  .weight(1f)
                  .background(Color(0xcc212121), shape = RoundedCornerShape(5.dp))
                  .padding(horizontal = 10.dp, vertical = 2.dp),
          initialColor = initialColor,
          ringOuterRadiusFraction = ringOuterRadiusFraction,
          ringInnerRadiusFraction = ringInnerRadiusFraction,
          ringBackgroundColor = ringBackgroundColor,
          ringBorderStrokeColor = ringBorderStrokeColor,
          ringBorderStrokeWidth = ringBorderStrokeWidth,
          selectionRadius = selectionRadius,
      ) { colorChange, hexChange ->
        color = colorChange
        hexString = hexChange
      }

      FloatingActionButton(
          onClick = { onDismiss(color, hexString) },
          backgroundColor = Color.Black,
      ) {
        Icon(imageVector = Icons.Filled.Close, contentDescription = null, tint = Blue400)
      }
    }
  }
}

@Composable
fun ColorPickerRingRectHSVDialog(
    initialColor: Color,
    ringOuterRadiusFraction: Float = .9f,
    ringInnerRadiusFraction: Float = .6f,
    ringBackgroundColor: Color = Color.Transparent,
    ringBorderStrokeColor: Color = Color.Black,
    ringBorderStrokeWidth: Dp = 4.dp,
    selectionRadius: Dp = 8.dp,
    onDismiss: (Color, String) -> Unit,
) {

  var color by remember { mutableStateOf(initialColor.copy()) }
  var hexString by remember { mutableStateOf(ColorUtil.colorToHexAlpha(color)) }

  Dialog(onDismissRequest = { onDismiss(color, hexString) }) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      ColorPickerRingRectHSV(
          modifier =
              Modifier.fillMaxWidth()
                  .weight(1f)
                  .background(Color(0xcc212121), shape = RoundedCornerShape(5.dp))
                  .padding(horizontal = 10.dp, vertical = 2.dp),
          initialColor = initialColor,
          ringOuterRadiusFraction = ringOuterRadiusFraction,
          ringInnerRadiusFraction = ringInnerRadiusFraction,
          ringBackgroundColor = ringBackgroundColor,
          ringBorderStrokeColor = ringBorderStrokeColor,
          ringBorderStrokeWidth = ringBorderStrokeWidth,
          selectionRadius = selectionRadius,
      ) { colorChange, hexChange ->
        color = colorChange
        hexString = hexChange
      }

      FloatingActionButton(
          onClick = { onDismiss(color, hexString) },
          backgroundColor = Color.Black,
      ) {
        Icon(imageVector = Icons.Filled.Close, contentDescription = null, tint = Blue400)
      }
    }
  }
}

@Composable
fun ColorPickerRingHexHSVDialog(
    modifier: Modifier = Modifier,
    initialColor: Color,
    selectionRadius: Dp = 8.dp,
    dialogBackgroundColor: Color = Color.White,
    dialogShape: Shape = RoundedCornerShape(5.dp),
    onDismiss: (Color, String) -> Unit,
) {

  var color by remember { mutableStateOf(initialColor.copy()) }
  var hexString by remember { mutableStateOf(ColorUtil.colorToHexAlpha(color)) }

  Dialog(onDismissRequest = { onDismiss(color, hexString) }) {
    Surface(
        modifier = modifier,
        color = dialogBackgroundColor,
        shape = dialogShape,
        elevation = 2.dp,
    ) {
      ColorPickerRingRectHex(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
          initialColor = initialColor,
          selectionRadius = selectionRadius,
      ) { colorChange, hexChange ->
        color = colorChange
        hexString = hexChange
      }
    }
  }
}

@Composable
fun ColorPickerCircleHSVDialog(
    modifier: Modifier = Modifier,
    initialColor: Color,
    selectionRadius: Dp = 8.dp,
    dialogBackgroundColor: Color = Color.White,
    dialogShape: Shape = RoundedCornerShape(5.dp),
    onDismiss: (Color, String) -> Unit,
) {

  var color by remember { mutableStateOf(initialColor.copy()) }
  var hexString by remember { mutableStateOf(ColorUtil.colorToHexAlpha(color)) }

  Dialog(onDismissRequest = { onDismiss(color, hexString) }) {
    Surface(
        modifier = modifier,
        color = dialogBackgroundColor,
        shape = dialogShape,
        elevation = 2.dp,
    ) {
      ColorPickerCircleValueHSV(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
          initialColor = initialColor,
          selectionRadius = selectionRadius,
      ) { colorChange, hexChange ->
        color = colorChange
        hexString = hexChange
      }
    }
  }
}

@Composable
fun ColorPickerSVRectHSVDialog(
    modifier: Modifier = Modifier,
    initialColor: Color,
    selectionRadius: Dp = 8.dp,
    dialogBackgroundColor: Color = Color.White,
    dialogShape: Shape = RoundedCornerShape(5.dp),
    onDismiss: (Color, String) -> Unit,
) {

  var color by remember { mutableStateOf(initialColor.copy()) }
  var hexString by remember { mutableStateOf(ColorUtil.colorToHexAlpha(color)) }

  Dialog(onDismissRequest = { onDismiss(color, hexString) }) {
    Surface(
        modifier = modifier,
        color = dialogBackgroundColor,
        shape = dialogShape,
        elevation = 2.dp,
    ) {
      ColorPickerRectSaturationValueHSV(
          modifier = Modifier,
          initialColor = initialColor,
          selectionRadius = selectionRadius,
      ) { colorChange, hexChange ->
        color = colorChange
        hexString = hexChange
      }
    }
  }
}

@Composable
fun ColorPickerSLRectHSLDialog(
    modifier: Modifier = Modifier,
    initialColor: Color,
    selectionRadius: Dp = 8.dp,
    dialogBackgroundColor: Color = Color.White,
    dialogShape: Shape = RoundedCornerShape(5.dp),
    onDismiss: (Color, String) -> Unit,
) {

  var color by remember { mutableStateOf(initialColor.copy()) }
  var hexString by remember { mutableStateOf(ColorUtil.colorToHexAlpha(color)) }

  Dialog(onDismissRequest = { onDismiss(color, hexString) }) {
    Surface(
        modifier = modifier,
        color = dialogBackgroundColor,
        shape = dialogShape,
        elevation = 2.dp,
    ) {
      ColorPickerRectSaturationLightnessHSL(
          modifier = Modifier,
          initialColor = initialColor,
          selectionRadius = selectionRadius,
      ) { colorChange, hexChange ->
        color = colorChange
        hexString = hexChange
      }
    }
  }
}

@Composable
fun ColorPickerHSRectHSVDialog(
    modifier: Modifier = Modifier,
    initialColor: Color,
    selectionRadius: Dp = 8.dp,
    dialogBackgroundColor: Color = Color.White,
    dialogShape: Shape = RoundedCornerShape(5.dp),
    onDismiss: (Color, String) -> Unit,
) {

  var color by remember { mutableStateOf(initialColor.copy()) }
  var hexString by remember { mutableStateOf(ColorUtil.colorToHexAlpha(color)) }

  Dialog(onDismissRequest = { onDismiss(color, hexString) }) {
    Surface(
        modifier = modifier,
        color = dialogBackgroundColor,
        shape = dialogShape,
        elevation = 2.dp,
    ) {
      ColorPickerRectHueSaturationHSV(
          modifier = Modifier,
          initialColor = initialColor,
          selectionRadius = selectionRadius,
      ) { colorChange, hexChange ->
        color = colorChange
        hexString = hexChange
      }
    }
  }
}

@Composable
fun ColorPickerHVRectHSVDialog(
    modifier: Modifier = Modifier,
    initialColor: Color,
    selectionRadius: Dp = 8.dp,
    dialogBackgroundColor: Color = Color.White,
    dialogShape: Shape = RoundedCornerShape(5.dp),
    onDismiss: (Color, String) -> Unit,
) {

  var color by remember { mutableStateOf(initialColor.copy()) }
  var hexString by remember { mutableStateOf(ColorUtil.colorToHexAlpha(color)) }

  Dialog(onDismissRequest = { onDismiss(color, hexString) }) {
    Surface(
        modifier = modifier,
        color = dialogBackgroundColor,
        shape = dialogShape,
        elevation = 2.dp,
    ) {
      ColorPickerRectHueValueHSV(
          modifier = Modifier,
          initialColor = initialColor,
          selectionRadius = selectionRadius,
      ) { colorChange, hexChange ->
        color = colorChange
        hexString = hexChange
      }
    }
  }
}

@Composable
fun ColorPickerHSRectHSLDialog(
    modifier: Modifier = Modifier,
    initialColor: Color,
    selectionRadius: Dp = 8.dp,
    dialogBackgroundColor: Color = Color.White,
    dialogShape: Shape = RoundedCornerShape(5.dp),
    onDismiss: (Color, String) -> Unit,
) {

  var color by remember { mutableStateOf(initialColor.copy()) }
  var hexString by remember { mutableStateOf(ColorUtil.colorToHexAlpha(color)) }

  Dialog(onDismissRequest = { onDismiss(color, hexString) }) {
    Surface(
        modifier = modifier,
        color = dialogBackgroundColor,
        shape = dialogShape,
        elevation = 2.dp,
    ) {
      ColorPickerRectHueSaturationHSL(
          modifier = Modifier,
          initialColor = initialColor,
          selectionRadius = selectionRadius,
      ) { colorChange, hexChange ->
        color = colorChange
        hexString = hexChange
      }
    }
  }
}

@Composable
fun ColorPickerHLRectHSLDialog(
    modifier: Modifier = Modifier,
    initialColor: Color,
    selectionRadius: Dp = 8.dp,
    dialogBackgroundColor: Color = Color.White,
    dialogShape: Shape = RoundedCornerShape(5.dp),
    onDismiss: (Color, String) -> Unit,
) {

  var color by remember { mutableStateOf(initialColor.copy()) }
  var hexString by remember { mutableStateOf(ColorUtil.colorToHexAlpha(color)) }

  Dialog(onDismissRequest = { onDismiss(color, hexString) }) {
    Surface(
        modifier = modifier,
        color = dialogBackgroundColor,
        shape = dialogShape,
        elevation = 2.dp,
    ) {
      ColorPickerRectHueLightnessHSL(
          modifier = Modifier,
          initialColor = initialColor,
          selectionRadius = selectionRadius,
      ) { colorChange, hexChange ->
        color = colorChange
        hexString = hexChange
      }
    }
  }
}

@Composable
fun ColorPickerM2Dialog(
    modifier: Modifier = Modifier,
    initialColor: Color,
    dialogBackgroundColor: Color = Color.White,
    dialogShape: Shape = RoundedCornerShape(5.dp),
    onDismiss: (Color, String) -> Unit,
) {

  var color by remember { mutableStateOf(initialColor.copy()) }
  var hexString by remember { mutableStateOf(ColorUtil.colorToHexAlpha(color)) }

  Dialog(onDismissRequest = { onDismiss(color, hexString) }) {
    Surface(
        modifier = modifier,
        color = dialogBackgroundColor,
        shape = dialogShape,
        elevation = 2.dp,
    ) {
      M2ColorPicker { colorChange ->
        color = colorChange
        hexString = ColorUtil.colorToHex(color)
      }
    }
  }
}

@Composable
fun ColorPickerM3Dialog(
    modifier: Modifier = Modifier,
    initialColor: Color,
    dialogBackgroundColor: Color = Color.White,
    dialogShape: Shape = RoundedCornerShape(5.dp),
    onDismiss: (Color, String) -> Unit,
) {

  var color by remember { mutableStateOf(initialColor.copy()) }
  var hexString by remember { mutableStateOf(ColorUtil.colorToHexAlpha(color)) }

  Dialog(onDismissRequest = { onDismiss(color, hexString) }) {
    Surface(
        modifier = modifier,
        color = dialogBackgroundColor,
        shape = dialogShape,
        elevation = 2.dp,
    ) {
      M3ColorPicker { colorChange ->
        color = colorChange
        hexString = ColorUtil.colorToHex(color)
      }
    }
  }
}
