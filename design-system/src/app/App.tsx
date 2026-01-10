import { useState } from 'react';
import { LoginScreen } from './components/LoginScreen';
import { LoadingScreen } from './components/LoadingScreen';
import { DatasetSelection } from './components/DatasetSelection';
import { EntryList } from './components/EntryList';
import { CreateEntryScreen } from './components/CreateEntryScreen';
import { DataEntryForm } from './components/DataEntryForm';
import { EventList } from './components/EventList';
import { TrackedEntityList } from './components/TrackedEntityList';
import { TrackedEntityDetails } from './components/TrackedEntityDetails';
import { RegisterTrackedEntity } from './components/RegisterTrackedEntity';
import { CreateEventScreen } from './components/CreateEventScreen';
import { SettingsScreen } from './components/SettingsScreen';
import { AboutScreen } from './components/AboutScreen';
import { ReportIssuesScreen } from './components/ReportIssuesScreen';
import { SyncDialog } from './components/SyncDialog';
import { Toaster } from './components/ui/sonner';
import { toast } from 'sonner';

type Screen = 
  | 'login' 
  | 'loadingLogin' 
  | 'datasets' 
  | 'entries' 
  | 'loadingEntry' 
  | 'createEntry' 
  | 'form' 
  | 'eventList'
  | 'createEvent'
  | 'eventForm'
  | 'trackerList'
  | 'trackerDetails'
  | 'registerEntity'
  | 'trackerEvent'
  | 'createTrackerEvent'
  | 'settings' 
  | 'about' 
  | 'report' 
  | 'loadingSync';

type ProgramType = 'dataset' | 'event' | 'tracker';

