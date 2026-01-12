import { ArrowLeft, Save, User } from 'lucide-react';
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

interface RegisterTrackedEntityProps {
  programName: string;
  onBack: () => void;
  onCreate: (data: Record<string, string>) => void;
}

export function RegisterTrackedEntity({ programName, onBack, onCreate }: RegisterTrackedEntityProps) {
  const [formData, setFormData] = useState({
    firstName: '',
    lastName: '',
    phone: '',
    nationalId: '',
    dateOfBirth: '',
    address: '',
    orgUnit: '',
    enrollmentDate: new Date().toISOString().split('T')[0]
  });

  const handleSubmit = () => {
    if (!formData.firstName || !formData.lastName || !formData.orgUnit) {
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
            <h1 className="text-lg">Register New Patient</h1>
            <p className="text-xs text-green-100">{programName}</p>
          </div>
        </div>
      </div>
      
      {/* Form Content */}
      <div className="flex-1 overflow-y-auto p-4 pb-24">
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4 mb-4">
          <div className="flex items-center justify-center mb-4">
            <div className="w-20 h-20 bg-purple-100 rounded-full flex items-center justify-center">
              <User className="w-10 h-10 text-purple-600" />
            </div>
          </div>
          
          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="firstName">
                First Name <span className="text-red-500">*</span>
              </Label>
              <Input
                id="firstName"
                type="text"
                value={formData.firstName}
                onChange={(e) => setFormData({ ...formData, firstName: e.target.value })}
                className="h-11"
              />
            </div>
            
            <div className="space-y-2">
              <Label htmlFor="lastName">
                Last Name <span className="text-red-500">*</span>
              </Label>
              <Input
                id="lastName"
                type="text"
                value={formData.lastName}
                onChange={(e) => setFormData({ ...formData, lastName: e.target.value })}
                className="h-11"
              />
            </div>
            
            <div className="space-y-2">
              <Label htmlFor="nationalId">National ID / Patient ID</Label>
              <Input
                id="nationalId"
                type="text"
                value={formData.nationalId}
                onChange={(e) => setFormData({ ...formData, nationalId: e.target.value })}
                className="h-11"
                placeholder="e.g., ANC-2026-001"
              />
            </div>
            
            <div className="space-y-2">
              <Label htmlFor="dateOfBirth">Date of Birth</Label>
              <Input
                id="dateOfBirth"
                type="date"
                value={formData.dateOfBirth}
                onChange={(e) => setFormData({ ...formData, dateOfBirth: e.target.value })}
                className="h-11"
              />
            </div>
            
            <div className="space-y-2">
              <Label htmlFor="phone">Phone Number</Label>
              <Input
                id="phone"
                type="tel"
                value={formData.phone}
                onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
                className="h-11"
                placeholder="+1234567890"
              />
            </div>
            
            <div className="space-y-2">
              <Label htmlFor="address">Address</Label>
              <Input
                id="address"
                type="text"
                value={formData.address}
                onChange={(e) => setFormData({ ...formData, address: e.target.value })}
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
            
            <div className="space-y-2">
              <Label htmlFor="enrollmentDate">
                Enrollment Date <span className="text-red-500">*</span>
              </Label>
              <Input
                id="enrollmentDate"
                type="date"
                value={formData.enrollmentDate}
                onChange={(e) => setFormData({ ...formData, enrollmentDate: e.target.value })}
                className="h-11"
              />
            </div>
          </div>
        </div>
      </div>
      
      {/* Bottom Action Bar */}
      <div className="fixed bottom-0 left-0 right-0 bg-white border-t border-gray-200 p-4">
        <div className="max-w-md mx-auto">
          <Button
            onClick={handleSubmit}
            className="w-full h-12 bg-purple-600 hover:bg-purple-700"
          >
            <Save className="w-4 h-4 mr-2" />
            Register Patient
          </Button>
        </div>
      </div>
    </div>
  );
}
