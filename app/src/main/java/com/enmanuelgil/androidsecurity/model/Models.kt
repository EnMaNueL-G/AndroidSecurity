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
    val permissions : List<String>,           // all granted dangerous perms
    val categories  : Set<PermCategory>,      // categorized
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
    val detail      : String,          // human-readable description
    val riskLevel   : RiskLevel,
    val isSystem    : Boolean
)

// ── Camera/Mic access entry ────────────────────────────
data class SensorAccessEntry(
    val packageName : String,
    val appName     : String,
    val icon        : Drawable?,
    val accessType  : SensorType,
    val lastAccess  : Long,            // epoch ms
    val accessCount : Int              // in current day
)

enum class SensorType { CAMERA, MICROPHONE, BOTH }

// ── History entry ──────────────────────────────────────
data class AccessHistoryEntry(
    val packageName : String,
    val appName     : String,
    val icon        : Drawable?,
    val permission  : String,
    val category    : PermCategory,
    val timestamp   : Long,            // epoch ms
    val durationMs  : Long             // 0 if unknown
)

// ── Security score ─────────────────────────────────────
data class SecurityScore(
    val score          : Int,     // 0-100
    val highRiskApps   : Int,
    val activeThreats  : Int,
    val totalApps      : Int,
    val sensorAccesses : Int      // last 24h
)
