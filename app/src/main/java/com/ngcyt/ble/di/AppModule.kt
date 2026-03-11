package com.ngcyt.ble.di

import android.content.Context
import androidx.room.Room
import com.ngcyt.ble.data.db.AppDatabase
import com.ngcyt.ble.data.settings.AppSettings
import com.ngcyt.ble.data.settings.dataStore
import com.ngcyt.ble.domain.detection.DetectionEngine
import com.ngcyt.ble.domain.fingerprint.BleFingerprintEngineImpl
import com.ngcyt.ble.domain.similarity.BehaviorSimilarityEngineImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "ngcytble.db").build()
    }

    @Provides
    @Singleton
    fun provideAppSettings(@ApplicationContext context: Context): AppSettings {
        return AppSettings(context.dataStore)
    }

    @Provides
    @Singleton
    fun provideBleFingerprintEngine(): BleFingerprintEngineImpl {
        return BleFingerprintEngineImpl()
    }

    @Provides
    @Singleton
    fun provideBehaviorSimilarityEngine(): BehaviorSimilarityEngineImpl {
        return BehaviorSimilarityEngineImpl()
    }

    @Provides
    @Singleton
    fun provideDetectionEngine(
        fingerprintEngine: BleFingerprintEngineImpl,
        similarityEngine: BehaviorSimilarityEngineImpl,
    ): DetectionEngine {
        return DetectionEngine(
            fingerprintEngine = fingerprintEngine,
            similarityEngine = similarityEngine,
        )
    }
}
