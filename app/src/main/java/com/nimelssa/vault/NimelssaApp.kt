package com.nimelssa.vault

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.nimelssa.vault.data.FirestoreCourseSync
import com.nimelssa.vault.data.OfflineManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Custom Application class that initialises:
 * 1. Firestore offline persistence  — caches queried data locally
 * 2. OfflineManager                 — tracks saved courses + downloaded files
 * 3. FirestoreCourseSync            — loads persisted course resources into memory
 */
class NimelssaApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // 1. Enable Firestore disk persistence (via settings builder)
        FirebaseFirestore.getInstance().firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()

        // 2. Initialise the offline file manager
        OfflineManager.init(this)

        // 3. Load persisted course resources from Firestore
        appScope.launch {
            FirestoreCourseSync.loadAll()
        }
    }
}
