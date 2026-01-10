import { Database, Download, CheckCircle } from 'lucide-react';
import { Progress } from './ui/progress';
import { useEffect, useState } from 'react';

interface LoadingScreenProps {
  type: 'login' | 'entry' | 'sync';
  onComplete: () => void;
}

interface LoadingStep {
  label: string;
  progress: number;
}

export function LoadingScreen({ type, onComplete }: LoadingScreenProps) {
  const [currentStep, setCurrentStep] = useState(0);
  const [progress, setProgress] = useState(0);

  const loginSteps: LoadingStep[] = [
    { label: 'Connecting to server...', progress: 20 },
    { label: 'Downloading metadata...', progress: 50 },
    { label: 'Downloading data...', progress: 80 },
    { label: 'Finalizing setup...', progress: 100 }
  ];

  const entrySteps: LoadingStep[] = [
    { label: 'Loading form structure...', progress: 40 },
    { label: 'Preparing data fields...', progress: 80 },
    { label: 'Ready!', progress: 100 }
  ];

  const syncSteps: LoadingStep[] = [
    { label: 'Uploading local changes...', progress: 30 },
    { label: 'Fetching updates...', progress: 70 },
    { label: 'Syncing complete!', progress: 100 }
  ];

  const steps = type === 'login' ? loginSteps : type === 'entry' ? entrySteps : syncSteps;

  useEffect(() => {
    const stepDuration = type === 'entry' ? 500 : 1200;
    
    if (currentStep < steps.length) {
      const timer = setTimeout(() => {
        setProgress(steps[currentStep].progress);
        
        if (currentStep === steps.length - 1) {
          setTimeout(() => {
            onComplete();
          }, 500);
        } else {
          setCurrentStep(currentStep + 1);
        }
      }, stepDuration);

      return () => clearTimeout(timer);
    }
  }, [currentStep, steps, onComplete, type]);

  const getIcon = () => {
    if (currentStep === steps.length - 1) {
      return <CheckCircle className="w-16 h-16 text-green-600" />;
    }
    return type === 'sync' ? (
      <Download className="w-16 h-16 text-blue-600 animate-bounce" />
    ) : (
      <Database className="w-16 h-16 text-green-600 animate-pulse" />
    );
  };

  return (
    <div className="min-h-screen bg-gradient-to-b from-green-600 to-green-700 flex flex-col items-center justify-center px-6">
      {/* Status Bar */}
      <div className="fixed top-0 left-0 right-0 h-6 bg-green-800"></div>
      
      <div className="w-full max-w-md">
        {/* Icon */}
        <div className="flex justify-center mb-8">
          <div className="w-24 h-24 bg-white rounded-full flex items-center justify-center shadow-lg">
            {getIcon()}
          </div>
        </div>
        
        {/* Current Step */}
        <div className="text-center mb-8">
          <h2 className="text-white text-xl mb-2">
            {type === 'login' && 'Setting Up Your Workspace'}
            {type === 'entry' && 'Preparing Form'}
            {type === 'sync' && 'Syncing Data'}
          </h2>
          <p className="text-green-100 text-sm">
            {steps[currentStep]?.label}
          </p>
        </div>
        
        {/* Progress Bar */}
        <div className="bg-white rounded-lg p-6 shadow-xl">
          <Progress value={progress} className="h-3 mb-4" />
          <div className="space-y-2">
            {steps.map((step, index) => (
              <div
                key={index}
                className={`flex items-center gap-2 text-sm ${
                  index <= currentStep ? 'text-gray-900' : 'text-gray-400'
                }`}
              >
                <div
                  className={`w-4 h-4 rounded-full flex items-center justify-center ${
                    index < currentStep
                      ? 'bg-green-600'
                      : index === currentStep
                      ? 'bg-blue-600 animate-pulse'
                      : 'bg-gray-300'
                  }`}
                >
                  {index < currentStep && (
                    <CheckCircle className="w-3 h-3 text-white" />
                  )}
                </div>
                <span>{step.label}</span>
              </div>
            ))}
          </div>
        </div>
        
        <p className="text-green-100 text-xs text-center mt-6">
          Please do not close the app
        </p>
      </div>
    </div>
  );
}
