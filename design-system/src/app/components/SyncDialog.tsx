import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from './ui/alert-dialog';
import { Download, Upload } from 'lucide-react';

interface SyncDialogProps {
  open: boolean;
  onClose: () => void;
  onSync: (mode: 'download' | 'upload-download') => void;
}

export function SyncDialog({ open, onClose, onSync }: SyncDialogProps) {
  return (
    <AlertDialog open={open} onOpenChange={onClose}>
      <AlertDialogContent className="max-w-sm">
        <AlertDialogHeader>
          <AlertDialogTitle>Sync Data</AlertDialogTitle>
          <AlertDialogDescription className="text-left">
            Choose how you want to sync your data with the server.
          </AlertDialogDescription>
        </AlertDialogHeader>
        
        <div className="space-y-3 py-4">
          <button
            onClick={() => onSync('download')}
            className="w-full p-4 border-2 border-blue-200 rounded-lg hover:bg-blue-50 active:bg-blue-100 transition-colors text-left"
          >
            <div className="flex items-start gap-3">
              <div className="w-10 h-10 bg-blue-100 rounded-full flex items-center justify-center flex-shrink-0">
                <Download className="w-5 h-5 text-blue-600" />
              </div>
              <div className="flex-1 min-w-0">
                <div className="font-medium text-sm mb-1">Download Only</div>
                <div className="text-xs text-gray-600">
                  Fetch latest data from server without uploading local changes
                </div>
              </div>
            </div>
          </button>
          
          <button
            onClick={() => onSync('upload-download')}
            className="w-full p-4 border-2 border-green-200 rounded-lg hover:bg-green-50 active:bg-green-100 transition-colors text-left"
          >
            <div className="flex items-start gap-3">
              <div className="w-10 h-10 bg-green-100 rounded-full flex items-center justify-center flex-shrink-0">
                <Upload className="w-5 h-5 text-green-600" />
              </div>
              <div className="flex-1 min-w-0">
                <div className="font-medium text-sm mb-1">Upload & Download</div>
                <div className="text-xs text-gray-600">
                  Upload local changes and fetch latest data from server
                </div>
              </div>
            </div>
          </button>
        </div>
        
        <AlertDialogFooter>
          <AlertDialogCancel onClick={onClose}>Cancel</AlertDialogCancel>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
