import { useState } from 'react';
import { Input } from './ui/input';
import { Button } from './ui/button';
import { Label } from './ui/label';
import { Database, Wifi } from 'lucide-react';
import { ProfileSelector } from './ProfileSelector';

interface LoginScreenProps {
  onLogin: () => void;
}

export function LoginScreen({ onLogin }: LoginScreenProps) {
  const [serverUrl, setServerUrl] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [showNewProfile, setShowNewProfile] = useState(false);

  const handleLogin = () => {
    setIsLoading(true);
    // Simulate login
    setTimeout(() => {
      setIsLoading(false);
      onLogin();
    }, 1500);
  };

  const handleSelectProfile = (profileId: string) => {
    // Load profile and go to app
    onLogin();
  };

  const handleAddNewProfile = () => {
    setShowNewProfile(true);
  };

  return (
    <div className="min-h-screen bg-gradient-to-b from-green-600 to-green-700 flex flex-col">
      {/* Status Bar */}
      <div className="h-6 bg-green-800"></div>
      
      {/* Main Content */}
      <div className="flex-1 flex flex-col items-center justify-center px-6 pb-12">
        <div className="w-full max-w-md">
          {/* Logo */}
          <div className="flex justify-center mb-8">
            <div className="w-24 h-24 bg-white rounded-full flex items-center justify-center shadow-lg">
              <Database className="w-12 h-12 text-green-600" />
            </div>
          </div>
          
          {/* Title */}
          <h1 className="text-white text-3xl text-center mb-2">Simple Data Entry</h1>
          <p className="text-green-100 text-center mb-8">DHIS2 Data Collection</p>
          
          {/* Login Form */}
          <div className="bg-white rounded-lg shadow-xl p-6 space-y-4">
            {!showNewProfile ? (
              <>
                <div className="space-y-2">
                  <Label className="text-sm text-gray-600">Saved Profiles</Label>
                  <ProfileSelector 
                    onSelectProfile={handleSelectProfile}
                    onAddNew={handleAddNewProfile}
                  />
                </div>
                
                <div className="relative">
                  <div className="absolute inset-0 flex items-center">
                    <span className="w-full border-t border-gray-200" />
                  </div>
                  <div className="relative flex justify-center text-xs">
                    <span className="bg-white px-2 text-gray-500">or login with new profile</span>
                  </div>
                </div>
                
                <Button 
                  onClick={handleAddNewProfile}
                  variant="outline"
                  className="w-full h-11"
                >
                  Login with New Credentials
                </Button>
              </>
            ) : (
              <>
                <div className="space-y-2">
                  <Label htmlFor="server">Server URL</Label>
                  <Input
                    id="server"
                    type="text"
                    placeholder="https://play.dhis2.org/2.40.0"
                    value={serverUrl}
                    onChange={(e) => setServerUrl(e.target.value)}
                    className="h-12"
                  />
                </div>
                
                <div className="space-y-2">
                  <Label htmlFor="username">Username</Label>
                  <Input
                    id="username"
                    type="text"
                    placeholder="Enter username"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    className="h-12"
                  />
                </div>
                
                <div className="space-y-2">
                  <Label htmlFor="password">Password</Label>
                  <Input
                    id="password"
                    type="password"
                    placeholder="Enter password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    className="h-12"
                  />
                </div>
                
                <div className="flex items-center gap-2 text-sm text-green-600 bg-green-50 p-3 rounded-md">
                  <Wifi className="w-4 h-4" />
                  <span>Internet connection required for first login</span>
                </div>
                
                <div className="flex gap-2">
                  <Button 
                    onClick={() => setShowNewProfile(false)}
                    variant="outline"
                    className="flex-1 h-12"
                  >
                    Cancel
                  </Button>
                  <Button 
                    onClick={handleLogin}
                    disabled={isLoading}
                    className="flex-1 h-12 bg-green-600 hover:bg-green-700 text-white"
                  >
                    {isLoading ? 'Logging in...' : 'Login & Sync'}
                  </Button>
                </div>
              </>
            )}
          </div>
          
          <p className="text-green-100 text-xs text-center mt-6">
            Version 1.0.0 • DHIS2 Compatible
          </p>
        </div>
      </div>
    </div>
  );
}