export default function App() {
  const [currentScreen, setCurrentScreen] = useState<Screen>('login');
  const [isOffline, setIsOffline] = useState(false);
  const [selectedProgram, setSelectedProgram] = useState<{
    id: string; 
    name: string; 
    type: ProgramType;
  } | null>(null);
  const [selectedEntry, setSelectedEntry] = useState<{id: string; title: string} | null>(null);
  const [selectedEntity, setSelectedEntity] = useState<{id: string; name: string} | null>(null);
  const [showSyncDialog, setShowSyncDialog] = useState(false);

  const handleLogin = () => {
    setCurrentScreen('loadingLogin');
  };

  const handleLoginComplete = () => {
    setCurrentScreen('datasets');
    toast.success('Login successful! Metadata synced locally.');
  };

  // Handle dataset selection
  const handleSelectDataset = (datasetId: string) => {
    const datasets: Record<string, string> = {
      '1': 'Child Health Program',
      '2': 'Malaria Case Reporting',
      '3': 'Maternal Health Services',
      '4': 'Immunization Coverage'
    };
    setSelectedProgram({ 
      id: datasetId, 
      name: datasets[datasetId] || 'Unknown Dataset',
      type: 'dataset'
    });
    setCurrentScreen('entries');
  };

  // Handle program selection (event or tracker)
  const handleSelectProgram = (programId: string, type: 'event' | 'tracker') => {
    const eventPrograms: Record<string, string> = {
      'e1': 'Outpatient Consultation',
      'e2': 'Birth Registration',
      'e3': 'Death Notification'
    };
    
    const trackerPrograms: Record<string, string> = {
      't1': 'Antenatal Care',
      't2': 'TB Treatment',
      't3': 'Child Growth Monitoring',
      't4': 'HIV Care & Treatment'
    };
    
    const programs = type === 'event' ? eventPrograms : trackerPrograms;
    
    setSelectedProgram({ 
      id: programId, 
      name: programs[programId] || 'Unknown Program',
      type
    });
    
    if (type === 'event') {
      setCurrentScreen('eventList');
    } else {
      setCurrentScreen('trackerList');
    }
  };

  // Dataset entry handlers
  const handleSelectEntry = (entryId: string) => {
    const entries: Record<string, string> = {
      '1': 'January 2026 - District Hospital',
      '2': 'December 2025 - District Hospital',
      '3': 'January 2026 - Health Center A',
      '4': 'December 2025 - Health Center A',
      '5': 'November 2025 - District Hospital',
      '6': 'January 2026 - Health Center B'
    };
    setSelectedEntry({ 
      id: entryId, 
      title: entries[entryId] || 'New Entry'
    });
    setCurrentScreen('loadingEntry');
  };

  const handleCreateNewEntry = () => {
    setCurrentScreen('createEntry');
  };

  const handleCreateEntry = (entry: { period: string; orgUnit: string; attributes: Record<string, string> }) => {
    setSelectedEntry({ 
      id: 'new', 
      title: `${entry.period} - ${entry.orgUnit}` 
    });
    toast.success('New entry created');
    setCurrentScreen('loadingEntry');
  };

  const handleEntryLoadComplete = () => {
    setCurrentScreen('form');
  };

  // Event program handlers
  const handleSelectEvent = (eventId: string) => {
    setSelectedEntry({ 
      id: eventId, 
      title: `Event ${eventId}` 
    });
    setCurrentScreen('loadingEntry');
  };

  const handleCreateNewEvent = () => {
    setCurrentScreen('createEvent');
  };

  const handleCreateEvent = (data: Record<string, string>) => {
    setSelectedEntry({ 
      id: 'new', 
      title: `${data.eventDate} - ${data.orgUnit}` 
    });
    toast.success('Event created successfully');
    setCurrentScreen('loadingEntry');
  };

  const handleEventLoadComplete = () => {
    setCurrentScreen('eventForm');
  };

  // Tracker program handlers
  const handleSelectEntity = (entityId: string) => {
    const entities: Record<string, string> = {
      '1': 'Sarah Johnson',
      '2': 'Mary Williams',
      '3': 'Emma Davis',
      '4': 'Lisa Brown'
    };
    setSelectedEntity({ 
      id: entityId, 
      name: entities[entityId] || 'Unknown Patient'
    });
    setCurrentScreen('trackerDetails');
  };

  const handleCreateNewEntity = () => {
    setCurrentScreen('registerEntity');
  };

  const handleRegisterEntity = (data: Record<string, string>) => {
    setSelectedEntity({ 
      id: 'new', 
      name: `${data.firstName} ${data.lastName}` 
    });
    toast.success('Patient registered successfully');
    setCurrentScreen('trackerDetails');
  };

  const handleSelectTrackerEvent = (eventId: string) => {
    setSelectedEntry({ 
      id: eventId, 
      title: `Event ${eventId}` 
    });
    setCurrentScreen('loadingEntry');
  };

  const handleAddTrackerEvent = () => {
    setCurrentScreen('createTrackerEvent');
  };

  const handleCreateTrackerEvent = (data: Record<string, string>) => {
    setSelectedEntry({ 
      id: 'new', 
      title: `${data.eventDate} - ${data.orgUnit}` 
    });
    toast.success('Visit created successfully');
    setCurrentScreen('loadingEntry');
  };

  const handleTrackerEventLoadComplete = () => {
    setCurrentScreen('trackerEvent');
  };

  const handleEditEntity = () => {
    toast.info('Edit patient feature coming soon');
  };

  // General handlers
  const handleSync = () => {
    if (isOffline) {
      toast.error('Cannot sync while offline');
    } else {
      setShowSyncDialog(true);
    }
  };

  const handleSyncMode = (mode: 'download' | 'upload-download') => {
    setShowSyncDialog(false);
    setCurrentScreen('loadingSync');
  };

  const handleSyncComplete = () => {
    setCurrentScreen('datasets');
    toast.success('Sync completed successfully');
  };

  const handleNavigate = (screen: 'settings' | 'about' | 'report') => {
    setCurrentScreen(screen);
  };

  // Back navigation handlers
  const handleBackToDatasets = () => {
    setSelectedProgram(null);
    setSelectedEntry(null);
    setSelectedEntity(null);
    setCurrentScreen('datasets');
  };

  const handleBackFromList = () => {
    setSelectedProgram(null);
    setCurrentScreen('datasets');
  };

  const handleBackToList = () => {
    setSelectedEntry(null);
    if (selectedProgram?.type === 'dataset') {
      setCurrentScreen('entries');
    } else if (selectedProgram?.type === 'event') {
      setCurrentScreen('eventList');
    }
  };

  const handleBackToTrackerList = () => {
    setSelectedEntity(null);
    setCurrentScreen('trackerList');
  };

  const handleBackToEntityDetails = () => {
    setSelectedEntry(null);
    setCurrentScreen('trackerDetails');
  };

  const handleLogout = () => {
    toast.success('Logged out successfully');
    setCurrentScreen('login');
    setSelectedProgram(null);
    setSelectedEntry(null);
    setSelectedEntity(null);
  };

  const handleDeleteAccount = () => {
    toast.success('Account deleted');
    setCurrentScreen('login');
    setSelectedProgram(null);
    setSelectedEntry(null);
    setSelectedEntity(null);
  };

  return (
    <div className="h-screen w-full max-w-md mx-auto bg-gray-50 relative overflow-hidden">
      {currentScreen === 'login' && (
        <LoginScreen onLogin={handleLogin} />
      )}
      
      {currentScreen === 'loadingLogin' && (
        <LoadingScreen type="login" onComplete={handleLoginComplete} />
      )}
      
      {currentScreen === 'datasets' && (
        <DatasetSelection 
          onSelectDataset={handleSelectDataset}
          onSelectProgram={handleSelectProgram}
          isOffline={isOffline}
          onSync={handleSync}
          onNavigate={handleNavigate}
          onLogout={handleLogout}
          onDeleteAccount={handleDeleteAccount}
        />
      )}
      
      {/* Dataset Screens */}
      {currentScreen === 'entries' && selectedProgram && selectedProgram.type === 'dataset' && (
        <EntryList
          datasetName={selectedProgram.name}
          onBack={handleBackFromList}
          onSelectEntry={handleSelectEntry}
          onCreateNew={handleCreateNewEntry}
          isOffline={isOffline}
        />
      )}
      
      {currentScreen === 'createEntry' && selectedProgram && selectedProgram.type === 'dataset' && (
        <CreateEntryScreen
          datasetName={selectedProgram.name}
          onBack={handleBackToList}
          onCreate={handleCreateEntry}
        />
      )}
      
      {currentScreen === 'form' && selectedProgram && selectedEntry && (
        <DataEntryForm
          datasetName={selectedProgram.name}
          entryTitle={selectedEntry.title}
          onBack={handleBackToList}
          isOffline={isOffline}
        />
      )}
      
      {/* Event Program Screens */}
      {currentScreen === 'eventList' && selectedProgram && selectedProgram.type === 'event' && (
        <EventList
          programName={selectedProgram.name}
          onBack={handleBackFromList}
          onSelectEvent={handleSelectEvent}
          onCreateNew={handleCreateNewEvent}
          isOffline={isOffline}
        />
      )}
      
      {currentScreen === 'createEvent' && selectedProgram && selectedProgram.type === 'event' && (
        <CreateEventScreen
          programName={selectedProgram.name}
          onBack={handleBackToList}
          onCreate={handleCreateEvent}
        />
      )}
      
      {currentScreen === 'eventForm' && selectedProgram && selectedEntry && (
        <DataEntryForm
          datasetName={selectedProgram.name}
          entryTitle={selectedEntry.title}
          onBack={handleBackToList}
          isOffline={isOffline}
        />
      )}
      
      {/* Tracker Program Screens */}
      {currentScreen === 'trackerList' && selectedProgram && selectedProgram.type === 'tracker' && (
        <TrackedEntityList
          programName={selectedProgram.name}
          onBack={handleBackFromList}
          onSelectEntity={handleSelectEntity}
          onCreateNew={handleCreateNewEntity}
          isOffline={isOffline}
        />
      )}
      
      {currentScreen === 'registerEntity' && selectedProgram && selectedProgram.type === 'tracker' && (
        <RegisterTrackedEntity
          programName={selectedProgram.name}
          onBack={handleBackToTrackerList}
          onCreate={handleRegisterEntity}
        />
      )}
      
      {currentScreen === 'trackerDetails' && selectedProgram && selectedEntity && (
        <TrackedEntityDetails
          programName={selectedProgram.name}
          entityName={selectedEntity.name}
          onBack={handleBackToTrackerList}
          onAddEvent={handleAddTrackerEvent}
          onEditEntity={handleEditEntity}
          onSelectEvent={handleSelectTrackerEvent}
          isOffline={isOffline}
        />
      )}
      
      {currentScreen === 'createTrackerEvent' && selectedProgram && selectedEntity && (
        <CreateEventScreen
          programName={selectedProgram.name}
          programStage="first-anc"
          onBack={handleBackToEntityDetails}
          onCreate={handleCreateTrackerEvent}
        />
      )}
      
      {currentScreen === 'trackerEvent' && selectedProgram && selectedEntry && (
        <DataEntryForm
          datasetName={selectedProgram.name}
          entryTitle={selectedEntry.title}
          onBack={handleBackToEntityDetails}
          isOffline={isOffline}
        />
      )}
      
      {/* Loading screens */}
      {currentScreen === 'loadingEntry' && (
        <LoadingScreen 
          type="entry" 
          onComplete={
            selectedProgram?.type === 'event' 
              ? handleEventLoadComplete 
              : selectedProgram?.type === 'tracker'
              ? handleTrackerEventLoadComplete
              : handleEntryLoadComplete
          } 
        />
      )}
      
      {/* Settings & Info Screens */}
      {currentScreen === 'settings' && (
        <SettingsScreen onBack={handleBackToDatasets} />
      )}
      
      {currentScreen === 'about' && (
        <AboutScreen onBack={handleBackToDatasets} />
      )}
      
      {currentScreen === 'report' && (
        <ReportIssuesScreen onBack={handleBackToDatasets} />
      )}
      
      {currentScreen === 'loadingSync' && (
        <LoadingScreen type="sync" onComplete={handleSyncComplete} />
      )}
      
      <SyncDialog 
        open={showSyncDialog}
        onClose={() => setShowSyncDialog(false)}
        onSync={handleSyncMode}
      />
      
      <Toaster />
      
      {/* Developer Toggle for Offline Mode */}
      {currentScreen !== 'login' && currentScreen !== 'loadingLogin' && currentScreen !== 'loadingEntry' && currentScreen !== 'loadingSync' && (
        <button
          onClick={() => {
            setIsOffline(!isOffline);
            toast.info(isOffline ? 'Back online' : 'Switched to offline mode');
          }}
          className="fixed bottom-4 left-4 bg-gray-800 text-white text-xs px-3 py-2 rounded-full opacity-50 hover:opacity-100 z-50"
        >
          {isOffline ? '📡 Go Online' : '✈️ Go Offline'}
        </button>
      )}
    </div>
  );
}
