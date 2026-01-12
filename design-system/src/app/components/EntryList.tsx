import { ArrowLeft, Plus, Search, Calendar, Edit, WifiOff, Wifi, Filter, CheckSquare } from 'lucide-react';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Badge } from './ui/badge';
import { Checkbox } from './ui/checkbox';
import { useState } from 'react';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from './ui/dropdown-menu';

interface Entry {
  id: string;
  title: string;
  period: string;
  orgUnit: string;
  lastModified: string;
  status: 'draft' | 'completed' | 'synced' | 'completed_synced';
  completionPercent: number;
}

interface EntryListProps {
  datasetName: string;
  onBack: () => void;
  onSelectEntry: (entryId: string) => void;
  onCreateNew: () => void;
  isOffline: boolean;
}

const mockEntries: Entry[] = [
  {
    id: '1',
    title: 'January 2026 - District Hospital',
    period: 'January 2026',
    orgUnit: 'District Hospital',
    lastModified: '1 hour ago',
    status: 'draft',
    completionPercent: 65
  },
  {
    id: '2',
    title: 'December 2025 - District Hospital',
    period: 'December 2025',
    orgUnit: 'District Hospital',
    lastModified: '2 days ago',
    status: 'completed_synced',
    completionPercent: 100
  },
  {
    id: '3',
    title: 'January 2026 - Health Center A',
    period: 'January 2026',
    orgUnit: 'Health Center A',
    lastModified: '3 hours ago',
    status: 'draft',
    completionPercent: 30
  },
  {
    id: '4',
    title: 'December 2025 - Health Center A',
    period: 'December 2025',
    orgUnit: 'Health Center A',
    lastModified: '1 week ago',
    status: 'completed',
    completionPercent: 100
  },
  {
    id: '5',
    title: 'November 2025 - District Hospital',
    period: 'November 2025',
    orgUnit: 'District Hospital',
    lastModified: '3 weeks ago',
    status: 'synced',
    completionPercent: 100
  },
  {
    id: '6',
    title: 'January 2026 - Health Center B',
    period: 'January 2026',
    orgUnit: 'Health Center B',
    lastModified: '5 hours ago',
    status: 'draft',
    completionPercent: 80
  }
];

