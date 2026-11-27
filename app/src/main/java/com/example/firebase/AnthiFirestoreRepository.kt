package com.example.firebase

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

class AnthiFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        check(!uid.isNullOrBlank()) { "El usuario debe iniciar sesión con Google/ANTHI antes de acceder a Firestore." }
        return uid
    }

    fun isOwnerUser(): Boolean {
        val user = auth.currentUser ?: return false
        return user.isEmailVerified && user.email.equals(OWNER_EMAIL, ignoreCase = true)
    }

    fun observeActiveEvents(): Flow<List<TacticalEvent>> = flow {
        val uid = requireUserId()
        val path = "events"
        emitAll(
            db.collection(path)
                .whereEqualTo("active", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        TacticalEvent::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).filter { it.active && it.status == "active" }
                        .sortedBy { it.eventNumber }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeEventResponders(eventId: String): Flow<List<EventResponder>> = flow {
        val uid = requireUserId()
        val path = "events/$eventId/responders"
        emitAll(
            db.collection("events").document(eventId).collection("responders")
                .whereEqualTo("active", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        EventResponder::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAdminGrant(userId: String): Flow<AdminGrant?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Lectura de rol fuera de usuario autenticado." }
        val path = "admins/$userId"
        emitAll(
            db.collection("admins").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            AdminGrant::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Lectura de perfil fuera de usuario autenticado." }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeAdminInvites(): Flow<List<AdminInvite>> = flow {
        val uid = requireUserId()
        if (!isOwnerUser()) {
            emit(emptyList())
            return@flow
        }
        val path = "admin_invites"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AdminInvite::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(
        anthiUid: String?,
        alias: String?,
        focusedEvent: Long = 1L,
        confirmedEventIdsFromAnthi: List<String> = emptyList()
    ): Result<Unit> {
        val currentUser = auth.currentUser
            ?: return Result.failure(IllegalStateException("Sesión no iniciada."))
        val uid = currentUser.uid
        val path = "users/$uid"
        return try {
            val safeAlias = alias?.trim()?.takeIf { it.isNotEmpty() }?.take(50)
                ?: currentUser.displayName?.trim()?.takeIf { it.isNotEmpty() }?.take(50)
                ?: "Móvil ANTHI"
            val safeEmail = currentUser.email?.trim()?.takeIf { it.length >= 3 }?.take(120)
                ?: "usuario@anthi.local"
            val safeAnthiUid = anthiUid?.trim()?.takeIf { it.isNotEmpty() }?.take(128)
            val safeFocused = focusedEvent.coerceIn(1L, 999L)
            val validConfirmed = confirmedEventIdsFromAnthi
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.matches(ID_REGEX) }
                .distinct()
                .take(20)

            val docRef = db.collection("users").document(uid)
            val existingSnap = docRef.get().awaitTask()
            if (!existingSnap.exists()) {
                val payload = linkedMapOf<String, Any>(
                    "userId" to uid,
                    "alias" to safeAlias,
                    "email" to safeEmail,
                    "focusedEvent" to safeFocused,
                    "confirmedEvent" to (extractEventNumber(validConfirmed.firstOrNull()) ?: 0L),
                    "confirmedEventIds" to validConfirmed,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (safeAnthiUid != null) {
                    payload["anthiUid"] = safeAnthiUid
                }
                docRef.set(payload).awaitTask()
            } else {
                val updates = linkedMapOf<String, Any>(
                    "alias" to safeAlias,
                    "focusedEvent" to safeFocused,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (safeAnthiUid != null) {
                    updates["anthiUid"] = safeAnthiUid
                }
                if (validConfirmed.isNotEmpty()) {
                    updates["confirmedEventIds"] = FieldValue.arrayUnion(*validConfirmed.toTypedArray())
                    extractEventNumber(validConfirmed.firstOrNull())?.let { num ->
                        updates["confirmedEvent"] = num.coerceIn(0L, 999L)
                    }
                }
                docRef.update(updates).awaitTask()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    /**
     * Activa o asegura en Firebase el evento de emergencia disparado desde el escudito/calavera M34 de ANTHI.
     * Queda persistido con active=true y status="active" y NO se cierra al minimizar/bloquear pantalla.
     */
    suspend fun activateEmergencyEvent(
        eventId: String,
        eventNumber: Long,
        callsign: String,
        sector: String,
        lat: Double,
        lng: Double
    ): Result<String> {
        val cleanId = sanitizeEventId(eventId, eventNumber)
        val path = "events/$cleanId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("events").document(cleanId)
            val snap = docRef.get().awaitTask()
            if (!snap.exists()) {
                val safeNum = eventNumber.coerceIn(1L, 999L)
                val safeCallsign = callsign.trim().ifEmpty { "Móvil ANTHI" }.take(50)
                val safeSector = sector.trim().ifEmpty { "Alerta Escudito M34 · En ruta" }.take(120)
                val payload = mapOf(
                    "eventNumber" to safeNum,
                    "code" to "ANTHI-SOS-$safeNum",
                    "callsign" to safeCallsign,
                    "sector" to safeSector,
                    "lat" to lat.coerceIn(-90.0, 90.0),
                    "lng" to lng.coerceIn(-180.0, 180.0),
                    "bearing" to 0L,
                    "speedKmh" to 0L,
                    "audioDb" to 72L,
                    "active" to true,
                    "status" to "active",
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).awaitTask()
            }
            Result.success(cleanId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun seedInitialEventsIfAllowed(): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return try {
            val existing = db.collection("events")
                .whereEqualTo("active", true)
                .limit(1)
                .get()
                .awaitTask()
            if (!existing.isEmpty) {
                return Result.success(Unit)
            }
            val defaults = listOf(
                mapOf(
                    "id" to "ev_1",
                    "eventNumber" to 1L,
                    "code" to "ANTHI-3401",
                    "callsign" to "Móvil 14",
                    "sector" to "Sector Café Madrid · Hacia el Norte",
                    "lat" to 7.1524,
                    "lng" to -73.1296,
                    "bearing" to 12L,
                    "speedKmh" to 54L,
                    "audioDb" to 66L
                ),
                mapOf(
                    "id" to "ev_2",
                    "eventNumber" to 2L,
                    "code" to "ANTHI-3402",
                    "callsign" to "Móvil 29",
                    "sector" to "Autopista Norte Km 4 · Noroeste",
                    "lat" to 7.1645,
                    "lng" to -73.1338,
                    "bearing" to 340L,
                    "speedKmh" to 68L,
                    "audioDb" to 59L
                )
            )
            defaults.forEach { ev ->
                val docId = ev["id"] as String
                val payload = mapOf(
                    "eventNumber" to ev["eventNumber"]!!,
                    "code" to ev["code"]!!,
                    "callsign" to ev["callsign"]!!,
                    "sector" to ev["sector"]!!,
                    "lat" to ev["lat"]!!,
                    "lng" to ev["lng"]!!,
                    "bearing" to ev["bearing"]!!,
                    "speedKmh" to ev["speedKmh"]!!,
                    "audioDb" to ev["audioDb"]!!,
                    "active" to true,
                    "status" to "active",
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                db.collection("events").document(docId).set(payload).awaitTask()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "events")
            Result.failure(e)
        }
    }

    suspend fun confirmSiVoy(
        eventId: String,
        eventNumber: Long,
        callsign: String,
        lat: Double,
        lng: Double
    ): Result<Unit> {
        val cleanId = sanitizeEventId(eventId, eventNumber)
        val path = "events/$cleanId/responders"
        return try {
            val uid = requireUserId()
            val safeCallsign = callsign.trim().ifEmpty { "Móvil ANTHI" }.take(50)
            val responderRef = db.collection("events").document(cleanId)
                .collection("responders").document(uid)
            val payload = mapOf(
                "userId" to uid,
                "eventId" to cleanId,
                "callsign" to safeCallsign,
                "lat" to lat.coerceIn(-90.0, 90.0),
                "lng" to lng.coerceIn(-180.0, 180.0),
                "etaMin" to 3L,
                "active" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            responderRef.set(payload).awaitTask()
            db.collection("users").document(uid).update(
                mapOf(
                    "confirmedEvent" to eventNumber.coerceIn(0L, 999L),
                    "focusedEvent" to eventNumber.coerceIn(1L, 999L),
                    "confirmedEventIds" to FieldValue.arrayUnion(cleanId),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitTask()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun closeEvent(eventId: String): Result<Unit> {
        val path = "events/$eventId"
        return try {
            val uid = requireUserId()
            require(eventId.isNotBlank() && eventId.matches(ID_REGEX)) { "ID de evento inválido." }
            db.collection("events").document(eventId).update(
                mapOf(
                    "active" to false,
                    "status" to "closed",
                    "closedBy" to uid,
                    "closedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitTask()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteEvent(eventId: String): Result<Unit> {
        val path = "events/$eventId"
        return try {
            requireUserId()
            if (!isOwnerUser()) {
                return Result.failure(
                    SecurityException("Solo el Superadministrador / Dueño puede eliminar eventos.")
                )
            }
            require(eventId.isNotBlank() && eventId.matches(ID_REGEX)) { "ID de evento inválido." }
            db.collection("events").document(eventId).delete().awaitTask()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun createAdminInvite(label: String, rawCode: String): Result<String> {
        var path = "admin_invites"
        return try {
            val uid = requireUserId()
            if (!isOwnerUser()) {
                return Result.failure(
                    SecurityException("Solo el Superadministrador / Dueño puede crear códigos de permiso.")
                )
            }
            val cleanCode = rawCode.trim()
            require(cleanCode.length >= 4) { "El código debe tener al menos 4 caracteres." }
            val cleanLabel = label.trim().ifEmpty { "Central M34" }.take(60)
            val hash = sha256Hex(cleanCode)
            path = "admin_invites/$hash"

            db.collection("admin_invites").document(hash).set(
                mapOf(
                    "label" to cleanLabel,
                    "active" to true,
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).awaitTask()
            Result.success(hash)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun revokeAdminInvite(inviteHash: String): Result<Unit> {
        val path = "admin_invites/$inviteHash"
        return try {
            requireUserId()
            if (!isOwnerUser()) {
                return Result.failure(
                    SecurityException("Solo el Superadministrador / Dueño puede revocar códigos.")
                )
            }
            require(inviteHash.matches(ID_REGEX)) { "Hash de invitación inválido." }
            db.collection("admin_invites").document(inviteHash).update(
                mapOf("active" to false)
            ).awaitTask()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun redeemAdminCode(rawCode: String, alias: String?): Result<AdminGrant> {
        val currentUser = auth.currentUser
            ?: return Result.failure(IllegalStateException("Inicia sesión primero."))
        val uid = currentUser.uid
        val cleanCode = rawCode.trim()
        if (cleanCode.length < 4) {
            return Result.failure(IllegalArgumentException("Código demasiado corto."))
        }
        val hash = sha256Hex(cleanCode)
        val invitePath = "admin_invites/$hash"
        return try {
            val inviteSnap = db.collection("admin_invites").document(hash).get().awaitTask()
            if (!inviteSnap.exists() || inviteSnap.getBoolean("active") != true) {
                return Result.failure(SecurityException("Código de acceso inválido o revocado."))
            }

            val safeAlias = alias?.trim()?.takeIf { it.isNotEmpty() }?.take(50)
                ?: currentUser.displayName?.trim()?.takeIf { it.isNotEmpty() }?.take(50)
                ?: "Admin M34"
            val safeEmail = currentUser.email?.trim()?.takeIf { it.length >= 3 }?.take(120)
                ?: "admin@anthi.local"

            val grantPayload = mapOf(
                "userId" to uid,
                "email" to safeEmail,
                "alias" to safeAlias,
                "role" to "ADMIN",
                "inviteHash" to hash,
                "grantedAt" to FieldValue.serverTimestamp()
            )
            db.collection("admins").document(uid).set(grantPayload).awaitTask()
            Result.success(
                AdminGrant(
                    id = uid,
                    userId = uid,
                    email = safeEmail,
                    alias = safeAlias,
                    role = "ADMIN",
                    inviteHash = hash
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, invitePath)
            Result.failure(e)
        }
    }

    companion object {
        const val OWNER_EMAIL = "cm2026enero@gmail.com"
        private val ID_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")

        fun sanitizeEventId(rawEventId: String?, fallbackNum: Long): String {
            val cleaned = rawEventId?.trim()?.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                ?.takeIf { it.isNotBlank() }?.take(120)
            return cleaned ?: "ev_${fallbackNum.coerceIn(1L, 999L)}"
        }

        fun extractEventNumber(rawEventId: String?): Long? {
            if (rawEventId.isNullOrBlank()) return null
            val digits = Regex("(\\d+)").find(rawEventId)?.value?.toLongOrNull() ?: return null
            return digits.coerceIn(1L, 999L)
        }

        fun sha256Hex(input: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
