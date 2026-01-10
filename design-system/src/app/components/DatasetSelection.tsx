import { ChevronRight, Database, Menu, RefreshCw, WifiOff, Wifi, Settings, Info, AlertCircle, LogOut, UserX, Activity, Users } from 'lucide-react';
import { Button } from './ui/button';
import { Badge } from './ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from './ui/tabs';
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from './ui/sheet';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from './ui/alert-dialog';
import { Separator } from './ui/separator';
import { useState } from 'react';

interface Dataset {
  id: string;
  name: string;
  description: string;
  lastSync: string;
  entryCount: number;
}

interface Program {
  id: string;
  name: string;
  description: string;
  type: 'event' | 'tracker';
  lastSync: string;
  count: number;
  countLabel: string;
}

interface DatasetSelectionProps {
  onSelectDataset: (datasetId: string) => void;
  onSelectProgram: (programId: string, type: 'event' | 'tracker') => void;
  isOffline: boolean;
  onSync: () => void;
  onNavigate: (screen: 'settings' | 'about' | 'report') => void;
  onLogout: () => void;
  onDeleteAccount: () => void;
}

const mockDatasets: Dataset[] = [
  {
    id: '1',
    name: 'Child Health Program',
    description: 'Monthly reporting for child health indicators',
    lastSync: '2 hours ago',
    entryCount: 12
  },
  {
    id: '2',
    name: 'Malaria Case Reporting',
    description: 'Weekly malaria surveillance data',
    lastSync: '5 hours ago',
    entryCount: 8
  },
  {
    id: '3',
    name: 'Maternal Health Services',
    description: 'Antenatal and postnatal care reporting',
    lastSync: 'Yesterday',
    entryCount: 15
  },
  {
    id: '4',
    name: 'Immunization Coverage',
    description: 'Routine immunization data collection',
    lastSync: '2 days ago',
    entryCount: 23
  }
];

const mockEventPrograms: Program[] = [
  {
    id: 'e1',
    name: 'Outpatient Consultation',
    description: 'Single visit patient consultations',
    type: 'event',
    lastSync: '1 hour ago',
    count: 45,
    countLabel: 'events'
  },
  {
    id: 'e2',
    name: 'Birth Registration',
    description: 'Newborn birth event registration',
    type: 'event',
    lastSync: '3 hours ago',
    count: 18,
    countLabel: 'events'
  },
  {
    id: 'e3',
    name: 'Death Notification',
    description: 'Community death event reporting',
    type: 'event',
    lastSync: 'Yesterday',
    count: 5,
    countLabel: 'events'
  }
];

const mockTrackerPrograms: Program[] = [
  {
    id: 't1',
    name: 'Antenatal Care',
    description: 'Track pregnant women through ANC visits',
    type: 'tracker',
    lastSync: '2 hours ago',
    count: 34,
    countLabel: 'patients'
  },
  {
    id: 't2',
    name: 'TB Treatment',
    description: 'Tuberculosis patient treatment tracking',
    type: 'tracker',
    lastSync: '4 hours ago',
    count: 12,
    countLabel: 'patients'
  },
  {
    id: 't3',
    name: 'Child Growth Monitoring',
    description: 'Track children under 5 growth indicators',
    type: 'tracker',
    lastSync: 'Yesterday',
    count: 56,
    countLabel: 'children'
  },
  {
    id: 't4',
    name: 'HIV Care & Treatment',
    description: 'HIV+ patient care and ART monitoring',
    type: 'tracker',
    lastSync: '2 days ago',
    count: 28,
    countLabel: 'patients'
  }
];

