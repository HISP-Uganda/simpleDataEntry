import { ArrowLeft, Info, Github, Mail, ExternalLink } from 'lucide-react';
import { Button } from './ui/button';
import { Separator } from './ui/separator';

interface AboutScreenProps {
  onBack: () => void;
}

export function AboutScreen({ onBack }: AboutScreenProps) {
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
          <h1 className="text-lg">About</h1>
        </div>
      </div>
      
      {/* About Content */}
      <div className="flex-1 overflow-y-auto">
        <div className="p-4 space-y-6">
          {/* App Info */}
          <div className="flex flex-col items-center text-center py-6">
            <div className="w-20 h-20 bg-green-600 rounded-full flex items-center justify-center shadow-lg mb-4">
              <Info className="w-10 h-10 text-white" />
            </div>
            <h2 className="text-2xl mb-1">Simple Data Entry</h2>
            <p className="text-gray-600 text-sm mb-2">DHIS2 Data Collection</p>
            <p className="text-gray-500 text-xs">Version 1.0.0 (Build 100)</p>
          </div>
          
          {/* Description */}
          <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
            <h3 className="font-medium mb-2">About This App</h3>
            <p className="text-sm text-gray-600 leading-relaxed">
              Simple Data Entry is an offline-capable mobile application designed for 
              efficient health data collection and reporting. The app syncs with DHIS2 
              systems to provide seamless data management for health facilities.
            </p>
          </div>
          
          {/* Features */}
          <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
            <h3 className="font-medium mb-3">Key Features</h3>
            <ul className="space-y-2 text-sm text-gray-600">
              <li className="flex items-start gap-2">
                <span className="text-green-600 mt-0.5">•</span>
                <span>Offline data entry with automatic sync</span>
              </li>
              <li className="flex items-start gap-2">
                <span className="text-green-600 mt-0.5">•</span>
                <span>Multiple profile support</span>
              </li>
              <li className="flex items-start gap-2">
                <span className="text-green-600 mt-0.5">•</span>
                <span>Secure credential storage</span>
              </li>
              <li className="flex items-start gap-2">
                <span className="text-green-600 mt-0.5">•</span>
                <span>Data validation and bulk operations</span>
              </li>
              <li className="flex items-start gap-2">
                <span className="text-green-600 mt-0.5">•</span>
                <span>DHIS2 compatible data formats</span>
              </li>
            </ul>
          </div>
          
          <Separator />
          
          {/* Links */}
          <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
            <div className="px-4 py-3 bg-gray-50 border-b border-gray-200">
              <h3 className="text-sm font-medium">Resources</h3>
            </div>
            <div className="divide-y divide-gray-200">
              <button className="w-full px-4 py-3 flex items-center justify-between hover:bg-gray-50 active:bg-gray-100">
                <div className="flex items-center gap-3">
                  <Github className="w-5 h-5 text-gray-500" />
                  <span className="text-sm">Source Code</span>
                </div>
                <ExternalLink className="w-4 h-4 text-gray-400" />
              </button>
              
              <button className="w-full px-4 py-3 flex items-center justify-between hover:bg-gray-50 active:bg-gray-100">
                <div className="flex items-center gap-3">
                  <Info className="w-5 h-5 text-gray-500" />
                  <span className="text-sm">Documentation</span>
                </div>
                <ExternalLink className="w-4 h-4 text-gray-400" />
              </button>
              
              <button className="w-full px-4 py-3 flex items-center justify-between hover:bg-gray-50 active:bg-gray-100">
                <div className="flex items-center gap-3">
                  <Mail className="w-5 h-5 text-gray-500" />
                  <span className="text-sm">Contact Support</span>
                </div>
                <ExternalLink className="w-4 h-4 text-gray-400" />
              </button>
            </div>
          </div>
          
          {/* Legal */}
          <div className="text-center space-y-2 pt-4">
            <p className="text-xs text-gray-500">
              © 2026 Simple Data Entry. All rights reserved.
            </p>
            <div className="flex items-center justify-center gap-4 text-xs">
              <button className="text-green-600 hover:underline">
                Privacy Policy
              </button>
              <span className="text-gray-300">•</span>
              <button className="text-green-600 hover:underline">
                Terms of Service
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
