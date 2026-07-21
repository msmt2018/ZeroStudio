package android.zero.studio.shell.wifi_adb_shell.data.local.mapper

import android.zero.studio.shell.wifi_adb_shell.data.local.model.WifiAdbDeviceEntity
import android.zero.studio.shell.wifi_adb_shell.domain.model.WifiAdbDevice

/**
 * Mapper extensions to convert between Room entity and domain model.
 */

fun WifiAdbDeviceEntity.toDomain(): WifiAdbDevice {
    return WifiAdbDevice(
        ip = ip,
        port = port,
        deviceName = deviceName,
        lastConnected = lastConnected,
        isPaired = isPaired,
        serialNumber = serialNumber,
        isOwnDevice = isOwnDevice,
        id = id
    )
}

fun WifiAdbDevice.toEntity(): WifiAdbDeviceEntity {
    return WifiAdbDeviceEntity(
        id = id,
        ip = ip,
        port = port,
        deviceName = deviceName,
        lastConnected = lastConnected,
        isPaired = isPaired,
        serialNumber = serialNumber,
        isOwnDevice = isOwnDevice
    )
}

fun List<WifiAdbDeviceEntity>.toDomainList(): List<WifiAdbDevice> {
    return map { it.toDomain() }
}
