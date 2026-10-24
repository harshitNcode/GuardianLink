package com.guardianlink.app.model

data class Alert(
    val id: Int? = null,
    val title: String = "",
    val time: String = "",
    val type: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
