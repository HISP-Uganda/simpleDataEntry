package com.ash.simpledataentry;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.view.View;
import androidx.fragment.app.Fragment;
import androidx.hilt.work.HiltWorkerFactory;
import androidx.hilt.work.WorkerAssistedFactory;
import androidx.hilt.work.WorkerFactoryModule_ProvideFactoryFactory;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;
import com.ash.simpledataentry.data.SessionManager;
import com.ash.simpledataentry.data.cache.MetadataCacheService;
import com.ash.simpledataentry.data.local.AppDatabase;
import com.ash.simpledataentry.data.local.CategoryComboDao;
import com.ash.simpledataentry.data.local.CategoryOptionComboDao;
import com.ash.simpledataentry.data.local.DataElementDao;
import com.ash.simpledataentry.data.local.DataValueDao;
import com.ash.simpledataentry.data.local.DataValueDraftDao;
import com.ash.simpledataentry.data.local.DatasetDao;
import com.ash.simpledataentry.data.local.OrganisationUnitDao;
import com.ash.simpledataentry.data.repositoryImpl.AuthRepositoryImpl;
import com.ash.simpledataentry.data.repositoryImpl.LoginUrlCacheRepository;
import com.ash.simpledataentry.data.repositoryImpl.SavedAccountRepository;
import com.ash.simpledataentry.data.repositoryImpl.ValidationRepository;
import com.ash.simpledataentry.data.security.AccountEncryption;
import com.ash.simpledataentry.data.sync.BackgroundDataPrefetcher;
import com.ash.simpledataentry.data.sync.BackgroundSyncManager;
import com.ash.simpledataentry.data.sync.BackgroundSyncWorker;
import com.ash.simpledataentry.data.sync.BackgroundSyncWorker_AssistedFactory;
import com.ash.simpledataentry.data.sync.NetworkStateManager;
import com.ash.simpledataentry.data.sync.SyncQueueManager;
import com.ash.simpledataentry.di.AppModule_ProvideAccountEncryptionFactory;
import com.ash.simpledataentry.di.AppModule_ProvideAppDatabaseFactory;
import com.ash.simpledataentry.di.AppModule_ProvideAuthRepositoryFactory;
import com.ash.simpledataentry.di.AppModule_ProvideBackgroundDataPrefetcherFactory;
import com.ash.simpledataentry.di.AppModule_ProvideBackgroundSyncManagerFactory;
import com.ash.simpledataentry.di.AppModule_ProvideCategoryComboDaoFactory;
import com.ash.simpledataentry.di.AppModule_ProvideCategoryOptionComboDaoFactory;
import com.ash.simpledataentry.di.AppModule_ProvideDataElementDaoFactory;
import com.ash.simpledataentry.di.AppModule_ProvideDataEntryRepositoryFactory;
import com.ash.simpledataentry.di.AppModule_ProvideDataEntryUseCasesFactory;
import com.ash.simpledataentry.di.AppModule_ProvideDataValueDaoFactory;
import com.ash.simpledataentry.di.AppModule_ProvideDataValueDraftDaoFactory;
import com.ash.simpledataentry.di.AppModule_ProvideDatasetDaoFactory;
import com.ash.simpledataentry.di.AppModule_ProvideDatasetInstancesRepositoryFactory;
import com.ash.simpledataentry.di.AppModule_ProvideDatasetsRepositoryFactory;
import com.ash.simpledataentry.di.AppModule_ProvideFilterDatasetsUseCaseFactory;
import com.ash.simpledataentry.di.AppModule_ProvideGetDatasetInstancesUseCaseFactory;
import com.ash.simpledataentry.di.AppModule_ProvideGetDatasetsUseCaseFactory;
import com.ash.simpledataentry.di.AppModule_ProvideLoginUrlCacheRepositoryFactory;
import com.ash.simpledataentry.di.AppModule_ProvideLogoutUseCaseFactory;
import com.ash.simpledataentry.di.AppModule_ProvideMetadataCacheServiceFactory;
import com.ash.simpledataentry.di.AppModule_ProvideNetworkStateManagerFactory;
import com.ash.simpledataentry.di.AppModule_ProvideOrganisationUnitDaoFactory;
import com.ash.simpledataentry.di.AppModule_ProvideSavedAccountRepositoryFactory;
import com.ash.simpledataentry.di.AppModule_ProvideSessionManagerFactory;
import com.ash.simpledataentry.di.AppModule_ProvideSettingsRepositoryFactory;
import com.ash.simpledataentry.di.AppModule_ProvideSyncDatasetInstancesUseCaseFactory;
import com.ash.simpledataentry.di.AppModule_ProvideSyncDatasetsUseCaseFactory;
import com.ash.simpledataentry.di.AppModule_ProvideSyncQueueManagerFactory;
import com.ash.simpledataentry.di.AppModule_ProvideSystemRepositoryFactory;
import com.ash.simpledataentry.di.AppModule_ProvideValidationRepositoryFactory;
import com.ash.simpledataentry.di.AppModule_ProvideValidationServiceFactory;
import com.ash.simpledataentry.domain.repository.AuthRepository;
import com.ash.simpledataentry.domain.repository.DataEntryRepository;
import com.ash.simpledataentry.domain.repository.DatasetInstancesRepository;
import com.ash.simpledataentry.domain.repository.DatasetsRepository;
import com.ash.simpledataentry.domain.repository.SettingsRepository;
import com.ash.simpledataentry.domain.repository.SystemRepository;
import com.ash.simpledataentry.domain.useCase.DataEntryUseCases;
import com.ash.simpledataentry.domain.useCase.FilterDatasetsUseCase;
import com.ash.simpledataentry.domain.useCase.GetDatasetInstancesUseCase;
import com.ash.simpledataentry.domain.useCase.GetDatasetsUseCase;
import com.ash.simpledataentry.domain.useCase.LoginUseCase;
import com.ash.simpledataentry.domain.useCase.LogoutUseCase;
import com.ash.simpledataentry.domain.useCase.SyncDatasetInstancesUseCase;
import com.ash.simpledataentry.domain.useCase.SyncDatasetsUseCase;
import com.ash.simpledataentry.domain.validation.ValidationService;
import com.ash.simpledataentry.presentation.MainActivity;
import com.ash.simpledataentry.presentation.MainActivity_MembersInjector;
import com.ash.simpledataentry.presentation.dataEntry.DataEntryViewModel;
import com.ash.simpledataentry.presentation.dataEntry.DataEntryViewModel_HiltModules;
import com.ash.simpledataentry.presentation.dataEntry.DataEntryViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.ash.simpledataentry.presentation.dataEntry.DataEntryViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.ash.simpledataentry.presentation.datasetInstances.DatasetInstancesViewModel;
import com.ash.simpledataentry.presentation.datasetInstances.DatasetInstancesViewModel_HiltModules;
import com.ash.simpledataentry.presentation.datasetInstances.DatasetInstancesViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.ash.simpledataentry.presentation.datasetInstances.DatasetInstancesViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.ash.simpledataentry.presentation.datasets.DatasetsViewModel;
import com.ash.simpledataentry.presentation.datasets.DatasetsViewModel_HiltModules;
import com.ash.simpledataentry.presentation.datasets.DatasetsViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.ash.simpledataentry.presentation.datasets.DatasetsViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.ash.simpledataentry.presentation.event.EventInstancesViewModel;
import com.ash.simpledataentry.presentation.event.EventInstancesViewModel_HiltModules;
import com.ash.simpledataentry.presentation.event.EventInstancesViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.ash.simpledataentry.presentation.event.EventInstancesViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.ash.simpledataentry.presentation.issues.ReportIssuesViewModel;
import com.ash.simpledataentry.presentation.issues.ReportIssuesViewModel_HiltModules;
import com.ash.simpledataentry.presentation.issues.ReportIssuesViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.ash.simpledataentry.presentation.issues.ReportIssuesViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.ash.simpledataentry.presentation.login.AccountSelectionViewModel;
import com.ash.simpledataentry.presentation.login.AccountSelectionViewModel_HiltModules;
import com.ash.simpledataentry.presentation.login.AccountSelectionViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.ash.simpledataentry.presentation.login.AccountSelectionViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.ash.simpledataentry.presentation.login.LoginViewModel;
import com.ash.simpledataentry.presentation.login.LoginViewModel_HiltModules;
import com.ash.simpledataentry.presentation.login.LoginViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.ash.simpledataentry.presentation.login.LoginViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.ash.simpledataentry.presentation.settings.SettingsViewModel;
import com.ash.simpledataentry.presentation.settings.SettingsViewModel_HiltModules;
import com.ash.simpledataentry.presentation.settings.SettingsViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.ash.simpledataentry.presentation.settings.SettingsViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.ash.simpledataentry.presentation.tracker.EventCaptureViewModel;
import com.ash.simpledataentry.presentation.tracker.EventCaptureViewModel_HiltModules;
import com.ash.simpledataentry.presentation.tracker.EventCaptureViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.ash.simpledataentry.presentation.tracker.EventCaptureViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.ash.simpledataentry.presentation.tracker.EventsTableViewModel;
import com.ash.simpledataentry.presentation.tracker.EventsTableViewModel_HiltModules;
import com.ash.simpledataentry.presentation.tracker.EventsTableViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.ash.simpledataentry.presentation.tracker.EventsTableViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.ash.simpledataentry.presentation.tracker.TrackerDashboardViewModel;
import com.ash.simpledataentry.presentation.tracker.TrackerDashboardViewModel_HiltModules;
import com.ash.simpledataentry.presentation.tracker.TrackerDashboardViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.ash.simpledataentry.presentation.tracker.TrackerDashboardViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.ash.simpledataentry.presentation.tracker.TrackerEnrollmentTableViewModel;
import com.ash.simpledataentry.presentation.tracker.TrackerEnrollmentTableViewModel_HiltModules;
import com.ash.simpledataentry.presentation.tracker.TrackerEnrollmentTableViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.ash.simpledataentry.presentation.tracker.TrackerEnrollmentTableViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.ash.simpledataentry.presentation.tracker.TrackerEnrollmentViewModel;
import com.ash.simpledataentry.presentation.tracker.TrackerEnrollmentViewModel_HiltModules;
import com.ash.simpledataentry.presentation.tracker.TrackerEnrollmentViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.ash.simpledataentry.presentation.tracker.TrackerEnrollmentViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.ash.simpledataentry.presentation.tracker.TrackerEnrollmentsViewModel;
import com.ash.simpledataentry.presentation.tracker.TrackerEnrollmentsViewModel_HiltModules;
import com.ash.simpledataentry.presentation.tracker.TrackerEnrollmentsViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.ash.simpledataentry.presentation.tracker.TrackerEnrollmentsViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dagger.hilt.android.ActivityRetainedLifecycle;
import dagger.hilt.android.ViewModelLifecycle;
import dagger.hilt.android.internal.builders.ActivityComponentBuilder;
import dagger.hilt.android.internal.builders.ActivityRetainedComponentBuilder;
import dagger.hilt.android.internal.builders.FragmentComponentBuilder;
import dagger.hilt.android.internal.builders.ServiceComponentBuilder;
import dagger.hilt.android.internal.builders.ViewComponentBuilder;
import dagger.hilt.android.internal.builders.ViewModelComponentBuilder;
import dagger.hilt.android.internal.builders.ViewWithFragmentComponentBuilder;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories_InternalFactoryFactory_Factory;
import dagger.hilt.android.internal.managers.ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory;
import dagger.hilt.android.internal.managers.SavedStateHandleHolder;
import dagger.hilt.android.internal.modules.ApplicationContextModule;
import dagger.hilt.android.internal.modules.ApplicationContextModule_ProvideApplicationFactory;
import dagger.hilt.android.internal.modules.ApplicationContextModule_ProvideContextFactory;
import dagger.internal.DaggerGenerated;
import dagger.internal.DoubleCheck;
import dagger.internal.LazyClassKeyMap;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.SingleCheck;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class DaggerSimpleDataEntry_HiltComponents_SingletonC {
  private DaggerSimpleDataEntry_HiltComponents_SingletonC() {
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private ApplicationContextModule applicationContextModule;

    private Builder() {
    }

    public Builder applicationContextModule(ApplicationContextModule applicationContextModule) {
      this.applicationContextModule = Preconditions.checkNotNull(applicationContextModule);
      return this;
    }

    public SimpleDataEntry_HiltComponents.SingletonC build() {
      Preconditions.checkBuilderRequirement(applicationContextModule, ApplicationContextModule.class);
      return new SingletonCImpl(applicationContextModule);
    }
  }

  private static final class ActivityRetainedCBuilder implements SimpleDataEntry_HiltComponents.ActivityRetainedC.Builder {
    private final SingletonCImpl singletonCImpl;

    private SavedStateHandleHolder savedStateHandleHolder;

    private ActivityRetainedCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ActivityRetainedCBuilder savedStateHandleHolder(
        SavedStateHandleHolder savedStateHandleHolder) {
      this.savedStateHandleHolder = Preconditions.checkNotNull(savedStateHandleHolder);
      return this;
    }

    @Override
    public SimpleDataEntry_HiltComponents.ActivityRetainedC build() {
      Preconditions.checkBuilderRequirement(savedStateHandleHolder, SavedStateHandleHolder.class);
      return new ActivityRetainedCImpl(singletonCImpl, savedStateHandleHolder);
    }
  }

  private static final class ActivityCBuilder implements SimpleDataEntry_HiltComponents.ActivityC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private Activity activity;

    private ActivityCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ActivityCBuilder activity(Activity activity) {
      this.activity = Preconditions.checkNotNull(activity);
      return this;
    }

    @Override
    public SimpleDataEntry_HiltComponents.ActivityC build() {
      Preconditions.checkBuilderRequirement(activity, Activity.class);
      return new ActivityCImpl(singletonCImpl, activityRetainedCImpl, activity);
    }
  }

  private static final class FragmentCBuilder implements SimpleDataEntry_HiltComponents.FragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private Fragment fragment;

    private FragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public FragmentCBuilder fragment(Fragment fragment) {
      this.fragment = Preconditions.checkNotNull(fragment);
      return this;
    }

    @Override
    public SimpleDataEntry_HiltComponents.FragmentC build() {
      Preconditions.checkBuilderRequirement(fragment, Fragment.class);
      return new FragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragment);
    }
  }

  private static final class ViewWithFragmentCBuilder implements SimpleDataEntry_HiltComponents.ViewWithFragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private View view;

    private ViewWithFragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;
    }

    @Override
    public ViewWithFragmentCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public SimpleDataEntry_HiltComponents.ViewWithFragmentC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewWithFragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl, view);
    }
  }

  private static final class ViewCBuilder implements SimpleDataEntry_HiltComponents.ViewC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private View view;

    private ViewCBuilder(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public ViewCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public SimpleDataEntry_HiltComponents.ViewC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, view);
    }
  }

  private static final class ViewModelCBuilder implements SimpleDataEntry_HiltComponents.ViewModelC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private SavedStateHandle savedStateHandle;

    private ViewModelLifecycle viewModelLifecycle;

    private ViewModelCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ViewModelCBuilder savedStateHandle(SavedStateHandle handle) {
      this.savedStateHandle = Preconditions.checkNotNull(handle);
      return this;
    }

    @Override
    public ViewModelCBuilder viewModelLifecycle(ViewModelLifecycle viewModelLifecycle) {
      this.viewModelLifecycle = Preconditions.checkNotNull(viewModelLifecycle);
      return this;
    }

    @Override
    public SimpleDataEntry_HiltComponents.ViewModelC build() {
      Preconditions.checkBuilderRequirement(savedStateHandle, SavedStateHandle.class);
      Preconditions.checkBuilderRequirement(viewModelLifecycle, ViewModelLifecycle.class);
      return new ViewModelCImpl(singletonCImpl, activityRetainedCImpl, savedStateHandle, viewModelLifecycle);
    }
  }

  private static final class ServiceCBuilder implements SimpleDataEntry_HiltComponents.ServiceC.Builder {
    private final SingletonCImpl singletonCImpl;

    private Service service;

    private ServiceCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ServiceCBuilder service(Service service) {
      this.service = Preconditions.checkNotNull(service);
      return this;
    }

    @Override
    public SimpleDataEntry_HiltComponents.ServiceC build() {
      Preconditions.checkBuilderRequirement(service, Service.class);
      return new ServiceCImpl(singletonCImpl, service);
    }
  }

  private static final class ViewWithFragmentCImpl extends SimpleDataEntry_HiltComponents.ViewWithFragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private final ViewWithFragmentCImpl viewWithFragmentCImpl = this;

    ViewWithFragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;


    }
  }

  private static final class FragmentCImpl extends SimpleDataEntry_HiltComponents.FragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl = this;

    FragmentCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl, Fragment fragmentParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return activityCImpl.getHiltInternalFactoryFactory();
    }

    @Override
    public ViewWithFragmentComponentBuilder viewWithFragmentComponentBuilder() {
      return new ViewWithFragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl);
    }
  }

  private static final class ViewCImpl extends SimpleDataEntry_HiltComponents.ViewC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final ViewCImpl viewCImpl = this;

    ViewCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }
  }

  private static final class ActivityCImpl extends SimpleDataEntry_HiltComponents.ActivityC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl = this;

    ActivityCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        Activity activityParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;


    }

    @Override
    public void injectMainActivity(MainActivity mainActivity) {
      injectMainActivity2(mainActivity);
    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return DefaultViewModelFactories_InternalFactoryFactory_Factory.newInstance(getViewModelKeys(), new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl));
    }

    @Override
    public Map<Class<?>, Boolean> getViewModelKeys() {
      return LazyClassKeyMap.<Boolean>of(ImmutableMap.<String, Boolean>builder().put(AccountSelectionViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, AccountSelectionViewModel_HiltModules.KeyModule.provide()).put(DataEntryViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, DataEntryViewModel_HiltModules.KeyModule.provide()).put(DatasetInstancesViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, DatasetInstancesViewModel_HiltModules.KeyModule.provide()).put(DatasetsViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, DatasetsViewModel_HiltModules.KeyModule.provide()).put(EventCaptureViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, EventCaptureViewModel_HiltModules.KeyModule.provide()).put(EventInstancesViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, EventInstancesViewModel_HiltModules.KeyModule.provide()).put(EventsTableViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, EventsTableViewModel_HiltModules.KeyModule.provide()).put(LoginViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, LoginViewModel_HiltModules.KeyModule.provide()).put(ReportIssuesViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, ReportIssuesViewModel_HiltModules.KeyModule.provide()).put(SettingsViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, SettingsViewModel_HiltModules.KeyModule.provide()).put(TrackerDashboardViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, TrackerDashboardViewModel_HiltModules.KeyModule.provide()).put(TrackerEnrollmentTableViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, TrackerEnrollmentTableViewModel_HiltModules.KeyModule.provide()).put(TrackerEnrollmentViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, TrackerEnrollmentViewModel_HiltModules.KeyModule.provide()).put(TrackerEnrollmentsViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, TrackerEnrollmentsViewModel_HiltModules.KeyModule.provide()).build());
    }

    @Override
    public ViewModelComponentBuilder getViewModelComponentBuilder() {
      return new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public FragmentComponentBuilder fragmentComponentBuilder() {
      return new FragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    @Override
    public ViewComponentBuilder viewComponentBuilder() {
      return new ViewCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    @CanIgnoreReturnValue
    private MainActivity injectMainActivity2(MainActivity instance) {
      MainActivity_MembersInjector.injectSessionManager(instance, singletonCImpl.provideSessionManagerProvider.get());
      return instance;
    }
  }

  private static final class ViewModelCImpl extends SimpleDataEntry_HiltComponents.ViewModelC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ViewModelCImpl viewModelCImpl = this;

    Provider<AccountSelectionViewModel> accountSelectionViewModelProvider;

    Provider<DataEntryViewModel> dataEntryViewModelProvider;

    Provider<DatasetInstancesViewModel> datasetInstancesViewModelProvider;

    Provider<DatasetsViewModel> datasetsViewModelProvider;

    Provider<EventCaptureViewModel> eventCaptureViewModelProvider;

    Provider<EventInstancesViewModel> eventInstancesViewModelProvider;

    Provider<EventsTableViewModel> eventsTableViewModelProvider;

    Provider<LoginViewModel> loginViewModelProvider;

    Provider<ReportIssuesViewModel> reportIssuesViewModelProvider;

    Provider<SettingsViewModel> settingsViewModelProvider;

    Provider<TrackerDashboardViewModel> trackerDashboardViewModelProvider;

    Provider<TrackerEnrollmentTableViewModel> trackerEnrollmentTableViewModelProvider;

    Provider<TrackerEnrollmentViewModel> trackerEnrollmentViewModelProvider;

    Provider<TrackerEnrollmentsViewModel> trackerEnrollmentsViewModelProvider;

    ViewModelCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        SavedStateHandle savedStateHandleParam, ViewModelLifecycle viewModelLifecycleParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;

      initialize(savedStateHandleParam, viewModelLifecycleParam);

    }

    LoginUseCase loginUseCase() {
      return new LoginUseCase(singletonCImpl.provideAuthRepositoryProvider.get());
    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandle savedStateHandleParam,
        final ViewModelLifecycle viewModelLifecycleParam) {
      this.accountSelectionViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 0);
      this.dataEntryViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 1);
      this.datasetInstancesViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 2);
      this.datasetsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 3);
      this.eventCaptureViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 4);
      this.eventInstancesViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 5);
      this.eventsTableViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 6);
      this.loginViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 7);
      this.reportIssuesViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 8);
      this.settingsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 9);
      this.trackerDashboardViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 10);
      this.trackerEnrollmentTableViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 11);
      this.trackerEnrollmentViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 12);
      this.trackerEnrollmentsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 13);
    }

    @Override
    public Map<Class<?>, javax.inject.Provider<ViewModel>> getHiltViewModelMap() {
      return LazyClassKeyMap.<javax.inject.Provider<ViewModel>>of(ImmutableMap.<String, javax.inject.Provider<ViewModel>>builder().put(AccountSelectionViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (accountSelectionViewModelProvider))).put(DataEntryViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (dataEntryViewModelProvider))).put(DatasetInstancesViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (datasetInstancesViewModelProvider))).put(DatasetsViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (datasetsViewModelProvider))).put(EventCaptureViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (eventCaptureViewModelProvider))).put(EventInstancesViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (eventInstancesViewModelProvider))).put(EventsTableViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (eventsTableViewModelProvider))).put(LoginViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (loginViewModelProvider))).put(ReportIssuesViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (reportIssuesViewModelProvider))).put(SettingsViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (settingsViewModelProvider))).put(TrackerDashboardViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (trackerDashboardViewModelProvider))).put(TrackerEnrollmentTableViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (trackerEnrollmentTableViewModelProvider))).put(TrackerEnrollmentViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (trackerEnrollmentViewModelProvider))).put(TrackerEnrollmentsViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (trackerEnrollmentsViewModelProvider))).build());
    }

    @Override
    public Map<Class<?>, Object> getHiltViewModelAssistedMap() {
      return ImmutableMap.<Class<?>, Object>of();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final ViewModelCImpl viewModelCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          ViewModelCImpl viewModelCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.viewModelCImpl = viewModelCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // com.ash.simpledataentry.presentation.login.AccountSelectionViewModel
          return (T) new AccountSelectionViewModel(singletonCImpl.provideSavedAccountRepositoryProvider.get());

          case 1: // com.ash.simpledataentry.presentation.dataEntry.DataEntryViewModel
          return (T) new DataEntryViewModel(ApplicationContextModule_ProvideApplicationFactory.provideApplication(singletonCImpl.applicationContextModule), singletonCImpl.provideDataEntryRepositoryProvider.get(), singletonCImpl.provideDataEntryUseCasesProvider.get(), singletonCImpl.dataValueDraftDao(), singletonCImpl.provideValidationRepositoryProvider.get(), singletonCImpl.provideSyncQueueManagerProvider.get(), singletonCImpl.provideNetworkStateManagerProvider.get(), singletonCImpl.provideSessionManagerProvider.get());

          case 2: // com.ash.simpledataentry.presentation.datasetInstances.DatasetInstancesViewModel
          return (T) new DatasetInstancesViewModel(singletonCImpl.provideGetDatasetInstancesUseCaseProvider.get(), singletonCImpl.provideSyncDatasetInstancesUseCaseProvider.get(), singletonCImpl.provideDataEntryRepositoryProvider.get(), singletonCImpl.provideDatasetInstancesRepositoryProvider.get(), singletonCImpl.provideDatasetsRepositoryProvider.get(), singletonCImpl.dataValueDraftDao(), singletonCImpl.provideSyncQueueManagerProvider.get(), singletonCImpl.provideSessionManagerProvider.get(), ApplicationContextModule_ProvideApplicationFactory.provideApplication(singletonCImpl.applicationContextModule));

          case 3: // com.ash.simpledataentry.presentation.datasets.DatasetsViewModel
          return (T) new DatasetsViewModel(singletonCImpl.provideDatasetsRepositoryProvider.get(), singletonCImpl.provideGetDatasetsUseCaseProvider.get(), singletonCImpl.provideSyncDatasetsUseCaseProvider.get(), singletonCImpl.provideFilterDatasetsUseCaseProvider.get(), singletonCImpl.provideLogoutUseCaseProvider.get(), singletonCImpl.provideBackgroundSyncManagerProvider.get(), singletonCImpl.provideBackgroundDataPrefetcherProvider.get(), singletonCImpl.provideSessionManagerProvider.get(), singletonCImpl.provideSavedAccountRepositoryProvider.get(), singletonCImpl.provideSyncQueueManagerProvider.get());

          case 4: // com.ash.simpledataentry.presentation.tracker.EventCaptureViewModel
          return (T) new EventCaptureViewModel(ApplicationContextModule_ProvideApplicationFactory.provideApplication(singletonCImpl.applicationContextModule), singletonCImpl.provideSessionManagerProvider.get(), singletonCImpl.provideDatasetInstancesRepositoryProvider.get(), singletonCImpl.provideSyncQueueManagerProvider.get(), singletonCImpl.provideNetworkStateManagerProvider.get());

          case 5: // com.ash.simpledataentry.presentation.event.EventInstancesViewModel
          return (T) new EventInstancesViewModel(singletonCImpl.provideDatasetInstancesRepositoryProvider.get(), singletonCImpl.provideSessionManagerProvider.get(), ApplicationContextModule_ProvideApplicationFactory.provideApplication(singletonCImpl.applicationContextModule));

          case 6: // com.ash.simpledataentry.presentation.tracker.EventsTableViewModel
          return (T) new EventsTableViewModel(singletonCImpl.provideDatasetInstancesRepositoryProvider.get(), singletonCImpl.provideSessionManagerProvider.get(), ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 7: // com.ash.simpledataentry.presentation.login.LoginViewModel
          return (T) new LoginViewModel(viewModelCImpl.loginUseCase(), singletonCImpl.provideLoginUrlCacheRepositoryProvider.get(), singletonCImpl.provideSavedAccountRepositoryProvider.get(), singletonCImpl.authRepositoryImplProvider.get());

          case 8: // com.ash.simpledataentry.presentation.issues.ReportIssuesViewModel
          return (T) new ReportIssuesViewModel();

          case 9: // com.ash.simpledataentry.presentation.settings.SettingsViewModel
          return (T) new SettingsViewModel(singletonCImpl.provideSavedAccountRepositoryProvider.get(), singletonCImpl.provideAccountEncryptionProvider.get(), singletonCImpl.provideSettingsRepositoryProvider.get(), singletonCImpl.provideBackgroundSyncManagerProvider.get(), singletonCImpl.provideAppDatabaseProvider.get(), singletonCImpl.provideSessionManagerProvider.get());

          case 10: // com.ash.simpledataentry.presentation.tracker.TrackerDashboardViewModel
          return (T) new TrackerDashboardViewModel(singletonCImpl.provideSessionManagerProvider.get(), singletonCImpl.provideDatasetInstancesRepositoryProvider.get(), singletonCImpl.provideSyncQueueManagerProvider.get(), singletonCImpl.provideNetworkStateManagerProvider.get());

          case 11: // com.ash.simpledataentry.presentation.tracker.TrackerEnrollmentTableViewModel
          return (T) new TrackerEnrollmentTableViewModel(singletonCImpl.provideDatasetInstancesRepositoryProvider.get(), singletonCImpl.provideSessionManagerProvider.get(), ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 12: // com.ash.simpledataentry.presentation.tracker.TrackerEnrollmentViewModel
          return (T) new TrackerEnrollmentViewModel(ApplicationContextModule_ProvideApplicationFactory.provideApplication(singletonCImpl.applicationContextModule), singletonCImpl.provideSessionManagerProvider.get(), singletonCImpl.provideDatasetInstancesRepositoryProvider.get(), singletonCImpl.provideSyncQueueManagerProvider.get(), singletonCImpl.provideNetworkStateManagerProvider.get());

          case 13: // com.ash.simpledataentry.presentation.tracker.TrackerEnrollmentsViewModel
          return (T) new TrackerEnrollmentsViewModel(singletonCImpl.provideDatasetInstancesRepositoryProvider.get(), singletonCImpl.provideSessionManagerProvider.get(), ApplicationContextModule_ProvideApplicationFactory.provideApplication(singletonCImpl.applicationContextModule));

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ActivityRetainedCImpl extends SimpleDataEntry_HiltComponents.ActivityRetainedC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl = this;

    Provider<ActivityRetainedLifecycle> provideActivityRetainedLifecycleProvider;

    ActivityRetainedCImpl(SingletonCImpl singletonCImpl,
        SavedStateHandleHolder savedStateHandleHolderParam) {
      this.singletonCImpl = singletonCImpl;

      initialize(savedStateHandleHolderParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandleHolder savedStateHandleHolderParam) {
      this.provideActivityRetainedLifecycleProvider = DoubleCheck.provider(new SwitchingProvider<ActivityRetainedLifecycle>(singletonCImpl, activityRetainedCImpl, 0));
    }

    @Override
    public ActivityComponentBuilder activityComponentBuilder() {
      return new ActivityCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public ActivityRetainedLifecycle getActivityRetainedLifecycle() {
      return provideActivityRetainedLifecycleProvider.get();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // dagger.hilt.android.ActivityRetainedLifecycle
          return (T) ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory.provideActivityRetainedLifecycle();

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ServiceCImpl extends SimpleDataEntry_HiltComponents.ServiceC {
    private final SingletonCImpl singletonCImpl;

    private final ServiceCImpl serviceCImpl = this;

    ServiceCImpl(SingletonCImpl singletonCImpl, Service serviceParam) {
      this.singletonCImpl = singletonCImpl;


    }
  }

  private static final class SingletonCImpl extends SimpleDataEntry_HiltComponents.SingletonC {
    private final ApplicationContextModule applicationContextModule;

    private final SingletonCImpl singletonCImpl = this;

    Provider<SessionManager> provideSessionManagerProvider;

    Provider<SystemRepository> provideSystemRepositoryProvider;

    Provider<AppDatabase> provideAppDatabaseProvider;

    Provider<DatasetInstancesRepository> provideDatasetInstancesRepositoryProvider;

    Provider<DatasetsRepository> provideDatasetsRepositoryProvider;

    Provider<NetworkStateManager> provideNetworkStateManagerProvider;

    Provider<BackgroundSyncWorker_AssistedFactory> backgroundSyncWorker_AssistedFactoryProvider;

    Provider<AccountEncryption> provideAccountEncryptionProvider;

    Provider<SavedAccountRepository> provideSavedAccountRepositoryProvider;

    Provider<MetadataCacheService> provideMetadataCacheServiceProvider;

    Provider<SyncQueueManager> provideSyncQueueManagerProvider;

    Provider<DataEntryRepository> provideDataEntryRepositoryProvider;

    Provider<DataEntryUseCases> provideDataEntryUseCasesProvider;

    Provider<ValidationService> provideValidationServiceProvider;

    Provider<ValidationRepository> provideValidationRepositoryProvider;

    Provider<GetDatasetInstancesUseCase> provideGetDatasetInstancesUseCaseProvider;

    Provider<SyncDatasetInstancesUseCase> provideSyncDatasetInstancesUseCaseProvider;

    Provider<GetDatasetsUseCase> provideGetDatasetsUseCaseProvider;

    Provider<SyncDatasetsUseCase> provideSyncDatasetsUseCaseProvider;

    Provider<FilterDatasetsUseCase> provideFilterDatasetsUseCaseProvider;

    Provider<AuthRepository> provideAuthRepositoryProvider;

    Provider<LogoutUseCase> provideLogoutUseCaseProvider;

    Provider<BackgroundSyncManager> provideBackgroundSyncManagerProvider;

    Provider<BackgroundDataPrefetcher> provideBackgroundDataPrefetcherProvider;

    Provider<LoginUrlCacheRepository> provideLoginUrlCacheRepositoryProvider;

    Provider<AuthRepositoryImpl> authRepositoryImplProvider;

    Provider<SettingsRepository> provideSettingsRepositoryProvider;

    SingletonCImpl(ApplicationContextModule applicationContextModuleParam) {
      this.applicationContextModule = applicationContextModuleParam;
      initialize(applicationContextModuleParam);
      initialize2(applicationContextModuleParam);

    }

    DatasetDao datasetDao() {
      return AppModule_ProvideDatasetDaoFactory.provideDatasetDao(provideAppDatabaseProvider.get());
    }

    Map<String, javax.inject.Provider<WorkerAssistedFactory<? extends ListenableWorker>>> mapOfStringAndProviderOfWorkerAssistedFactoryOf(
        ) {
      return ImmutableMap.<String, javax.inject.Provider<WorkerAssistedFactory<? extends ListenableWorker>>>of("com.ash.simpledataentry.data.sync.BackgroundSyncWorker", ((Provider) (backgroundSyncWorker_AssistedFactoryProvider)));
    }

    HiltWorkerFactory hiltWorkerFactory() {
      return WorkerFactoryModule_ProvideFactoryFactory.provideFactory(mapOfStringAndProviderOfWorkerAssistedFactoryOf());
    }

    DataValueDraftDao dataValueDraftDao() {
      return AppModule_ProvideDataValueDraftDaoFactory.provideDataValueDraftDao(provideAppDatabaseProvider.get());
    }

    DataElementDao dataElementDao() {
      return AppModule_ProvideDataElementDaoFactory.provideDataElementDao(provideAppDatabaseProvider.get());
    }

    CategoryComboDao categoryComboDao() {
      return AppModule_ProvideCategoryComboDaoFactory.provideCategoryComboDao(provideAppDatabaseProvider.get());
    }

    CategoryOptionComboDao categoryOptionComboDao() {
      return AppModule_ProvideCategoryOptionComboDaoFactory.provideCategoryOptionComboDao(provideAppDatabaseProvider.get());
    }

    OrganisationUnitDao organisationUnitDao() {
      return AppModule_ProvideOrganisationUnitDaoFactory.provideOrganisationUnitDao(provideAppDatabaseProvider.get());
    }

    DataValueDao dataValueDao() {
      return AppModule_ProvideDataValueDaoFactory.provideDataValueDao(provideAppDatabaseProvider.get());
    }

    @SuppressWarnings("unchecked")
    private void initialize(final ApplicationContextModule applicationContextModuleParam) {
      this.provideSessionManagerProvider = DoubleCheck.provider(new SwitchingProvider<SessionManager>(singletonCImpl, 1));
      this.provideSystemRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<SystemRepository>(singletonCImpl, 0));
      this.provideAppDatabaseProvider = DoubleCheck.provider(new SwitchingProvider<AppDatabase>(singletonCImpl, 4));
      this.provideDatasetInstancesRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<DatasetInstancesRepository>(singletonCImpl, 5));
      this.provideDatasetsRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<DatasetsRepository>(singletonCImpl, 3));
      this.provideNetworkStateManagerProvider = DoubleCheck.provider(new SwitchingProvider<NetworkStateManager>(singletonCImpl, 6));
      this.backgroundSyncWorker_AssistedFactoryProvider = SingleCheck.provider(new SwitchingProvider<BackgroundSyncWorker_AssistedFactory>(singletonCImpl, 2));
      this.provideAccountEncryptionProvider = DoubleCheck.provider(new SwitchingProvider<AccountEncryption>(singletonCImpl, 8));
      this.provideSavedAccountRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<SavedAccountRepository>(singletonCImpl, 7));
      this.provideMetadataCacheServiceProvider = DoubleCheck.provider(new SwitchingProvider<MetadataCacheService>(singletonCImpl, 10));
      this.provideSyncQueueManagerProvider = DoubleCheck.provider(new SwitchingProvider<SyncQueueManager>(singletonCImpl, 11));
      this.provideDataEntryRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<DataEntryRepository>(singletonCImpl, 9));
      this.provideDataEntryUseCasesProvider = DoubleCheck.provider(new SwitchingProvider<DataEntryUseCases>(singletonCImpl, 12));
      this.provideValidationServiceProvider = DoubleCheck.provider(new SwitchingProvider<ValidationService>(singletonCImpl, 14));
      this.provideValidationRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<ValidationRepository>(singletonCImpl, 13));
      this.provideGetDatasetInstancesUseCaseProvider = DoubleCheck.provider(new SwitchingProvider<GetDatasetInstancesUseCase>(singletonCImpl, 15));
      this.provideSyncDatasetInstancesUseCaseProvider = DoubleCheck.provider(new SwitchingProvider<SyncDatasetInstancesUseCase>(singletonCImpl, 16));
      this.provideGetDatasetsUseCaseProvider = DoubleCheck.provider(new SwitchingProvider<GetDatasetsUseCase>(singletonCImpl, 17));
      this.provideSyncDatasetsUseCaseProvider = DoubleCheck.provider(new SwitchingProvider<SyncDatasetsUseCase>(singletonCImpl, 18));
      this.provideFilterDatasetsUseCaseProvider = DoubleCheck.provider(new SwitchingProvider<FilterDatasetsUseCase>(singletonCImpl, 19));
      this.provideAuthRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<AuthRepository>(singletonCImpl, 21));
      this.provideLogoutUseCaseProvider = DoubleCheck.provider(new SwitchingProvider<LogoutUseCase>(singletonCImpl, 20));
      this.provideBackgroundSyncManagerProvider = DoubleCheck.provider(new SwitchingProvider<BackgroundSyncManager>(singletonCImpl, 22));
      this.provideBackgroundDataPrefetcherProvider = DoubleCheck.provider(new SwitchingProvider<BackgroundDataPrefetcher>(singletonCImpl, 23));
      this.provideLoginUrlCacheRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<LoginUrlCacheRepository>(singletonCImpl, 24));
    }

    @SuppressWarnings("unchecked")
    private void initialize2(final ApplicationContextModule applicationContextModuleParam) {
      this.authRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<AuthRepositoryImpl>(singletonCImpl, 25));
      this.provideSettingsRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<SettingsRepository>(singletonCImpl, 26));
    }

    @Override
    public void injectSimpleDataEntry(SimpleDataEntry simpleDataEntry) {
      injectSimpleDataEntry2(simpleDataEntry);
    }

    @Override
    public Set<Boolean> getDisableFragmentGetContextFix() {
      return ImmutableSet.<Boolean>of();
    }

    @Override
    public ActivityRetainedComponentBuilder retainedComponentBuilder() {
      return new ActivityRetainedCBuilder(singletonCImpl);
    }

    @Override
    public ServiceComponentBuilder serviceComponentBuilder() {
      return new ServiceCBuilder(singletonCImpl);
    }

    @CanIgnoreReturnValue
    private SimpleDataEntry injectSimpleDataEntry2(SimpleDataEntry instance) {
      SimpleDataEntry_MembersInjector.injectSystemRepository(instance, provideSystemRepositoryProvider.get());
      SimpleDataEntry_MembersInjector.injectWorkerFactory(instance, hiltWorkerFactory());
      return instance;
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // com.ash.simpledataentry.domain.repository.SystemRepository
          return (T) AppModule_ProvideSystemRepositoryFactory.provideSystemRepository(singletonCImpl.provideSessionManagerProvider.get());

          case 1: // com.ash.simpledataentry.data.SessionManager
          return (T) AppModule_ProvideSessionManagerFactory.provideSessionManager();

          case 2: // com.ash.simpledataentry.data.sync.BackgroundSyncWorker_AssistedFactory
          return (T) new BackgroundSyncWorker_AssistedFactory() {
            @Override
            public BackgroundSyncWorker create(Context context, WorkerParameters workerParams) {
              return new BackgroundSyncWorker(context, workerParams, singletonCImpl.provideSessionManagerProvider.get(), singletonCImpl.provideDatasetsRepositoryProvider.get(), singletonCImpl.provideDatasetInstancesRepositoryProvider.get(), singletonCImpl.provideNetworkStateManagerProvider.get(), singletonCImpl.provideAppDatabaseProvider.get());
            }
          };

          case 3: // com.ash.simpledataentry.domain.repository.DatasetsRepository
          return (T) AppModule_ProvideDatasetsRepositoryFactory.provideDatasetsRepository(singletonCImpl.provideSessionManagerProvider.get(), singletonCImpl.datasetDao(), ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.provideDatasetInstancesRepositoryProvider.get());

          case 4: // com.ash.simpledataentry.data.local.AppDatabase
          return (T) AppModule_ProvideAppDatabaseFactory.provideAppDatabase(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 5: // com.ash.simpledataentry.domain.repository.DatasetInstancesRepository
          return (T) AppModule_ProvideDatasetInstancesRepositoryFactory.provideDatasetInstancesRepository(singletonCImpl.provideSessionManagerProvider.get(), singletonCImpl.provideAppDatabaseProvider.get());

          case 6: // com.ash.simpledataentry.data.sync.NetworkStateManager
          return (T) AppModule_ProvideNetworkStateManagerFactory.provideNetworkStateManager(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 7: // com.ash.simpledataentry.data.repositoryImpl.SavedAccountRepository
          return (T) AppModule_ProvideSavedAccountRepositoryFactory.provideSavedAccountRepository(singletonCImpl.provideAppDatabaseProvider.get(), singletonCImpl.provideAccountEncryptionProvider.get());

          case 8: // com.ash.simpledataentry.data.security.AccountEncryption
          return (T) AppModule_ProvideAccountEncryptionFactory.provideAccountEncryption();

          case 9: // com.ash.simpledataentry.domain.repository.DataEntryRepository
          return (T) AppModule_ProvideDataEntryRepositoryFactory.provideDataEntryRepository(singletonCImpl.provideSessionManagerProvider.get(), singletonCImpl.dataValueDraftDao(), singletonCImpl.dataElementDao(), singletonCImpl.categoryComboDao(), singletonCImpl.categoryOptionComboDao(), singletonCImpl.organisationUnitDao(), singletonCImpl.dataValueDao(), singletonCImpl.provideMetadataCacheServiceProvider.get(), singletonCImpl.provideNetworkStateManagerProvider.get(), singletonCImpl.provideSyncQueueManagerProvider.get(), ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 10: // com.ash.simpledataentry.data.cache.MetadataCacheService
          return (T) AppModule_ProvideMetadataCacheServiceFactory.provideMetadataCacheService(singletonCImpl.provideSessionManagerProvider.get(), singletonCImpl.dataElementDao(), singletonCImpl.categoryComboDao(), singletonCImpl.categoryOptionComboDao(), singletonCImpl.organisationUnitDao(), singletonCImpl.dataValueDao());

          case 11: // com.ash.simpledataentry.data.sync.SyncQueueManager
          return (T) AppModule_ProvideSyncQueueManagerFactory.provideSyncQueueManager(singletonCImpl.provideNetworkStateManagerProvider.get(), singletonCImpl.provideSessionManagerProvider.get(), singletonCImpl.provideAppDatabaseProvider.get(), ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 12: // com.ash.simpledataentry.domain.useCase.DataEntryUseCases
          return (T) AppModule_ProvideDataEntryUseCasesFactory.provideDataEntryUseCases(singletonCImpl.provideDataEntryRepositoryProvider.get(), singletonCImpl.provideDatasetInstancesRepositoryProvider.get());

          case 13: // com.ash.simpledataentry.data.repositoryImpl.ValidationRepository
          return (T) AppModule_ProvideValidationRepositoryFactory.provideValidationRepository(singletonCImpl.provideValidationServiceProvider.get());

          case 14: // com.ash.simpledataentry.domain.validation.ValidationService
          return (T) AppModule_ProvideValidationServiceFactory.provideValidationService(singletonCImpl.provideSessionManagerProvider.get());

          case 15: // com.ash.simpledataentry.domain.useCase.GetDatasetInstancesUseCase
          return (T) AppModule_ProvideGetDatasetInstancesUseCaseFactory.provideGetDatasetInstancesUseCase(singletonCImpl.provideDatasetInstancesRepositoryProvider.get());

          case 16: // com.ash.simpledataentry.domain.useCase.SyncDatasetInstancesUseCase
          return (T) AppModule_ProvideSyncDatasetInstancesUseCaseFactory.provideSyncDatasetInstancesUseCase(singletonCImpl.provideDatasetInstancesRepositoryProvider.get());

          case 17: // com.ash.simpledataentry.domain.useCase.GetDatasetsUseCase
          return (T) AppModule_ProvideGetDatasetsUseCaseFactory.provideGetDatasetsUseCase(singletonCImpl.provideDatasetsRepositoryProvider.get());

          case 18: // com.ash.simpledataentry.domain.useCase.SyncDatasetsUseCase
          return (T) AppModule_ProvideSyncDatasetsUseCaseFactory.provideSyncDatasetsUseCase(singletonCImpl.provideDatasetsRepositoryProvider.get());

          case 19: // com.ash.simpledataentry.domain.useCase.FilterDatasetsUseCase
          return (T) AppModule_ProvideFilterDatasetsUseCaseFactory.provideFilterDatasetsUseCase(singletonCImpl.provideDatasetsRepositoryProvider.get());

          case 20: // com.ash.simpledataentry.domain.useCase.LogoutUseCase
          return (T) AppModule_ProvideLogoutUseCaseFactory.provideLogoutUseCase(singletonCImpl.provideAuthRepositoryProvider.get());

          case 21: // com.ash.simpledataentry.domain.repository.AuthRepository
          return (T) AppModule_ProvideAuthRepositoryFactory.provideAuthRepository(singletonCImpl.provideSessionManagerProvider.get());

          case 22: // com.ash.simpledataentry.data.sync.BackgroundSyncManager
          return (T) AppModule_ProvideBackgroundSyncManagerFactory.provideBackgroundSyncManager(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 23: // com.ash.simpledataentry.data.sync.BackgroundDataPrefetcher
          return (T) AppModule_ProvideBackgroundDataPrefetcherFactory.provideBackgroundDataPrefetcher(singletonCImpl.provideSessionManagerProvider.get(), singletonCImpl.provideMetadataCacheServiceProvider.get(), singletonCImpl.datasetDao());

          case 24: // com.ash.simpledataentry.data.repositoryImpl.LoginUrlCacheRepository
          return (T) AppModule_ProvideLoginUrlCacheRepositoryFactory.provideLoginUrlCacheRepository(singletonCImpl.provideAppDatabaseProvider.get());

          case 25: // com.ash.simpledataentry.data.repositoryImpl.AuthRepositoryImpl
          return (T) new AuthRepositoryImpl(singletonCImpl.provideSessionManagerProvider.get());

          case 26: // com.ash.simpledataentry.domain.repository.SettingsRepository
          return (T) AppModule_ProvideSettingsRepositoryFactory.provideSettingsRepository(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          default: throw new AssertionError(id);
        }
      }
    }
  }
}
