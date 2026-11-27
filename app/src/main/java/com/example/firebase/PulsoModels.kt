package com.example.firebase

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AnthiHierarchyLevel {
    SUPERADMIN,
    ADMIN,
    DRIVER
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val anthiUid: String? = null,
    val alias: String = "",
    val email: String = "",
    val focusedEvent: Long = 1L,
    val confirmedEvent: Long = 0L,
    val confirmedEventIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AdminGrant(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val alias: String = "",
    val role: String = "ADMIN",
    val inviteHash: String = "",
    @ServerTimestamp val grantedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AdminInvite(
    @DocumentId val id: String = "",
    val label: String = "",
    val active: Boolean = true,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class TacticalEvent(
    @DocumentId val id: String = "",
    val eventNumber: Long = 1L,
    val code: String = "",
    val callsign: String = "",
    val sector: String = "",
    val lat: Double = 7.1524,
    val lng: Double = -73.1296,
    val bearing: Long = 0L,
    val speedKmh: Long = 0L,
    val audioDb: Long = 60L,
    val active: Boolean = true,
    val status: String = "active",
    val createdBy: String = "",
    val closedBy: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    @ServerTimestamp val closedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class EventResponder(
    @DocumentId val id: String = "",
    val userId: String = "",
    val eventId: String = "",
    val callsign: String = "",
    val lat: Double = 7.1462,
    val lng: Double = -73.1332,
    val etaMin: Long = 3L,
    val active: Boolean = true,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
