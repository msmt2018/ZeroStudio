package com.itsaky.androidide.repository.sdkmanager.models

import com.google.gson.annotations.SerializedName

/**
 * JSON 结构的 SDK 清单模型。
 * @author android_zero
 */
data class SdkManifest(
    @SerializedName("android_sdk") val androidSdk: String?,
    @SerializedName("cmdline_tools") val cmdlineTools: String?,
    @SerializedName("build_tools") val buildTools: Map<String, Map<String, String>>?,
    @SerializedName("platform_tools") val platformTools: Map<String, Map<String, String>>?,
    @SerializedName("android_ndk") val androidNdk: Map<String, Map<String, String>>?,
    @SerializedName("android_cmake") val androidCmake: Map<String, Map<String, String>>?,
    @SerializedName("jdk_11") val jdk11: Map<String, String>?,
    @SerializedName("jdk_17") val jdk17: Map<String, String>?,
    @SerializedName("jdk_21") val jdk21: Map<String, String>?,
    @SerializedName("jdk_22") val jdk22: Map<String, String>?,
    @SerializedName("jdk_23") val jdk23: Map<String, String>?,
    @SerializedName("jdk_24") val jdk24: Map<String, String>?,
    @SerializedName("jdk_25") val jdk25: Map<String, String>?,
    @SerializedName("jdk_26") val jdk26: Map<String, String>?
)
