package com.v2ir.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// FIX (Bug #28): Removed unused injected dependencies (ConfigRepository, SubscriptionRepository,
// CloudflareIpDatabase). The class body was empty — seedIfEmpty() did nothing — yet Hilt was
// keeping all three dependencies alive for the full app lifetime. Cleaned up to hold only
// what's actually needed (currently nothing beyond @ApplicationContext for future use).
@Singleton
class SeedDataInitializer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Seed initial data if the database is empty.
     * Currently a no-op — default configs/subscriptions removed per product decision.
     * Reserved for future first-run onboarding logic.
     */
    suspend fun seedIfEmpty() {
        // No default configs or subscriptions
    }
}