export function EntryList({ datasetName, onBack, onSelectEntry, onCreateNew, isOffline }: EntryListProps) {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedEntries, setSelectedEntries] = useState<Set<string>>(new Set());
  const [filterStatus, setFilterStatus] = useState<string>('all');
  const [bulkMode, setBulkMode] = useState(false);

  const getStatusColor = (status: Entry['status']) => {
    switch (status) {
      case 'draft':
        return 'bg-orange-100 text-orange-700';
      case 'completed':
        return 'bg-green-100 text-green-700';
      case 'synced':
        return 'bg-blue-100 text-blue-700';
      case 'completed_synced':
        return 'bg-emerald-100 text-emerald-700';
    }
  };

  const getStatusText = (status: Entry['status']) => {
    switch (status) {
      case 'draft':
        return 'Draft';
      case 'completed':
        return 'Completed';
      case 'synced':
        return 'Synced';
      case 'completed_synced':
        return 'Completed & Synced';
    }
  };

  const filteredEntries = mockEntries.filter(entry => {
    const matchesSearch = entry.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
                          entry.orgUnit.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesFilter = filterStatus === 'all' || entry.status === filterStatus;
    return matchesSearch && matchesFilter;
  });

  const toggleEntrySelection = (entryId: string) => {
    const newSelection = new Set(selectedEntries);
    if (newSelection.has(entryId)) {
      newSelection.delete(entryId);
    } else {
      newSelection.add(entryId);
    }
    setSelectedEntries(newSelection);
  };

  const toggleSelectAll = () => {
    if (selectedEntries.size === filteredEntries.length) {
      setSelectedEntries(new Set());
    } else {
      setSelectedEntries(new Set(filteredEntries.map(e => e.id)));
    }
  };

  const handleBulkComplete = () => {
    // Handle bulk completion
    setSelectedEntries(new Set());
    setBulkMode(false);
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
          <div className="flex-1">
            <h1 className="text-lg">{datasetName}</h1>
            <p className="text-xs text-green-100">Data Entries</p>
          </div>
          {isOffline ? (
            <WifiOff className="w-5 h-5 text-red-300" />
          ) : (
            <Wifi className="w-5 h-5 text-green-200" />
          )}
        </div>
      </div>
      
      {/* Search and Filter Bar */}
      <div className="bg-white border-b border-gray-200 p-4 space-y-3">
        <div className="flex gap-2">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-5 h-5 text-gray-400" />
            <Input
              type="text"
              placeholder="Search entries..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-10 h-11"
            />
          </div>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" size="icon" className="h-11 w-11 flex-shrink-0">
                <Filter className="w-5 h-5" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuLabel>Filter by Status</DropdownMenuLabel>
              <DropdownMenuSeparator />
              <DropdownMenuItem onClick={() => setFilterStatus('all')}>
                All Entries
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => setFilterStatus('draft')}>
                Draft Only
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => setFilterStatus('completed')}>
                Completed Only
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => setFilterStatus('synced')}>
                Synced Only
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => setFilterStatus('completed_synced')}>
                Completed & Synced
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
          <Button 
            variant={bulkMode ? "default" : "outline"} 
            size="icon" 
            className="h-11 w-11 flex-shrink-0"
            onClick={() => {
              setBulkMode(!bulkMode);
              setSelectedEntries(new Set());
            }}
          >
            <CheckSquare className="w-5 h-5" />
          </Button>
        </div>
        
        {bulkMode && (
          <div className="flex items-center justify-between bg-blue-50 border border-blue-200 rounded-md p-3">
            <div className="flex items-center gap-3">
              <Checkbox 
                checked={selectedEntries.size === filteredEntries.length && filteredEntries.length > 0}
                onCheckedChange={toggleSelectAll}
              />
              <span className="text-sm">
                {selectedEntries.size} selected
              </span>
            </div>
            {selectedEntries.size > 0 && (
              <Button 
                size="sm" 
                className="bg-green-600 hover:bg-green-700"
                onClick={handleBulkComplete}
              >
                Complete Selected
              </Button>
            )}
          </div>
        )}
      </div>
      
      {/* Entry List */}
      <div className="flex-1 overflow-y-auto pb-20">
        <div className="p-4 space-y-3">
          {filteredEntries.map((entry) => (
            <div
              key={entry.id}
              onClick={() => !bulkMode && onSelectEntry(entry.id)}
              className="bg-white rounded-lg shadow-sm border border-gray-200 p-4 active:bg-gray-50 cursor-pointer"
            >
              <div className="flex items-start gap-3">
                {bulkMode && (
                  <Checkbox 
                    checked={selectedEntries.has(entry.id)}
                    onCheckedChange={() => toggleEntrySelection(entry.id)}
                    onClick={(e) => e.stopPropagation()}
                    className="mt-1"
                  />
                )}
                
                <div className="flex-1 min-w-0">
                  <div className="flex items-start justify-between mb-2">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1">
                        <Calendar className="w-4 h-4 text-gray-500 flex-shrink-0" />
                        <h3 className="text-base truncate">{entry.period}</h3>
                      </div>
                      <p className="text-sm text-gray-600 truncate">{entry.orgUnit}</p>
                    </div>
                    <Badge variant="secondary" className={`${getStatusColor(entry.status)} ml-2 flex-shrink-0`}>
                      {getStatusText(entry.status)}
                    </Badge>
                  </div>
                  
                  {/* Progress Bar */}
                  {entry.status === 'draft' && (
                    <div className="mt-3 mb-2">
                      <div className="flex items-center justify-between text-xs text-gray-600 mb-1">
                        <span>Completion</span>
                        <span>{entry.completionPercent}%</span>
                      </div>
                      <div className="w-full bg-gray-200 rounded-full h-2">
                        <div
                          className="bg-green-600 h-2 rounded-full transition-all"
                          style={{ width: `${entry.completionPercent}%` }}
                        ></div>
                      </div>
                    </div>
                  )}
                  
                  <div className="flex items-center gap-1 text-xs text-gray-500 mt-3">
                    <Edit className="w-3 h-3" />
                    <span>Modified {entry.lastModified}</span>
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
      
      {/* Floating Action Button */}
      {!bulkMode && (
        <div className="fixed bottom-6 right-6">
          <Button
            onClick={onCreateNew}
            size="lg"
            className="w-14 h-14 rounded-full bg-green-600 hover:bg-green-700 shadow-lg"
          >
            <Plus className="w-6 h-6" />
          </Button>
        </div>
      )}
    </div>
  );
}
