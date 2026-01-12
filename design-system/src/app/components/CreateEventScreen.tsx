import { ArrowLeft, Save, Activity } from 'lucide-react';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Label } from './ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from './ui/select';
import { useState } from 'react';
import { toast } from 'sonner';

interface CreateEventScreenProps {
  programName: string;
  programStage?: string;
  onBack: () => void;
  onCreate: (data: Record<string, string>) => void;
}

export function CreateEventScreen({ programName, programStage, onBack, onCreate }: CreateEventScreenProps) {
  const [formData, setFormData] = useState({
    eventDate: new Date().toISOString().split('T')[0],
    orgUnit: '',
    programStage: programStage || ''
  });

  const handleSubmit = () => {
    if (!formData.eventDate || !formData.orgUnit) {
      toast.error('Please fill in all required fields');
      return;
    }

    onCreate(formData);
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
            <h1 className="text-lg">Create Event</h1>
            <p className="text-xs text-green-100">{programName}</p>
          </div>
        </div>
      </div>
      
      {/* Form Content */}
      <div className="flex-1 overflow-y-auto p-4 pb-24">
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
          <div className="flex items-center justify-center mb-6">
            <div className="w-16 h-16 bg-orange-100 rounded-full flex items-center justify-center">
              <Activity className="w-8 h-8 text-orange-600" />
            </div>
          </div>
          
          <div className="space-y-4">
            {!programStage && (
              <div className="space-y-2">
                <Label htmlFor="programStage">
                  Event Type <span className="text-red-500">*</span>
                </Label>
                <Select value={formData.programStage} onValueChange={(value) => setFormData({ ...formData, programStage: value })}>
                  <SelectTrigger id="programStage" className="h-11">
                    <SelectValue placeholder="Select event type" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="first-anc">First ANC Visit</SelectItem>
                    <SelectItem value="second-anc">Second ANC Visit</SelectItem>
                    <SelectItem value="third-anc">Third ANC Visit</SelectItem>
                    <SelectItem value="fourth-anc">Fourth ANC Visit</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            )}
            
            <div className="space-y-2">
              <Label htmlFor="eventDate">
                Event Date <span className="text-red-500">*</span>
              </Label>
              <Input
                id="eventDate"
                type="date"
                value={formData.eventDate}
                onChange={(e) => setFormData({ ...formData, eventDate: e.target.value })}
                className="h-11"
              />
            </div>
            
            <div className="space-y-2">
              <Label htmlFor="orgUnit">
                Organization Unit <span className="text-red-500">*</span>
              </Label>
              <Select value={formData.orgUnit} onValueChange={(value) => setFormData({ ...formData, orgUnit: value })}>
                <SelectTrigger id="orgUnit" className="h-11">
                  <SelectValue placeholder="Select facility" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="district-hospital">District Hospital</SelectItem>
                  <SelectItem value="health-center-a">Health Center A</SelectItem>
                  <SelectItem value="health-center-b">Health Center B</SelectItem>
                  <SelectItem value="health-center-c">Health Center C</SelectItem>
                </SelectContent>
              </Select>
            </div>
            
            <div className="bg-blue-50 border border-blue-200 rounded-md p-3 mt-4">
              <p className="text-sm text-blue-900">
                <strong>Note:</strong> After creating the event, you'll be able to enter the data for this visit.
              </p>
            </div>
          </div>
        </div>
      </div>
      
      {/* Bottom Action Bar */}
      <div className="fixed bottom-0 left-0 right-0 bg-white border-t border-gray-200 p-4">
        <div className="max-w-md mx-auto">
          <Button
            onClick={handleSubmit}
            className="w-full h-12 bg-orange-600 hover:bg-orange-700"
          >
            <Save className="w-4 h-4 mr-2" />
            Create Event
          </Button>
        </div>
      </div>
    </div>
  );
}
