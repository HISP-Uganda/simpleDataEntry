import { ArrowLeft, Plus, User, Calendar, MapPin, Phone, Hash, Activity, Edit, Clock, CheckCircle, Upload } from 'lucide-react';
import { Button } from './ui/button';
import { Badge } from './ui/badge';
import { Separator } from './ui/separator';
import { Tabs, TabsContent, TabsList, TabsTrigger } from './ui/tabs';

interface TrackedEntityDetailsProps {
  programName: string;
  entityName: string;
  onBack: () => void;
  onAddEvent: () => void;
  onEditEntity: () => void;
  onSelectEvent: (eventId: string) => void;
  isOffline: boolean;
}

interface Event {
  id: string;
  programStage: string;
  eventDate: string;
  status: 'Draft' | 'Completed' | 'Synced';
  dataValues: Record<string, string>;
}

const mockEntityData = {
  attributes: {
    firstName: 'Sarah',
    lastName: 'Johnson',
    phone: '+1234567890',
    nationalId: 'ANC-2026-001',
    dateOfBirth: '1992-03-15',
    address: '123 Main St, District'
  },
  enrollmentDate: '2026-01-05',
  enrollmentStatus: 'Active',
  orgUnit: 'District Hospital'
};

const mockEvents: Event[] = [
  {
    id: '1',
    programStage: 'First ANC Visit',
    eventDate: '2026-01-08',
    status: 'Completed',
    dataValues: {
      weight: '65 kg',
      bloodPressure: '120/80',
      hemoglobin: '12.5 g/dL'
    }
  },
  {
    id: '2',
    programStage: 'Second ANC Visit',
    eventDate: '2026-01-07',
    status: 'Draft',
    dataValues: {
      weight: '66 kg',
      bloodPressure: '118/78'
    }
  },
  {
    id: '3',
    programStage: 'Initial Registration',
    eventDate: '2026-01-05',
    status: 'Synced',
    dataValues: {
      gestationalAge: '12 weeks',
      expectedDeliveryDate: '2026-07-15'
    }
  }
];