export function DatasetSelection({ onSelectDataset, onSelectProgram, isOffline, onSync, onNavigate, onLogout, onDeleteAccount }: DatasetSelectionProps) {
  const [showLogoutDialog, setShowLogoutDialog] = useState(false);
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [activeTab, setActiveTab] = useState('datasets');

  const handleLogout = () => {
    setShowLogoutDialog(false);
    setDrawerOpen(false);
    onLogout();
  };

  const handleDeleteAccount = () => {
    setShowDeleteDialog(false);
    setDrawerOpen(false);
    onDeleteAccount();
  };

  const handleNavigate = (screen: 'settings' | 'about' | 'report') => {
    setDrawerOpen(false);
    onNavigate(screen);
  };

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      {/* Status Bar */}
      <div className="h-6 bg-green-700"></div>
      
      {/* App Bar */}
      <div className="bg-green-600 text-white shadow-md">
        <div className="flex items-center justify-between px-4 py-3">
          <div className="flex items-center gap-3">
            <Sheet open={drawerOpen} onOpenChange={setDrawerOpen}>
              <SheetTrigger asChild>
                <Button variant="ghost" size="icon" className="text-white hover:bg-green-700">
                  <Menu className="w-6 h-6" />
                </Button>
              </SheetTrigger>
              <SheetContent side="left" className="w-[280px] p-0">
                <SheetHeader className="bg-green-600 text-white p-4">
                  <SheetTitle className="text-white text-left">Menu</SheetTitle>
                  <p className="text-xs text-green-100 text-left">john.doe@dhis2.org</p>
                </SheetHeader>
                
                <div className="py-4">
                  <div className="space-y-1 px-2">
                    <button
                      onClick={() => handleNavigate('settings')}
                      className="w-full flex items-center gap-3 px-3 py-2.5 rounded-md hover:bg-gray-100 active:bg-gray-200 text-left"
                    >
                      <Settings className="w-5 h-5 text-gray-600" />
                      <span className="text-sm">Settings</span>
                    </button>
                    
                    <button
                      onClick={() => handleNavigate('about')}
                      className="w-full flex items-center gap-3 px-3 py-2.5 rounded-md hover:bg-gray-100 active:bg-gray-200 text-left"
                    >
                      <Info className="w-5 h-5 text-gray-600" />
                      <span className="text-sm">About</span>
                    </button>
                    
                    <button
                      onClick={() => handleNavigate('report')}
                      className="w-full flex items-center gap-3 px-3 py-2.5 rounded-md hover:bg-gray-100 active:bg-gray-200 text-left"
                    >
                      <AlertCircle className="w-5 h-5 text-gray-600" />
                      <span className="text-sm">Report an Issue</span>
                    </button>
                  </div>
                  
                  <Separator className="my-4" />
                  
                  <div className="space-y-1 px-2">
                    <button
                      onClick={() => setShowLogoutDialog(true)}
                      className="w-full flex items-center gap-3 px-3 py-2.5 rounded-md hover:bg-gray-100 active:bg-gray-200 text-left"
                    >
                      <LogOut className="w-5 h-5 text-gray-600" />
                      <span className="text-sm">Logout</span>
                    </button>
                    
                    <button
                      onClick={() => setShowDeleteDialog(true)}
                      className="w-full flex items-center gap-3 px-3 py-2.5 rounded-md hover:bg-red-50 active:bg-red-100 text-left"
                    >
                      <UserX className="w-5 h-5 text-red-600" />
                      <span className="text-sm text-red-600">Delete Account</span>
                    </button>
                  </div>
                </div>
                
                <div className="absolute bottom-4 left-4 right-4">
                  <p className="text-xs text-gray-500 text-center">
                    Version 1.0.0 • Build 100
                  </p>
                </div>
              </SheetContent>
            </Sheet>
            <div>
              <h1 className="text-lg">Simple Data Entry</h1>
              <p className="text-xs text-green-100">
                {activeTab === 'datasets' ? 'Datasets' : activeTab === 'events' ? 'Event Programs' : 'Tracker Programs'}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            {isOffline ? (
              <WifiOff className="w-5 h-5 text-red-300" />
            ) : (
              <Wifi className="w-5 h-5 text-green-200" />
            )}
            <Button 
              variant="ghost" 
              size="icon" 
              className="text-white hover:bg-green-700"
              onClick={onSync}
            >
              <RefreshCw className="w-5 h-5" />
            </Button>
          </div>
        </div>
        
        {isOffline && (
          <div className="bg-orange-500 px-4 py-2 text-sm">
            Offline mode • Changes will sync when online
          </div>
        )}
      </div>
      
      {/* Tabs and Content */}
      <Tabs value={activeTab} onValueChange={setActiveTab} className="flex-1 flex flex-col">
        <TabsList className="w-full rounded-none border-b bg-white h-12 grid grid-cols-3">
          <TabsTrigger value="datasets" className="gap-2 data-[state=active]:border-b-2 data-[state=active]:border-green-600">
            <Database className="w-4 h-4" />
            <span className="text-xs">Datasets</span>
          </TabsTrigger>
          <TabsTrigger value="events" className="gap-2 data-[state=active]:border-b-2 data-[state=active]:border-green-600">
            <Activity className="w-4 h-4" />
            <span className="text-xs">Events</span>
          </TabsTrigger>
          <TabsTrigger value="tracker" className="gap-2 data-[state=active]:border-b-2 data-[state=active]:border-green-600">
            <Users className="w-4 h-4" />
            <span className="text-xs">Tracker</span>
          </TabsTrigger>
        </TabsList>
        
        <TabsContent value="datasets" className="flex-1 overflow-y-auto mt-0">
          <div className="p-4 space-y-3">
            {mockDatasets.map((dataset) => (
              <div
                key={dataset.id}
                onClick={() => onSelectDataset(dataset.id)}
                className="bg-white rounded-lg shadow-sm border border-gray-200 p-4 active:bg-gray-50 cursor-pointer"
              >
                <div className="flex items-start justify-between mb-2">
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-1">
                      <Database className="w-4 h-4 text-blue-600 flex-shrink-0" />
                      <h3 className="text-base">{dataset.name}</h3>
                    </div>
                    <p className="text-sm text-gray-600">{dataset.description}</p>
                  </div>
                  <ChevronRight className="w-5 h-5 text-gray-400 flex-shrink-0 mt-1" />
                </div>
                
                <div className="flex items-center justify-between mt-3">
                  <div className="text-xs text-gray-500">
                    Synced {dataset.lastSync}
                  </div>
                  <Badge variant="secondary" className="bg-blue-100 text-blue-700">
                    {dataset.entryCount} {dataset.entryCount !== 1 ? 'entries' : 'entry'}
                  </Badge>
                </div>
              </div>
            ))}
          </div>
        </TabsContent>
        
        <TabsContent value="events" className="flex-1 overflow-y-auto mt-0">
          <div className="p-4 space-y-3">
            {mockEventPrograms.map((program) => (
              <div
                key={program.id}
                onClick={() => onSelectProgram(program.id, 'event')}
                className="bg-white rounded-lg shadow-sm border border-gray-200 p-4 active:bg-gray-50 cursor-pointer"
              >
                <div className="flex items-start justify-between mb-2">
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-1">
                      <Activity className="w-4 h-4 text-orange-600 flex-shrink-0" />
                      <h3 className="text-base">{program.name}</h3>
                    </div>
                    <p className="text-sm text-gray-600">{program.description}</p>
                  </div>
                  <ChevronRight className="w-5 h-5 text-gray-400 flex-shrink-0 mt-1" />
                </div>
                
                <div className="flex items-center justify-between mt-3">
                  <div className="text-xs text-gray-500">
                    Synced {program.lastSync}
                  </div>
                  <Badge variant="secondary" className="bg-orange-100 text-orange-700">
                    {program.count} {program.countLabel}
                  </Badge>
                </div>
              </div>
            ))}
          </div>
        </TabsContent>
        
        <TabsContent value="tracker" className="flex-1 overflow-y-auto mt-0">
          <div className="p-4 space-y-3">
            {mockTrackerPrograms.map((program) => (
              <div
                key={program.id}
                onClick={() => onSelectProgram(program.id, 'tracker')}
                className="bg-white rounded-lg shadow-sm border border-gray-200 p-4 active:bg-gray-50 cursor-pointer"
              >
                <div className="flex items-start justify-between mb-2">
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-1">
                      <Users className="w-4 h-4 text-purple-600 flex-shrink-0" />
                      <h3 className="text-base">{program.name}</h3>
                    </div>
                    <p className="text-sm text-gray-600">{program.description}</p>
                  </div>
                  <ChevronRight className="w-5 h-5 text-gray-400 flex-shrink-0 mt-1" />
                </div>
                
                <div className="flex items-center justify-between mt-3">
                  <div className="text-xs text-gray-500">
                    Synced {program.lastSync}
                  </div>
                  <Badge variant="secondary" className="bg-purple-100 text-purple-700">
                    {program.count} {program.countLabel}
                  </Badge>
                </div>
              </div>
            ))}
          </div>
        </TabsContent>
      </Tabs>
      
      {/* Logout Dialog */}
      <AlertDialog open={showLogoutDialog} onOpenChange={setShowLogoutDialog}>
        <AlertDialogContent className="max-w-sm">
          <AlertDialogHeader>
            <AlertDialogTitle>Logout</AlertDialogTitle>
            <AlertDialogDescription>
              Are you sure you want to logout? Make sure all your data is synced before logging out.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={handleLogout} className="bg-green-600 hover:bg-green-700">
              Logout
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
      
      {/* Delete Account Dialog */}
      <AlertDialog open={showDeleteDialog} onOpenChange={setShowDeleteDialog}>
        <AlertDialogContent className="max-w-sm">
          <AlertDialogHeader>
            <AlertDialogTitle>Delete Account</AlertDialogTitle>
            <AlertDialogDescription>
              This action cannot be undone. This will permanently delete your account and remove all your data from our servers.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction 
              onClick={handleDeleteAccount}
              className="bg-red-600 hover:bg-red-700"
            >
              Delete Account
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}