import { ArrowLeft, Send, Camera, Paperclip } from 'lucide-react';
import { Button } from './ui/button';
import { Label } from './ui/label';
import { Input } from './ui/input';
import { Textarea } from './ui/textarea';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from './ui/select';
import { useState } from 'react';
import { toast } from 'sonner';

interface ReportIssuesScreenProps {
  onBack: () => void;
}

export function ReportIssuesScreen({ onBack }: ReportIssuesScreenProps) {
  const [issueType, setIssueType] = useState('');
  const [subject, setSubject] = useState('');
  const [description, setDescription] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = () => {
    if (!issueType || !subject || !description) {
      toast.error('Please fill in all required fields');
      return;
    }

    setIsSubmitting(true);
    setTimeout(() => {
      setIsSubmitting(false);
      toast.success('Issue reported successfully', {
        description: 'Our team will review your report soon'
      });
      setIssueType('');
      setSubject('');
      setDescription('');
    }, 1500);
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
          <h1 className="text-lg">Report an Issue</h1>
        </div>
      </div>
      
      {/* Form Content */}
      <div className="flex-1 overflow-y-auto p-4">
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4 mb-4">
          <div className="flex items-start gap-3 p-3 bg-blue-50 border border-blue-200 rounded-md mb-4">
            <div className="text-blue-600 text-sm leading-relaxed">
              <strong>Help us improve!</strong> Describe any bugs, issues, or suggestions 
              you have. Include as much detail as possible.
            </div>
          </div>
          
          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="issuetype">
                Issue Type <span className="text-red-500">*</span>
              </Label>
              <Select value={issueType} onValueChange={setIssueType}>
                <SelectTrigger id="issuetype" className="h-11">
                  <SelectValue placeholder="Select issue type" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="bug">Bug Report</SelectItem>
                  <SelectItem value="crash">App Crash</SelectItem>
                  <SelectItem value="sync">Sync Issue</SelectItem>
                  <SelectItem value="data">Data Loss/Corruption</SelectItem>
                  <SelectItem value="ui">UI/UX Problem</SelectItem>
                  <SelectItem value="feature">Feature Request</SelectItem>
                  <SelectItem value="other">Other</SelectItem>
                </SelectContent>
              </Select>
            </div>
            
            <div className="space-y-2">
              <Label htmlFor="subject">
                Subject <span className="text-red-500">*</span>
              </Label>
              <Input
                id="subject"
                type="text"
                placeholder="Brief summary of the issue"
                value={subject}
                onChange={(e) => setSubject(e.target.value)}
                className="h-11"
              />
            </div>
            
            <div className="space-y-2">
              <Label htmlFor="description">
                Description <span className="text-red-500">*</span>
              </Label>
              <Textarea
                id="description"
                placeholder="Describe the issue in detail. Include steps to reproduce if applicable."
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                className="min-h-32 resize-none"
              />
              <p className="text-xs text-gray-500">
                {description.length}/500 characters
              </p>
            </div>
            
            <div className="space-y-2">
              <Label>Attachments (Optional)</Label>
              <div className="flex gap-2">
                <Button variant="outline" className="flex-1 h-10">
                  <Camera className="w-4 h-4 mr-2" />
                  Take Photo
                </Button>
                <Button variant="outline" className="flex-1 h-10">
                  <Paperclip className="w-4 h-4 mr-2" />
                  Attach File
                </Button>
              </div>
            </div>
          </div>
        </div>
        
        {/* System Info */}
        <div className="bg-gray-100 rounded-lg p-4 mb-4">
          <h3 className="text-sm font-medium mb-2">System Information</h3>
          <div className="space-y-1 text-xs text-gray-600">
            <p>App Version: 1.0.0 (Build 100)</p>
            <p>Device: Android 12</p>
            <p>DHIS2 Server: play.dhis2.org</p>
            <p>Last Sync: 2 hours ago</p>
          </div>
        </div>
        
        <Button
          onClick={handleSubmit}
          disabled={isSubmitting}
          className="w-full h-12 bg-green-600 hover:bg-green-700"
        >
          <Send className="w-4 h-4 mr-2" />
          {isSubmitting ? 'Submitting...' : 'Submit Report'}
        </Button>
      </div>
    </div>
  );
}
