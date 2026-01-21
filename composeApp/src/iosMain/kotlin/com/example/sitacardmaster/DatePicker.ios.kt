package com.example.sitacardmaster

import androidx.compose.runtime.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.*
import platform.UIKit.*

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformDatePicker(
    onDateSelected: (day: Int, month: Int, year: Int) -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) {
        val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
        
        // Create date picker
        val datePicker = UIDatePicker()
        datePicker.datePickerMode = UIDatePickerMode.UIDatePickerModeDate
        datePicker.preferredDatePickerStyle = UIDatePickerStyle.UIDatePickerStyleWheels
        datePicker.setFrame(CGRectMake(0.0, 0.0, 270.0, 200.0))
        
        // Create container view controller
        val pickerVC = UIViewController()
        pickerVC.view.addSubview(datePicker)
        
        // Create alert
        val alert = UIAlertController.alertControllerWithTitle(
            title = "Select Date",
            message = null,
            preferredStyle = UIAlertControllerStyleAlert
        )
        
        alert.setValue(pickerVC, forKey = "contentViewController")
        
        // Add OK action
        val okAction = UIAlertAction.actionWithTitle(
            title = "OK",
            style = UIAlertActionStyleDefault
        ) { _ ->
            val selectedDate = datePicker.date
            val calendar = NSCalendar.currentCalendar
            val components = calendar.components(
                NSCalendarUnitDay or NSCalendarUnitMonth or NSCalendarUnitYear,
                fromDate = selectedDate
            )
            
            onDateSelected(
                components.day.toInt(),
                components.month.toInt(),
                components.year.toInt()
            )
        }
        alert.addAction(okAction)
        
        // Add Cancel action
        val cancelAction = UIAlertAction.actionWithTitle(
            title = "Cancel",
            style = UIAlertActionStyleCancel
        ) { _ ->
            onDismiss()
        }
        alert.addAction(cancelAction)
        
        // Present
        rootViewController?.presentViewController(alert, animated = true, completion = null)
    }
}
