package com.example.sitacardmaster

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLog

actual fun platformLog(tag: String, message: String) {
    NSLog("[%s] %s", tag, message)
}

actual fun currentTime(): String {
    val formatter = NSDateFormatter()
    formatter.dateFormat = "HH:mm:ss"
    return formatter.stringFromDate(NSDate())
}

actual fun platformNow(): String {
    val formatter = NSDateFormatter()
    formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
    return formatter.stringFromDate(NSDate())
}
