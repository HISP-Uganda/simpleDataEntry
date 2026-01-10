import { ArrowLeft, Plus, Search, Filter, Users, User, Calendar, MapPin, Phone, Hash } from 'lucide-react';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Badge } from './ui/badge';
import { useState } from 'react';

interface TrackedEntity {
  id: string;
  attributes: Record<string, string>;
  enrollmentDate: string;
  orgUnit: string;
  eventsCount: number;
  lastUpdated: string;
}

interface TrackedEntityListProps {
  programName: string;
  onBack: () => void;
  onSelectEntity: (entityId: string) => void;
  onCreateNew: () => void;
  isOffline: boolean;
}

const mockEntities: TrackedEntity[] = [
  {
    id: '1',
    attributes: {
      firstName: 'Sarah',
      lastName: 'Johnson',
      phone: '+1234567890',
      nationalId: 'ANC-2026-001'
    },
    enrollmentDate: '2026-01-05',
    orgUnit: 'District Hospital',
    eventsCount: 3,
    lastUpdated: '2 hours ago'
  },
  {
    id: '2',
    attributes: {
      firstName: 'Mary',
      lastName: 'Williams',
      phone: '+1234567891',
      nationalId: 'ANC-2026-002'
    },
    enrollmentDate: '2026-01-03',
    orgUnit: 'Health Center A',
    eventsCount: 2,
    lastUpdated: '5 hours ago'
  },
  {
    id: '3',
    attributes: {
      firstName: 'Emma',
      lastName: 'Davis',
      phone: '+1234567892',
      nationalId: 'ANC-2025-125'
    },
    enrollmentDate: '2025-12-28',
    orgUnit: 'District Hospital',
    eventsCount: 5,
    lastUpdated: '1 day ago'
  },
  {
    id: '4',
    attributes: {
      firstName: 'Lisa',
      lastName: 'Brown',
      phone: '+1234567893',
      nationalId: 'ANC-2025-118'
    },
    enrollmentDate: '2025-12-20',
    orgUnit: 'Health Center B',
    eventsCount: 4,
    lastUpdated: '2 days ago'
  }
];

export function TrackedEntityList({ programName, onBack, onSelectEntity, onCreateNew, isOffline }: TrackedEntityListProps) {
  const [searchQuery, setSearchQuery] = useState('');
  const [showFilter, setShowFilter] = useState(false);

  const filteredEntities = mockEntities.filter(entity => {
    const matchesSearch = 
      entity.attributes.firstName?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      entity.attributes.lastName?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      entity.attributes.nationalId?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      entity.attributes.phone?.includes(searchQuery) ||
      entity.orgUnit.toLowerCase().includes(searchQuery.toLowerCase());
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
              <Users className="w-5 h-5" />
              <h1 className="text-lg">{programName}</h1>
            </div>
            <p className="text-xs text-green-100">{filteredEntities.length} enrolled</p>
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
              placeholder="Search by name, ID, or phone..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-9 bg-white/90 border-0 h-10"
            />
          </div>
        </div>
      </div>
      
      {/* Entity List */}
      <div className="flex-1 overflow-y-auto pb-20">
        <div className="p-4 space-y-3">
          {filteredEntities.map((entity) => (
            <div
              key={entity.id}
              onClick={() => onSelectEntity(entity.id)}
              className="bg-white rounded-lg shadow-sm border border-gray-200 p-4 active:bg-gray-50 cursor-pointer"
            >
              <div className="flex items-start gap-3 mb-3">
                <div className="w-10 h-10 bg-purple-100 rounded-full flex items-center justify-center flex-shrink-0">
                  <User className="w-5 h-5 text-purple-600" />
                </div>
                <div className="flex-1 min-w-0">
                  <h3 className="font-medium mb-1">
                    {entity.attributes.firstName} {entity.attributes.lastName}
                  </h3>
                  <div className="space-y-1">
                    {entity.attributes.nationalId && (
                      <div className="flex items-center gap-2 text-xs text-gray-600">
                        <Hash className="w-3 h-3" />
                        <span>{entity.attributes.nationalId}</span>
                      </div>
                    )}
                    {entity.attributes.phone && (
                      <div className="flex items-center gap-2 text-xs text-gray-600">
                        <Phone className="w-3 h-3" />
                        <span>{entity.attributes.phone}</span>
                      </div>
                    )}
                  </div>
                </div>
                <Badge variant="secondary" className="bg-purple-100 text-purple-700 text-xs">
                  {entity.eventsCount} {entity.eventsCount === 1 ? 'visit' : 'visits'}
                </Badge>
              </div>
              
              <div className="flex items-center justify-between pt-2 border-t border-gray-100">
                <div className="flex items-center gap-2 text-xs text-gray-500">
                  <MapPin className="w-3 h-3" />
                  <span>{entity.orgUnit}</span>
                </div>
                <div className="flex items-center gap-2 text-xs text-gray-500">
                  <Calendar className="w-3 h-3" />
                  <span>Enrolled {new Date(entity.enrollmentDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}</span>
                </div>
              </div>
            </div>
          ))}
          
          {filteredEntities.length === 0 && (
            <div className="text-center py-12">
              <Users className="w-12 h-12 text-gray-300 mx-auto mb-3" />
              <h3 className="text-gray-600 mb-1">No patients found</h3>
              <p className="text-sm text-gray-500">
                {searchQuery ? 'Try adjusting your search' : 'Register your first patient'}
              </p>
            </div>
          )}
        </div>
      </div>
      
      {/* FAB */}
      <div className="fixed bottom-6 right-6">
        <Button
          onClick={onCreateNew}
          className="h-14 w-14 rounded-full shadow-lg bg-purple-600 hover:bg-purple-700"
        >
          <Plus className="w-6 h-6" />
        </Button>
      </div>
    </div>
  );
}
