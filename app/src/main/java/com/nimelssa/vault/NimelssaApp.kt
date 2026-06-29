package com.nimelssa.vault

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.nimelssa.vault.data.CourseRepository
import com.nimelssa.vault.data.DriveScanner
import com.nimelssa.vault.data.OfflineManager
import com.nimelssa.vault.data.ProposalRepository
import com.nimelssa.vault.data.ResourceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Custom Application class that initialises:
 * 1. Firestore offline persistence  — caches queried data locally
 * 2. OfflineManager                 — tracks saved courses + downloaded files
 * 3. CourseRepository               — loads course metadata from Firestore (seeds if empty)
 * 4. ResourceRepository             — loads all resources from Firestore
 * 5. DriveScanner                   — loads API key for Drive scanning
 * 6. ProposalRepository             — loads pending proposals from Firestore
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

        // 3. Load course metadata from Firestore (seeds 58 courses if empty)
        appScope.launch {
            CourseRepository.loadAll()
        }

        // 4. Load all resources from Firestore (migrates legacy format)
        appScope.launch {
            ResourceRepository.loadAll()
        }

        // 5. Initialise Drive scanner with API key from secrets.xml
        val apiKey = getString(R.string.drive_api_key)
        DriveScanner.init(apiKey)

        // 6. Load pending proposals from Firestore
        appScope.launch {
            ProposalRepository.loadAll()
        }
    }
}
