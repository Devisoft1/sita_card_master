package com.example.sitacardmaster

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import sitacardmaster.composeapp.generated.resources.*

@Composable
fun PoweredBySection(
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val grayText = Color(0xFF757575)
    val devisoftBlue = Color(0xFF00509E)
    val devisoftOrange = Color(0xFFF58220)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { uriHandler.openUri("https://devisoft.co.in") },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Powered by ",
            color = grayText,
            fontSize = 13.sp
        )
        Text(
            text = "Devi",
            color = devisoftBlue,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Soft",
            color = devisoftOrange,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
