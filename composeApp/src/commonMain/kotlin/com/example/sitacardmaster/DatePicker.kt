package com.example.sitacardmaster

import androidx.compose.runtime.Composable

@Composable
expect fun PlatformDatePicker(
    onDateSelected: (day: Int, month: Int, year: Int) -> Unit,
    onDismiss: () -> Unit
)
