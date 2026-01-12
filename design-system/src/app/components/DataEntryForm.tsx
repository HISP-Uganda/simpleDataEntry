import { ArrowLeft, Save, Send, WifiOff, Wifi, Check, AlertCircle, ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Label } from './ui/label';
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from './ui/accordion';
import { useState, useRef, useEffect } from 'react';
import { toast } from 'sonner';

interface DataEntryFormProps {
  datasetName: string;
  entryTitle: string;
  onBack: () => void;
  isOffline: boolean;
}

interface SectionProgress {
  id: string;
  name: string;
  completed: number;
  total: number;
  subsections?: { id: string; name: string; completed: number; total: number; }[];
}

const sections: SectionProgress[] = [
  {
    id: 'section1',
    name: 'Child Mortality Indicators',
    completed: 4,
    total: 8,
    subsections: [
      { id: 'section1-mortality', name: 'Mortality Data', completed: 4, total: 4 },
      { id: 'section1-nutrition', name: 'Nutrition', completed: 0, total: 4 }
    ]
  },
  {
    id: 'section2',
    name: 'Maternal Health Services',
    completed: 2,
    total: 6
  },
  {
    id: 'section3',
    name: 'Immunization Coverage',
    completed: 0,
    total: 7
  },
  {
    id: 'section4',
    name: 'Disease Surveillance',
    completed: 0,
    total: 5
  }
];