export function TrackedEntityDetails({ 
  programName, 
  entityName, 
  onBack, 
  onAddEvent, 
  onEditEntity,
  onSelectEvent,
  isOffline 
}: TrackedEntityDetailsProps) {
  const getStatusBadge = (status: Event['status']) => {
    switch (status) {
      case 'Draft':
        return <Badge className="bg-yellow-100 text-yellow-800 text-xs">Draft</Badge>;
      case 'Completed':
        return <Badge className="bg-blue-100 text-blue-800 text-xs">Completed</Badge>;
      case 'Synced':
        return <Badge className="bg-green-100 text-green-800 text-xs">Synced</Badge>;
    }
  };

  const getStatusIcon = (status: Event['status']) => {
    switch (status) {
      case 'Draft':
        return <Clock className="w-4 h-4 text-yellow-600" />;
      case 'Completed':
        return <CheckCircle className="w-4 h-4 text-blue-600" />;
      case 'Synced':
        return <Upload className="w-4 h-4 text-green-600" />;
    }
  };

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      {/* Status Bar */}
      <div className="h-6 bg-green-700"></div>
      
      {/* App Bar */}
      <div className="bg-green-600 text-white shadow-md">
        <div className="flex items-center gap-3 px-4 py-3">
          <Button 
            variant="ghost" 
            size="icon" 
            className="text-white hover:bg-green-700"
            onClick={onBack}
          >
            <ArrowLeft className="w-6 h-6" />
          </Button>
          <div className="flex-1 min-w-0">
            <h1 className="text-lg truncate">{entityName}</h1>
            <p className="text-xs text-green-100">{programName}</p>
          </div>
          <Button
            variant="ghost"
            size="icon"
            className="text-white hover:bg-green-700"
            onClick={onEditEntity}
          >
            <Edit className="w-5 h-5" />
          </Button>
        </div>
      </div>
      
      {/* Content */}
      <div className="flex-1 overflow-y-auto pb-20">
        <Tabs defaultValue="profile" className="w-full">
          <TabsList className="w-full rounded-none border-b bg-white h-11 grid grid-cols-2">
            <TabsTrigger value="profile" className="data-[state=active]:border-b-2 data-[state=active]:border-purple-600">
              Profile
            </TabsTrigger>
            <TabsTrigger value="events" className="data-[state=active]:border-b-2 data-[state=active]:border-purple-600">
              Events ({mockEvents.length})
            </TabsTrigger>
          </TabsList>
          
          <TabsContent value="profile" className="mt-0">
            <div className="p-4 space-y-4">
              {/* Profile Card */}
              <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
                <div className="flex items-start gap-3 mb-4">
                  <div className="w-16 h-16 bg-purple-100 rounded-full flex items-center justify-center flex-shrink-0">
                    <User className="w-8 h-8 text-purple-600" />
                  </div>
                  <div className="flex-1">
                    <h2 className="text-xl font-medium mb-1">
                      {mockEntityData.attributes.firstName} {mockEntityData.attributes.lastName}
                    </h2>
                    <Badge className="bg-green-100 text-green-800">
                      {mockEntityData.enrollmentStatus}
                    </Badge>
                  </div>
                </div>
                
                <Separator className="my-4" />
                
                <div className="space-y-3">
                  {mockEntityData.attributes.nationalId && (
                    <div className="flex items-center gap-3">
                      <Hash className="w-4 h-4 text-gray-500" />
                      <div className="flex-1">
                        <p className="text-xs text-gray-500">National ID</p>
                        <p className="text-sm">{mockEntityData.attributes.nationalId}</p>
                      </div>
                    </div>
                  )}
                  
                  {mockEntityData.attributes.phone && (
                    <div className="flex items-center gap-3">
                      <Phone className="w-4 h-4 text-gray-500" />
                      <div className="flex-1">
                        <p className="text-xs text-gray-500">Phone</p>
                        <p className="text-sm">{mockEntityData.attributes.phone}</p>
                      </div>
                    </div>
                  )}
                  
                  {mockEntityData.attributes.dateOfBirth && (
                    <div className="flex items-center gap-3">
                      <Calendar className="w-4 h-4 text-gray-500" />
                      <div className="flex-1">
                        <p className="text-xs text-gray-500">Date of Birth</p>
                        <p className="text-sm">
                          {new Date(mockEntityData.attributes.dateOfBirth).toLocaleDateString('en-US', {
                            year: 'numeric',
                            month: 'long',
                            day: 'numeric'
                          })}
                          {' '}({new Date().getFullYear() - new Date(mockEntityData.attributes.dateOfBirth).getFullYear()} years)
                        </p>
                      </div>
                    </div>
                  )}
                  
                  {mockEntityData.attributes.address && (
                    <div className="flex items-center gap-3">
                      <MapPin className="w-4 h-4 text-gray-500" />
                      <div className="flex-1">
                        <p className="text-xs text-gray-500">Address</p>
                        <p className="text-sm">{mockEntityData.attributes.address}</p>
                      </div>
                    </div>
                  )}
                </div>
              </div>
              
              {/* Enrollment Info */}
              <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
                <h3 className="font-medium mb-3">Enrollment Information</h3>
                <div className="space-y-3">
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-gray-600">Enrollment Date</span>
                    <span className="font-medium">
                      {new Date(mockEntityData.enrollmentDate).toLocaleDateString('en-US', {
                        year: 'numeric',
                        month: 'short',
                        day: 'numeric'
                      })}
                    </span>
                  </div>
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-gray-600">Organization Unit</span>
                    <span className="font-medium">{mockEntityData.orgUnit}</span>
                  </div>
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-gray-600">Total Events</span>
                    <span className="font-medium">{mockEvents.length}</span>
                  </div>
                </div>
              </div>
            </div>
          </TabsContent>
          
          <TabsContent value="events" className="mt-0">
            <div className="p-4 space-y-3">
              {mockEvents.map((event) => (
                <div
                  key={event.id}
                  onClick={() => onSelectEvent(event.id)}
                  className="bg-white rounded-lg shadow-sm border border-gray-200 p-4 active:bg-gray-50 cursor-pointer"
                >
                  <div className="flex items-start justify-between mb-3">
                    <div className="flex items-center gap-2">
                      {getStatusIcon(event.status)}
                      <div>
                        <h3 className="font-medium text-sm">{event.programStage}</h3>
                        <p className="text-xs text-gray-500">
                          {new Date(event.eventDate).toLocaleDateString('en-US', {
                            month: 'short',
                            day: 'numeric',
                            year: 'numeric'
                          })}
                        </p>
                      </div>
                    </div>
                    {getStatusBadge(event.status)}
                  </div>
                  
                  {Object.keys(event.dataValues).length > 0 && (
                    <div className="space-y-1 pt-2 border-t border-gray-100">
                      {Object.entries(event.dataValues).slice(0, 2).map(([key, value]) => (
                        <div key={key} className="text-xs text-gray-600">
                          <span className="capitalize">{key.replace(/([A-Z])/g, ' $1').trim()}:</span>{' '}
                          <span className="font-medium">{value}</span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </TabsContent>
        </Tabs>
      </div>
      
      {/* FAB */}
      <div className="fixed bottom-6 right-6">
        <Button
          onClick={onAddEvent}
          className="h-14 w-14 rounded-full shadow-lg bg-purple-600 hover:bg-purple-700"
        >
          <Plus className="w-6 h-6" />
        </Button>
      </div>
    </div>
  );
}
