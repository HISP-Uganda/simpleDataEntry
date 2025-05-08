package com.ash.simpledataentry.di

import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.data.repositoryImpl.AuthRepositoryImpl
import com.ash.simpledataentry.data.repositoryImpl.DataEntryRepositoryImpl
import com.ash.simpledataentry.data.repositoryImpl.DatasetInstancesRepositoryImpl
import com.ash.simpledataentry.data.repositoryImpl.DatasetsRepositoryImpl
import com.ash.simpledataentry.data.repositoryImpl.SystemRepositoryImpl
import com.ash.simpledataentry.domain.repository.AuthRepository
import com.ash.simpledataentry.domain.repository.DataEntryRepository
import com.ash.simpledataentry.domain.repository.DatasetInstancesRepository
import com.ash.simpledataentry.domain.repository.DatasetsRepository
import com.ash.simpledataentry.domain.repository.SystemRepository
import com.ash.simpledataentry.domain.useCase.DataEntryUseCases
import com.ash.simpledataentry.domain.useCase.FilterDatasetsUseCase
import com.ash.simpledataentry.domain.useCase.GetDataValuesUseCase
import com.ash.simpledataentry.domain.useCase.GetDatasetInstancesUseCase
import com.ash.simpledataentry.domain.useCase.GetDatasetsUseCase
import com.ash.simpledataentry.domain.useCase.LogoutUseCase
import com.ash.simpledataentry.domain.useCase.SaveDataValueUseCase
import com.ash.simpledataentry.domain.useCase.SyncDatasetInstancesUseCase
import com.ash.simpledataentry.domain.useCase.SyncDatasetsUseCase
import com.ash.simpledataentry.domain.useCase.ValidateValueUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSessionManager(): SessionManager {
        return SessionManager()
    }

    @Provides
    @Singleton
    fun provideAuthRepository(sessionManager: SessionManager): AuthRepository {

        return AuthRepositoryImpl(sessionManager)

    }


    @Provides
    @Singleton
    fun provideSystemRepository(sessionManager: SessionManager): SystemRepository {

        return SystemRepositoryImpl(sessionManager)
    }

    @Provides
    @Singleton
    fun provideLogoutUseCase(authRepository: AuthRepository): LogoutUseCase {
        return LogoutUseCase(authRepository)
    }


    @Provides
    @Singleton
    fun provideDatasetsRepository(sessionManager: SessionManager): DatasetsRepository {
        return DatasetsRepositoryImpl(sessionManager)
    }

    /**
     * UseCase for retrieving all datasets.
     */
    @Provides
    @Singleton
    fun provideGetDatasetsUseCase(repository: DatasetsRepository): GetDatasetsUseCase {
        return GetDatasetsUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideSyncDatasetsUseCase(repository: DatasetsRepository): SyncDatasetsUseCase {
        return SyncDatasetsUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideFilterDatasetsUseCase(repository: DatasetsRepository): FilterDatasetsUseCase {
        return FilterDatasetsUseCase(repository)
    }


    @Provides
    @Singleton
    fun provideDatasetInstancesRepository(
        sessionManager: SessionManager
    ): DatasetInstancesRepository {
        return DatasetInstancesRepositoryImpl(sessionManager)
    }



    @Provides
    @Singleton
    fun provideGetDatasetInstancesUseCase(
        repository: DatasetInstancesRepository
    ): GetDatasetInstancesUseCase {
        return GetDatasetInstancesUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideSyncDatasetInstancesUseCase(
        repository: DatasetInstancesRepository
    ): SyncDatasetInstancesUseCase {
        return SyncDatasetInstancesUseCase(repository)
    }



    @Provides
    @Singleton
    fun provideDataEntryRepository(
        sessionManager: SessionManager
    ): DataEntryRepository {
        return DataEntryRepositoryImpl(sessionManager)
    }

    @Provides
    @Singleton
    fun provideGetDataValuesUseCase(
        repository: DataEntryRepository
    ): GetDataValuesUseCase {
        return GetDataValuesUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideSaveDataValueUseCase(
        repository: DataEntryRepository
    ): SaveDataValueUseCase {
        return SaveDataValueUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideValidateValueUseCase(
        repository: DataEntryRepository
    ): ValidateValueUseCase {
        return ValidateValueUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideDataEntryUseCases(
        getDataValuesUseCase: GetDataValuesUseCase,
        saveDataValueUseCase: SaveDataValueUseCase,
        validateValueUseCase: ValidateValueUseCase
    ): DataEntryUseCases {
        return DataEntryUseCases(
            getDataValues = getDataValuesUseCase,
            saveDataValue = saveDataValueUseCase,
            validateValue = validateValueUseCase
        )
    }


}