export function DataEntryForm({ datasetName, entryTitle, onBack, isOffline }: DataEntryFormProps) {
  const [isSaving, setIsSaving] = useState(false);
  const [isValidating, setIsValidating] = useState(false);
  const [currentSection, setCurrentSection] = useState('section1');
  const [currentSubsection, setCurrentSubsection] = useState<string | null>('section1-mortality');
  const [openSections, setOpenSections] = useState<string[]>(['section1']);
  const sectionRefs = useRef<Record<string, HTMLDivElement | null>>({});

  useEffect(() => {
    // Observe which section is in view
    const observers: IntersectionObserver[] = [];
    
    Object.entries(sectionRefs.current).forEach(([id, element]) => {
      if (element) {
        const observer = new IntersectionObserver(
          (entries) => {
            entries.forEach((entry) => {
              if (entry.isIntersecting) {
                setCurrentSection(id);
              }
            });
          },
          { threshold: 0.5 }
        );
        observer.observe(element);
        observers.push(observer);
      }
    });

    return () => observers.forEach(observer => observer.disconnect());
  }, []);

  const handleSave = () => {
    setIsSaving(true);
    setTimeout(() => {
      setIsSaving(false);
      toast.success(isOffline ? 'Draft saved locally' : 'Draft saved');
    }, 500);
  };

  const handleValidate = () => {
    setIsValidating(true);
    setTimeout(() => {
      setIsValidating(false);
      toast.success('Validation passed', {
        description: '2 warnings found'
      });
    }, 1000);
  };

  const handleComplete = () => {
    if (isOffline) {
      toast.success('Entry marked as completed offline');
    } else {
      toast.success('Entry completed and synced');
    }
  };

  const scrollToSection = (sectionId: string) => {
    sectionRefs.current[sectionId]?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    if (!openSections.includes(sectionId)) {
      setOpenSections([...openSections, sectionId]);
    }
  };

  const getSectionProgress = (section: SectionProgress) => {
    return section.total > 0 ? Math.round((section.completed / section.total) * 100) : 0;
  };

  const currentSectionData = sections.find(s => s.id === currentSection);
  const currentSectionIndex = sections.findIndex(s => s.id === currentSection);
  const hasSubsections = currentSectionData?.subsections && currentSectionData.subsections.length > 0;

  const navigateToNextSection = () => {
    if (currentSectionIndex < sections.length - 1) {
      const nextSection = sections[currentSectionIndex + 1];
      scrollToSection(nextSection.id);
      setCurrentSection(nextSection.id);
      // Reset to first subsection if exists
      if (nextSection.subsections && nextSection.subsections.length > 0) {
        setCurrentSubsection(nextSection.subsections[0].id);
      } else {
        setCurrentSubsection(null);
      }
    }
  };

  const navigateToPreviousSection = () => {
    if (currentSectionIndex > 0) {
      const prevSection = sections[currentSectionIndex - 1];
      scrollToSection(prevSection.id);
      setCurrentSection(prevSection.id);
      // Reset to first subsection if exists
      if (prevSection.subsections && prevSection.subsections.length > 0) {
        setCurrentSubsection(prevSection.subsections[0].id);
      } else {
        setCurrentSubsection(null);
      }
    }
  };

  const navigateToNextSubsection = () => {
    if (hasSubsections && currentSubsection) {
      const subsections = currentSectionData.subsections!;
      const currentSubIndex = subsections.findIndex(s => s.id === currentSubsection);
      if (currentSubIndex < subsections.length - 1) {
        setCurrentSubsection(subsections[currentSubIndex + 1].id);
      }
    }
  };

  const navigateToPreviousSubsection = () => {
    if (hasSubsections && currentSubsection) {
      const subsections = currentSectionData.subsections!;
      const currentSubIndex = subsections.findIndex(s => s.id === currentSubsection);
      if (currentSubIndex > 0) {
        setCurrentSubsection(subsections[currentSubIndex - 1].id);
      }
    }
  };

  const getCurrentSubsectionName = () => {
    if (hasSubsections && currentSubsection) {
      const subsection = currentSectionData.subsections!.find(s => s.id === currentSubsection);
      return subsection?.name;
    }
    return null;
  };

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      {/* Status Bar */}
      <div className="h-6 bg-green-700"></div>
      
      {/* App Bar */}
      <div className="bg-green-600 text-white shadow-md">
        <div className="px-4 py-3">
          <div className="flex items-center gap-3 mb-3">
            <Button 
              variant="ghost" 
              size="icon" 
              className="text-white hover:bg-green-700"
              onClick={onBack}
            >
              <ArrowLeft className="w-6 h-6" />
            </Button>
            <div className="flex-1 min-w-0">
              <h1 className="text-lg truncate">{datasetName}</h1>
              <p className="text-xs text-green-100 truncate">{entryTitle}</p>
            </div>
            {isOffline ? (
              <WifiOff className="w-5 h-5 text-red-300" />
            ) : (
              <Wifi className="w-5 h-5 text-green-200" />
            )}
          </div>
          
          {/* Action Buttons */}
          <div className="flex gap-2">
            <Button
              onClick={handleSave}
              disabled={isSaving}
              variant="secondary"
              size="sm"
              className="flex-1 h-9 bg-white/20 hover:bg-white/30 text-white border-0"
            >
              <Save className="w-4 h-4 mr-1" />
              Save
            </Button>
            <Button
              onClick={handleValidate}
              disabled={isValidating}
              variant="secondary"
              size="sm"
              className="flex-1 h-9 bg-white/20 hover:bg-white/30 text-white border-0"
            >
              <AlertCircle className="w-4 h-4 mr-1" />
              Validate
            </Button>
            <Button
              onClick={handleComplete}
              disabled={isOffline}
              size="sm"
              className="flex-1 h-9 bg-green-800 hover:bg-green-900 text-white"
            >
              <Check className="w-4 h-4 mr-1" />
              Complete
            </Button>
          </div>
        </div>
      </div>
      
      {/* Current Section Indicator with Navigation */}
      <div className="bg-blue-50 border-b border-blue-200 px-2 py-2">
        <div className="flex items-center gap-1">
          {/* Section Navigation */}
          <Button
            variant="ghost"
            size="icon"
            className="h-8 w-8 flex-shrink-0 text-blue-900"
            onClick={navigateToPreviousSection}
            disabled={currentSectionIndex === 0}
          >
            <ChevronLeft className="w-5 h-5" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            className="h-8 w-8 flex-shrink-0 text-blue-900"
            onClick={navigateToNextSection}
            disabled={currentSectionIndex === sections.length - 1}
          >
            <ChevronRight className="w-5 h-5" />
          </Button>
          
          {/* Section Name */}
          <div className="flex-1 min-w-0 px-2">
            <div className="flex items-center gap-2">
              <div className="w-2 h-2 bg-blue-600 rounded-full animate-pulse flex-shrink-0"></div>
              <div className="flex-1 min-w-0">
                <div className="text-sm text-blue-900 truncate">
                  {currentSectionData?.name || 'Data Entry'}
                </div>
                {hasSubsections && getCurrentSubsectionName() && (
                  <div className="text-xs text-blue-700 truncate">
                    {getCurrentSubsectionName()}
                  </div>
                )}
              </div>
            </div>
          </div>
          
          {/* Subsection Navigation (only shown if current section has subsections) */}
          {hasSubsections && (
            <>
              <Button
                variant="ghost"
                size="icon"
                className="h-8 w-8 flex-shrink-0 text-blue-900"
                onClick={navigateToPreviousSubsection}
                disabled={
                  !currentSubsection ||
                  currentSectionData.subsections?.findIndex(s => s.id === currentSubsection) === 0
                }
              >
                <ChevronLeft className="w-4 h-4" />
              </Button>
              <Button
                variant="ghost"
                size="icon"
                className="h-8 w-8 flex-shrink-0 text-blue-900"
                onClick={navigateToNextSubsection}
                disabled={
                  !currentSubsection ||
                  currentSectionData.subsections?.findIndex(s => s.id === currentSubsection) ===
                    (currentSectionData.subsections?.length || 0) - 1
                }
              >
                <ChevronRight className="w-4 h-4" />
              </Button>
            </>
          )}
        </div>
      </div>
      
      {/* Form Content */}
      <div className="flex-1 overflow-y-auto">
        <div className="p-4">
          <Accordion 
            type="multiple" 
            value={openSections}
            onValueChange={setOpenSections}
            className="space-y-3"
          >
            {/* Section 1: Child Mortality */}
            <AccordionItem 
              value="section1" 
              className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden"
              ref={(el) => (sectionRefs.current['section1'] = el)}
            >
              <AccordionTrigger className="px-4 py-3 hover:no-underline hover:bg-gray-50">
                <div className="flex items-center justify-between w-full pr-2">
                  <span>Child Mortality Indicators</span>
                  <span className="text-xs text-gray-500 mr-2">4/8 completed</span>
                </div>
              </AccordionTrigger>
              <AccordionContent className="px-4 pb-4">
                {/* Nested Accordion for Subsections */}
                <Accordion type="multiple" className="space-y-2 pt-2">
                  <AccordionItem value="mortality" className="border rounded-md">
                    <AccordionTrigger className="px-3 py-2 text-sm hover:no-underline hover:bg-gray-50">
                      <div className="flex items-center justify-between w-full pr-2">
                        <span>Mortality Data</span>
                        <span className="text-xs text-gray-500 mr-2">4/4</span>
                      </div>
                    </AccordionTrigger>
                    <AccordionContent className="px-3 pb-3">
                      <div className="space-y-3 pt-2">
                        <div className="space-y-2">
                          <Label htmlFor="under5deaths">Deaths under 5 years</Label>
                          <Input
                            id="under5deaths"
                            type="number"
                            placeholder="Enter value"
                            defaultValue="12"
                            className="h-10"
                          />
                        </div>
                        
                        <div className="space-y-2">
                          <Label htmlFor="infantdeaths">Infant deaths (0-1 year)</Label>
                          <Input
                            id="infantdeaths"
                            type="number"
                            placeholder="Enter value"
                            defaultValue="8"
                            className="h-10"
                          />
                        </div>
                        
                        <div className="space-y-2">
                          <Label htmlFor="neonataldeaths">Neonatal deaths (0-28 days)</Label>
                          <Input
                            id="neonataldeaths"
                            type="number"
                            placeholder="Enter value"
                            defaultValue="5"
                            className="h-10"
                          />
                        </div>
                        
                        <div className="space-y-2">
                          <Label htmlFor="stillbirths">Stillbirths</Label>
                          <Input
                            id="stillbirths"
                            type="number"
                            placeholder="Enter value"
                            defaultValue="3"
                            className="h-10"
                          />
                        </div>
                      </div>
                    </AccordionContent>
                  </AccordionItem>
                  
                  <AccordionItem value="nutrition" className="border rounded-md">
                    <AccordionTrigger className="px-3 py-2 text-sm hover:no-underline hover:bg-gray-50">
                      <div className="flex items-center justify-between w-full pr-2">
                        <span>Nutrition</span>
                        <span className="text-xs text-gray-500 mr-2">0/4</span>
                      </div>
                    </AccordionTrigger>
                    <AccordionContent className="px-3 pb-3">
                      <div className="space-y-3 pt-2">
                        <div className="space-y-2">
                          <Label htmlFor="malenutrition">Severe malnutrition cases</Label>
                          <Input
                            id="malenutrition"
                            type="number"
                            placeholder="Enter value"
                            className="h-10"
                          />
                        </div>
                        
                        <div className="space-y-2">
                          <Label htmlFor="growth">Children on growth monitoring</Label>
                          <Input
                            id="growth"
                            type="number"
                            placeholder="Enter value"
                            className="h-10"
                          />
                        </div>
                        
                        <div className="space-y-2">
                          <Label htmlFor="vitamin">Vitamin A supplementation</Label>
                          <Input
                            id="vitamin"
                            type="number"
                            placeholder="Enter value"
                            className="h-10"
                          />
                        </div>
                        
                        <div className="space-y-2">
                          <Label htmlFor="deworming">Deworming doses</Label>
                          <Input
                            id="deworming"
                            type="number"
                            placeholder="Enter value"
                            className="h-10"
                          />
                        </div>
                      </div>
                    </AccordionContent>
                  </AccordionItem>
                </Accordion>
              </AccordionContent>
            </AccordionItem>
            
            {/* Section 2: Maternal Health */}
            <AccordionItem 
              value="section2" 
              className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden"
              ref={(el) => (sectionRefs.current['section2'] = el)}
            >
              <AccordionTrigger className="px-4 py-3 hover:no-underline hover:bg-gray-50">
                <div className="flex items-center justify-between w-full pr-2">
                  <span>Maternal Health Services</span>
                  <span className="text-xs text-gray-500 mr-2">2/6 completed</span>
                </div>
              </AccordionTrigger>
              <AccordionContent className="px-4 pb-4">
                <div className="space-y-4 pt-2">
                  <div className="space-y-2">
                    <Label htmlFor="anc1">ANC 1st visit</Label>
                    <Input
                      id="anc1"
                      type="number"
                      placeholder="Enter value"
                      defaultValue="145"
                      className="h-10"
                    />
                  </div>
                  
                  <div className="space-y-2">
                    <Label htmlFor="anc4">ANC 4th visit</Label>
                    <Input
                      id="anc4"
                      type="number"
                      placeholder="Enter value"
                      defaultValue="98"
                      className="h-10"
                    />
                  </div>
                  
                  <div className="space-y-2">
                    <Label htmlFor="deliveries">Facility deliveries</Label>
                    <Input
                      id="deliveries"
                      type="number"
                      placeholder="Enter value"
                      className="h-10"
                    />
                  </div>
                  
                  <div className="space-y-2">
                    <Label htmlFor="csections">C-sections performed</Label>
                    <Input
                      id="csections"
                      type="number"
                      placeholder="Enter value"
                      className="h-10"
                    />
                  </div>
                  
                  <div className="space-y-2">
                    <Label htmlFor="pnc">Postnatal care visits</Label>
                    <Input
                      id="pnc"
                      type="number"
                      placeholder="Enter value"
                      className="h-10"
                    />
                  </div>
                  
                  <div className="space-y-2">
                    <Label htmlFor="familyplan">Family planning services</Label>
                    <Input
                      id="familyplan"
                      type="number"
                      placeholder="Enter value"
                      className="h-10"
                    />
                  </div>
                </div>
              </AccordionContent>
            </AccordionItem>
            
            {/* Section 3: Immunization */}
            <AccordionItem 
              value="section3" 
              className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden"
              ref={(el) => (sectionRefs.current['section3'] = el)}
            >
              <AccordionTrigger className="px-4 py-3 hover:no-underline hover:bg-gray-50">
                <div className="flex items-center justify-between w-full pr-2">
                  <span>Immunization Coverage</span>
                  <span className="text-xs text-gray-500 mr-2">0/7 completed</span>
                </div>
              </AccordionTrigger>
              <AccordionContent className="px-4 pb-4">
                <div className="space-y-4 pt-2">
                  <div className="space-y-2">
                    <Label htmlFor="bcg">BCG doses given</Label>
                    <Input
                      id="bcg"
                      type="number"
                      placeholder="Enter value"
                      className="h-10"
                    />
                  </div>
                  
                  <div className="space-y-2">
                    <Label htmlFor="opv0">OPV 0 doses</Label>
                    <Input
                      id="opv0"
                      type="number"
                      placeholder="Enter value"
                      className="h-10"
                    />
                  </div>
                  
                  <div className="space-y-2">
                    <Label htmlFor="dpt1">DPT 1 doses</Label>
                    <Input
                      id="dpt1"
                      type="number"
                      placeholder="Enter value"
                      className="h-10"
                    />
                  </div>
                  
                  <div className="space-y-2">
                    <Label htmlFor="dpt3">DPT 3 doses</Label>
                    <Input
                      id="dpt3"
                      type="number"
                      placeholder="Enter value"
                      className="h-10"
                    />
                  </div>
                  
                  <div className="space-y-2">
                    <Label htmlFor="measles">Measles doses</Label>
                    <Input
                      id="measles"
                      type="number"
                      placeholder="Enter value"
                      className="h-10"
                    />
                  </div>
                  
                  <div className="space-y-2">
                    <Label htmlFor="rotavirus">Rotavirus doses</Label>
                    <Input
                      id="rotavirus"
                      type="number"
                      placeholder="Enter value"
                      className="h-10"
                    />
                  </div>
                  
                  <div className="space-y-2">
                    <Label htmlFor="pneumo">Pneumococcal doses</Label>
                    <Input
                      id="pneumo"
                      type="number"
                      placeholder="Enter value"
                      className="h-10"
                    />
                  </div>
                </div>
              </AccordionContent>
            </AccordionItem>
            
            {/* Section 4: Disease Surveillance */}
            <AccordionItem 
              value="section4" 
              className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden"
              ref={(el) => (sectionRefs.current['section4'] = el)}
            >
              <AccordionTrigger className="px-4 py-3 hover:no-underline hover:bg-gray-50">
                <div className="flex items-center justify-between w-full pr-2">
                  <span>Disease Surveillance</span>
                  <span className="text-xs text-gray-500 mr-2">0/5 completed</span>
                </div>
              </AccordionTrigger>
              <AccordionContent className="px-4 pb-4">
                <div className="space-y-4 pt-2">
                  <div className="space-y-2">
                    <Label htmlFor="malaria">Confirmed malaria cases</Label>
                    <Input
                      id="malaria"
                      type="number"
                      placeholder="Enter value"
                      className="h-10"
                    />
                  </div>
                  
                  <div className="space-y-2">
                    <Label htmlFor="tb">TB cases registered</Label>
                    <Input
                      id="tb"
                      type="number"
                      placeholder="Enter value"
                      className="h-10"
                    />
                  </div>
                  
                  <div className="space-y-2">
                    <Label htmlFor="hiv">HIV tests conducted</Label>
                    <Input
                      id="hiv"
                      type="number"
                      placeholder="Enter value"
                      className="h-10"
                    />
                  </div>
                  
                  <div className="space-y-2">
                    <Label htmlFor="diarrhea">Diarrhea cases</Label>
                    <Input
                      id="diarrhea"
                      type="number"
                      placeholder="Enter value"
                      className="h-10"
                    />
                  </div>
                  
                  <div className="space-y-2">
                    <Label htmlFor="pneumonia">Pneumonia cases</Label>
                    <Input
                      id="pneumonia"
                      type="number"
                      placeholder="Enter value"
                      className="h-10"
                    />
                  </div>
                </div>
              </AccordionContent>
            </AccordionItem>
          </Accordion>
        </div>
      </div>
    </div>
  );
}