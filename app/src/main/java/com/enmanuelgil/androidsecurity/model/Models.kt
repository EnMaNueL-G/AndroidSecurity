package com.enmanuelgil.androidsecurity.model

import android.graphics.drawable.Drawable

// ── Risk levels ────────────────────────────────────────
enum class RiskLevel(val order: Int) {
    HIGH(0), MEDIUM(1), LOW(2), SAFE(3)
}

// ── Permission categories ──────────────────────────────
enum class PermCategory {
    CAMERA, MICROPHONE, LOCATION, CONTACTS, SMS, STORAGE,
    ACCESSIBILITY, DEVICE_ADMIN, OVERLAY, OTHER
}

// ── App permission entry ───────────────────────────────
data class AppPermissionInfo(
    val packageName : String,
    val appName     : String,
    val icon        : Drawable?,
    val permissions : List<String>,
    val categories  : Set<PermCategory>,
    val riskLevel   : RiskLevel,
    val isSystem    : Boolean
)

// ── Threat / detection entry ───────────────────────────
enum class ThreatType {
    ACCESSIBILITY_SERVICE,
    DANGEROUS_COMBO,
    OVERLAY_ACTIVE,
    DEVICE_ADMIN
}

data class ThreatEntry(
    val packageName : String,
    val appName     : String,
    val icon        : Drawable?,
    val threatType  : ThreatType,
    val detail      : String,
    val reason      : String,        // Descripción en español para el usuario
    val riskLevel   : RiskLevel,
    val isSystem    : Boolean
)

// ── Camera/Mic grant entry (Guard tab — no false timestamps) ──
data class SensorAccessEntry(
    val packageName : String,
    val appName     : String,
    val icon        : Drawable?,
    val accessType  : SensorType,
    val lastAccess  : Long,          // 0 if unknown / not tracked
    val accessCount : Int            // 0 if not tracked
)

enum class SensorType { CAMERA, MICROPHONE, BOTH }

// ── History entry (from AccessLog — service logged events only) ──
data class AccessHistoryEntry(
    val packageName : String,
    val appName     : String,
    val icon        : Drawable?,
    val permission  : String,
    val category    : PermCategory,
    val timestamp   : Long,
    val durationMs  : Long
)

// ── Device-level anomaly entry ─────────────────────────
enum class AnomalyType {
    ROOT_DETECTED, DEV_OPTIONS, USB_DEBUG, ADB_NETWORK,
    VPN_ACTIVE, USER_CERTIFICATE, OVERLAY_APPS,
    NOTIFICATION_LISTENERS, BATTERY_WHITELIST
}

data class AnomalyEntry(
    val title       : String,
    val description : String,
    val riskLevel   : RiskLevel,
    val type        : AnomalyType,
    val value       : String = "",
    val actionText  : String = "",
    val actionScheme: String = ""
)

// ── Security score ─────────────────────────────────────
data class SecurityScore(
    val score          : Int,
    val highRiskApps   : Int,
    val activeThreats  : Int,
    val totalApps      : Int,
    val sensorAccesses : Int
)
