package com.example.sitacardmaster

import platform.UIKit.UIDevice
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.SystemConfiguration.*
import kotlinx.cinterop.*
import platform.posix.sockaddr_in

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

actual fun getCurrentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

@OptIn(ExperimentalForeignApi::class)
actual fun isNetworkAvailable(): Boolean = memScoped {
    val address = alloc<sockaddr_in>()
    address.sin_len = sizeOf<sockaddr_in>().toUByte()
    address.sin_family = platform.posix.AF_INET.toUByte()

    val reachability = SCNetworkReachabilityCreateWithAddress(null, address.ptr.reinterpret())
    val flags = alloc<SCNetworkReachabilityFlagsVar>()
    
    val success = SCNetworkReachabilityGetFlags(reachability, flags.ptr)
    if (!success) return@memScoped false

    val isReachable = (flags.value and kSCNetworkReachabilityFlagsReachable) != 0u
    val needsConnection = (flags.value and kSCNetworkReachabilityFlagsConnectionRequired) != 0u

    isReachable && !needsConnection
}