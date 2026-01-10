import { useState } from 'react';
import { ChevronDown, UserCircle, Plus, Lock } from 'lucide-react';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from './ui/select';
import { Button } from './ui/button';

interface Profile {
  id: string;
  username: string;
  serverUrl: string;
  displayName: string;
  lastSync: string;
}

interface ProfileSelectorProps {
  onSelectProfile: (profileId: string) => void;
  onAddNew: () => void;
}

const mockProfiles: Profile[] = [
  {
    id: '1',
    username: 'john.doe@dhis2.org',
    serverUrl: 'play.dhis2.org',
    displayName: 'John Doe - Play Server',
    lastSync: '2 hours ago'
  },
  {
    id: '2',
    username: 'jane.smith@health.gov',
    serverUrl: 'data.health.gov',
    displayName: 'Jane Smith - Health Dept',
    lastSync: 'Yesterday'
  }
];

export function ProfileSelector({ onSelectProfile, onAddNew }: ProfileSelectorProps) {
  const [selectedProfile, setSelectedProfile] = useState<string>(mockProfiles[0].id);

  const handleProfileChange = (profileId: string) => {
    if (profileId === 'new') {
      onAddNew();
    } else {
      setSelectedProfile(profileId);
      onSelectProfile(profileId);
    }
  };

  const currentProfile = mockProfiles.find(p => p.id === selectedProfile);

  return (
    <div className="w-full">
      <Select value={selectedProfile} onValueChange={handleProfileChange}>
        <SelectTrigger className="h-14 bg-white border-2 border-green-200">
          <div className="flex items-center gap-3 w-full">
            <UserCircle className="w-8 h-8 text-green-600 flex-shrink-0" />
            <div className="flex-1 text-left min-w-0">
              <div className="text-sm truncate">{currentProfile?.displayName}</div>
              <div className="text-xs text-gray-500 truncate">{currentProfile?.serverUrl}</div>
            </div>
          </div>
        </SelectTrigger>
        <SelectContent>
          {mockProfiles.map((profile) => (
            <SelectItem key={profile.id} value={profile.id}>
              <div className="flex items-center gap-3 py-1">
                <UserCircle className="w-6 h-6 text-green-600 flex-shrink-0" />
                <div className="flex-1 min-w-0">
                  <div className="text-sm truncate">{profile.displayName}</div>
                  <div className="text-xs text-gray-500 truncate">{profile.serverUrl}</div>
                  <div className="flex items-center gap-1 text-xs text-gray-400 mt-0.5">
                    <Lock className="w-3 h-3" />
                    <span>Synced {profile.lastSync}</span>
                  </div>
                </div>
              </div>
            </SelectItem>
          ))}
          <SelectItem value="new">
            <div className="flex items-center gap-3 py-1 text-green-600">
              <Plus className="w-6 h-6" />
              <span>Add New Profile</span>
            </div>
          </SelectItem>
        </SelectContent>
      </Select>
    </div>
  );
}
