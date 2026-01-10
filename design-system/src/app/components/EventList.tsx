import { ArrowLeft, Plus, Search, Filter, Activity, Calendar, MapPin, CheckCircle, Clock, Upload } from 'lucide-react';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Badge } from './ui/badge';
import { useState } from 'react';

interface Event {
  id: string;
  eventDate: string;
  orgUnit: string;
  status: 'Draft' | 'Completed' | 'Synced';
  dataValues: Record<string, string>;
}

interface EventListProps {
  programName: string;
  onBack: () => void;
  onSelectEvent: (eventId: string) => void;
  onCreateNew: () => void;
  isOffline: boolean;
}

const mockEvents: Event[] = [
  {
    id: '1',
    eventDate: '2026-01-08',
    orgUnit: 'District Hospital',
    status: 'Completed',
    dataValues: { diagnosis: 'Malaria', patientAge: '25' }
  },
  {
    id: '2',
    eventDate: '2026-01-07',
    orgUnit: 'Health Center A',
    status: 'Synced',
    dataValues: { diagnosis: 'Flu', patientAge: '42' }
  },
  {
    id: '3',
    eventDate: '2026-01-07',
    orgUnit: 'District Hospital',
    status: 'Draft',
    dataValues: { diagnosis: 'Tuberculosis', patientAge: '34' }
  },
  {
    id: '4',
    eventDate: '2026-01-06',
    orgUnit: 'Health Center B',
    status: 'Synced',
    dataValues: { diagnosis: 'Hypertension', patientAge: '58' }
  },
  {
    id: '5',
    eventDate: '2026-01-06',
    orgUnit: 'Health Center A',
    status: 'Completed',
    dataValues: { diagnosis: 'Diabetes', patientAge: '51' }
  }
];

export function EventList({ programName, onBack, onSelectEvent, onCreateNew, isOffline }: EventListProps) {
  const [searchQuery, setSearchQuery] = useState('');
  const [showFilter, setShowFilter] = useState(false);

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

  const filteredEvents = mockEvents.filter(event => {
    const matchesSearch = 
      event.orgUnit.toLowerCase().includes(searchQuery.toLowerCase()) ||
      event.eventDate.includes(searchQuery) ||
      Object.values(event.dataValues).some(val => 
        val.toLowerCase().includes(searchQuery.toLowerCase())
      );
    return matchesSearch;
  });

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
          <div className="flex-1">
            <div className="flex items-center gap-2">
              <Activity className="w-5 h-5" />
              <h1 className="text-lg">{programName}</h1>
            </div>
            <p className="text-xs text-green-100">{filteredEvents.length} events</p>
          </div>
          <Button
            variant="ghost"
            size="icon"
            className="text-white hover:bg-green-700"
            onClick={() => setShowFilter(!showFilter)}
          >
            <Filter className="w-5 h-5" />
          </Button>
        </div>
        
        {/* Search Bar */}
        <div className="px-4 pb-3">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-4 h-4 text-gray-400" />
            <Input
              type="text"
              placeholder="Search events..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-9 bg-white/90 border-0 h-10"
            />
          </div>
        </div>
      </div>
      
      {/* Event List */}
      <div className="flex-1 overflow-y-auto pb-20">
        <div className="p-4 space-y-3">
          {filteredEvents.map((event) => (
            <div
              key={event.id}
              onClick={() => onSelectEvent(event.id)}
              className="bg-white rounded-lg shadow-sm border border-gray-200 p-4 active:bg-gray-50 cursor-pointer"
            >
              <div className="flex items-start justify-between mb-3">
                <div className="flex items-center gap-2">
                  {getStatusIcon(event.status)}
                  <span className="text-sm font-medium">
                    {new Date(event.eventDate).toLocaleDateString('en-US', { 
                      month: 'short', 
                      day: 'numeric',
                      year: 'numeric'
                    })}
                  </span>
                </div>
                {getStatusBadge(event.status)}
              </div>
              
              <div className="space-y-2">
                <div className="flex items-center gap-2 text-sm text-gray-600">
                  <MapPin className="w-3 h-3" />
                  <span>{event.orgUnit}</span>
                </div>
                
                {event.dataValues.diagnosis && (
                  <div className="text-sm">
                    <span className="text-gray-500">Diagnosis:</span>{' '}
                    <span className="font-medium">{event.dataValues.diagnosis}</span>
                  </div>
                )}
                
                {event.dataValues.patientAge && (
                  <div className="text-sm text-gray-600">
                    Age: {event.dataValues.patientAge} years
                  </div>
                )}
              </div>
            </div>
          ))}
          
          {filteredEvents.length === 0 && (
            <div className="text-center py-12">
              <Activity className="w-12 h-12 text-gray-300 mx-auto mb-3" />
              <h3 className="text-gray-600 mb-1">No events found</h3>
              <p className="text-sm text-gray-500">
                {searchQuery ? 'Try adjusting your search' : 'Create your first event'}
              </p>
            </div>
          )}
        </div>
      </div>
      
      {/* FAB */}
      <div className="fixed bottom-6 right-6">
        <Button
          onClick={onCreateNew}
          className="h-14 w-14 rounded-full shadow-lg bg-orange-600 hover:bg-orange-700"
        >
          <Plus className="w-6 h-6" />
        </Button>
      </div>
    </div>
  );
}
