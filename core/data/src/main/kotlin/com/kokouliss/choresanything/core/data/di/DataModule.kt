package com.kokouliss.choresanything.core.data.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.kokouliss.choresanything.core.data.firebase.FirebaseAuthRepository
import com.kokouliss.choresanything.core.data.firebase.FirestoreActivityRepository
import com.kokouliss.choresanything.core.data.firebase.FirestoreChoreRepository
import com.kokouliss.choresanything.core.data.firebase.FirestoreHouseholdRepository
import com.kokouliss.choresanything.core.domain.repository.ActivityRepository
import com.kokouliss.choresanything.core.domain.repository.AuthRepository
import com.kokouliss.choresanything.core.domain.repository.ChoreRepository
import com.kokouliss.choresanything.core.domain.repository.HouseholdRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindHouseholdRepository(impl: FirestoreHouseholdRepository): HouseholdRepository

    @Binds
    @Singleton
    abstract fun bindChoreRepository(impl: FirestoreChoreRepository): ChoreRepository

    @Binds
    @Singleton
    abstract fun bindActivityRepository(impl: FirestoreActivityRepository): ActivityRepository

    companion object {
        @Provides
        @Singleton
        fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

        @Provides
        @Singleton
        fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

        @Provides
        @Singleton
        fun provideMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()

        @Provides
        @IoDispatcher
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
