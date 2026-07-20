package com.v2ir.di

import android.content.Context
import androidx.room.Room
import com.v2ir.data.local.database.AppDatabase
import com.v2ir.data.local.database.ConfigDao
import com.v2ir.data.local.database.SubscriptionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        // callTimeout is a hard deadline for the entire call (connect + read + write).
        // Without this, a server that accepts the connection but never sends data
        // will hang the coroutine indefinitely — critical for restricted networks.
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7
            )
            // FIX (Bug #6): Removed fallbackToDestructiveMigration().
            // It was silently wiping ALL user data (configs, subscriptions) when a device
            // had a DB version outside the explicit migration chain (e.g., skipped a version).
            // With explicit migrations covering 1→7, Room will now throw an
            // IllegalStateException on an unhandled version, making problems visible
            // instead of silently destroying user data.
            .build()

    @Provides
    @Singleton
    fun provideConfigDao(database: AppDatabase): ConfigDao =
        database.configDao()

    @Provides
    @Singleton
    fun provideSubscriptionDao(database: AppDatabase): SubscriptionDao =
        database.subscriptionDao()
}




