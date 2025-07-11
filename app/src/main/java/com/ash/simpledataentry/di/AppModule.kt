package com.ash.simpledataentry.di

import android.content.Context
import androidx.room.Room
import com.ash.simpledataentry.data.SessionManager
import com.ash.simpledataentry.data.repositoryImpl.AuthRepositoryImpl
import com.ash.simpledataentry.data.repositoryImpl.DataEntryRepositoryImpl
import com.ash.simpledataentry.data.repositoryImpl.DatasetInstancesRepositoryImpl
import com.ash.simpledataentry.data.repositoryImpl.DatasetsRepositoryImpl
import com.ash.simpledataentry.data.repositoryImpl.SystemRepositoryImpl
import com.ash.simpledataentry.data.local.AppDatabase
import com.ash.simpledataentry.data.local.DataValueDraftDao
import com.ash.simpledataentry.data.local.DataElementDao
import com.ash.simpledataentry.data.local.CategoryComboDao
import com.ash.simpledataentry.data.local.CategoryOptionComboDao
import com.ash.simpledataentry.data.local.DatasetDao
import com.ash.simpledataentry.data.local.OrganisationUnitDao
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
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ash.simpledataentry.data.local.DataValueDao

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE datasets ADD COLUMN description TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE data_elements ADD COLUMN description TEXT")
        }
    }

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
    fun provideDatasetsRepository(
        sessionManager: SessionManager,
        datasetDao: DatasetDao,
        @ApplicationContext context: Context
    ): DatasetsRepository {
        return DatasetsRepositoryImpl(sessionManager, datasetDao, context)
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
        sessionManager: SessionManager,
        draftDao: DataValueDraftDao,
        dataElementDao: DataElementDao,
        categoryComboDao: CategoryComboDao,
        categoryOptionComboDao: CategoryOptionComboDao,
        organisationUnitDao: OrganisationUnitDao,
        dataValueDao: DataValueDao,
        @ApplicationContext context: Context
    ): DataEntryRepository {
        return DataEntryRepositoryImpl(
            sessionManager,
            draftDao,
            dataElementDao,
            categoryComboDao,
            categoryOptionComboDao,
            organisationUnitDao,
            context,
            dataValueDao
        )
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

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "simple_data_entry_db"
        )
        .addMigrations(MIGRATION_1_2)
        .build()
    }

    @Provides
    fun provideDataValueDraftDao(db: AppDatabase): DataValueDraftDao = db.dataValueDraftDao()

    @Provides
    fun provideDatasetDao(db: AppDatabase): DatasetDao = db.datasetDao()

    @Provides
    fun provideDataElementDao(db: AppDatabase): DataElementDao = db.dataElementDao()

    @Provides
    fun provideCategoryComboDao(db: AppDatabase): CategoryComboDao = db.categoryComboDao()

    @Provides
    fun provideCategoryOptionComboDao(db: AppDatabase): CategoryOptionComboDao = db.categoryOptionComboDao()

    @Provides
    fun provideOrganisationUnitDao(db: AppDatabase): OrganisationUnitDao = db.organisationUnitDao()

    @Provides
    fun provideDataValueDao(db: AppDatabase): DataValueDao = db.dataValueDao()

}