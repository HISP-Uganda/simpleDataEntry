import { ArrowLeft, Save } from 'lucide-react';
import { Button } from './ui/button';
import { Label } from './ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from './ui/select';
import { Input } from './ui/input';
import { useState } from 'react';

interface CreateEntryScreenProps {
  datasetName: string;
  onBack: () => void;
  onCreate: (entry: { period: string; orgUnit: string; attributes: Record<string, string> }) => void;
}

export function CreateEntryScreen({ datasetName, onBack, onCreate }: CreateEntryScreenProps) {
  const [period, setPeriod] = useState('');
  const [orgUnit, setOrgUnit] = useState('');
  const [facilityType, setFacilityType] = useState('');
  const [reportingLevel, setReportingLevel] = useState('');

  const handleCreate = () => {
    onCreate({
      period,
      orgUnit,
      attributes: {
        facilityType,
        reportingLevel
      }
    });
  };

  const canCreate = period && orgUnit;

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
            <h1 className="text-lg">Create New Entry</h1>
            <p className="text-xs text-green-100">{datasetName}</p>
          </div>
        </div>
      </div>
      
      {/* Form Content */}
      <div className="flex-1 overflow-y-auto p-4">
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4 space-y-4">
          <div className="space-y-2">
            <Label htmlFor="period" className="text-sm">
              Reporting Period <span className="text-red-500">*</span>
            </Label>
            <Select value={period} onValueChange={setPeriod}>
              <SelectTrigger id="period" className="h-11">
                <SelectValue placeholder="Select period" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="January 2026">January 2026</SelectItem>
                <SelectItem value="December 2025">December 2025</SelectItem>
                <SelectItem value="November 2025">November 2025</SelectItem>
                <SelectItem value="October 2025">October 2025</SelectItem>
                <SelectItem value="September 2025">September 2025</SelectItem>
              </SelectContent>
            </Select>
          </div>
          
          <div className="space-y-2">
            <Label htmlFor="orgunit" className="text-sm">
              Organisation Unit (Location) <span className="text-red-500">*</span>
            </Label>
            <Select value={orgUnit} onValueChange={setOrgUnit}>
              <SelectTrigger id="orgunit" className="h-11">
                <SelectValue placeholder="Select organisation unit" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="District Hospital">District Hospital</SelectItem>
                <SelectItem value="Health Center A">Health Center A</SelectItem>
                <SelectItem value="Health Center B">Health Center B</SelectItem>
                <SelectItem value="Health Center C">Health Center C</SelectItem>
                <SelectItem value="Health Post 1">Health Post 1</SelectItem>
                <SelectItem value="Health Post 2">Health Post 2</SelectItem>
              </SelectContent>
            </Select>
          </div>
          
          <div className="border-t border-gray-200 pt-4 mt-4">
            <h3 className="text-sm mb-3">Attribute Combination (Optional)</h3>
            
            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="facilitytype" className="text-sm text-gray-600">
                  Facility Type
                </Label>
                <Select value={facilityType} onValueChange={setFacilityType}>
                  <SelectTrigger id="facilitytype" className="h-11">
                    <SelectValue placeholder="Select facility type" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="hospital">Hospital</SelectItem>
                    <SelectItem value="health_center">Health Center</SelectItem>
                    <SelectItem value="health_post">Health Post</SelectItem>
                    <SelectItem value="clinic">Clinic</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              
              <div className="space-y-2">
                <Label htmlFor="reportinglevel" className="text-sm text-gray-600">
                  Reporting Level
                </Label>
                <Select value={reportingLevel} onValueChange={setReportingLevel}>
                  <SelectTrigger id="reportinglevel" className="h-11">
                    <SelectValue placeholder="Select reporting level" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="national">National</SelectItem>
                    <SelectItem value="regional">Regional</SelectItem>
                    <SelectItem value="district">District</SelectItem>
                    <SelectItem value="facility">Facility</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
          </div>
          
          <div className="bg-blue-50 border border-blue-200 rounded-md p-3 text-sm text-blue-800">
            <strong>Note:</strong> Ensure the period and organisation unit combination is unique.
          </div>
        </div>
      </div>
      
      {/* Bottom Action Bar */}
      <div className="bg-white border-t border-gray-200 p-4 shadow-lg">
        <Button
          onClick={handleCreate}
          disabled={!canCreate}
          className="w-full h-12 bg-green-600 hover:bg-green-700"
        >
          <Save className="w-4 h-4 mr-2" />
          Create Entry
        </Button>
      </div>
    </div>
  );
}
