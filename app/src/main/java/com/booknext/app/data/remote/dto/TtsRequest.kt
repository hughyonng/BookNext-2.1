package com.booknext.app.data.remote.dto

data class TtsRequest(
    val text: String,
    val voice: String = "zh-CN-XiaoxiaoNeural",
    val rate: String = "+0%",
    val pitch: String = "+0Hz",
    val engine: String = "edge",
    val apiKey: String = "",
    val secretKey: String = "",
)