# UI/UX IMPLEMENTATION PLAN

## DETAILED IMPLEMENTATION PLAN

Based on specific user requirements, here's exactly what will be implemented:

## 1. LOGIN PAGE MODIFICATIONS

### **a. Icon Above Entry Fields**
**File**: `LoginScreen.kt` (lines 153-159, in the Column layout)  
**Plan**: 
- Add `Icon()` composable above the saved account section
- Use `Icons.Default.AccountCircle` as placeholder
- Add comment: `// TODO: Replace with custom app icon - modify this Icon() composable`
- Position: After `Arrangement.Center` but before first input field

### **b. DHIS2-Style Loading Animation**  
**File**: `LoginScreen.kt` (lines 95-118, splash screen section)
**Current**: Basic `CircularProgressIndicator` with "Loading your data..." text
**Plan**: Based on DHIS2 research, implement:
- Pulsing circle animation (similar to DHIS2's loading spinners)
- Replace single CircularProgressIndicator with 3-dot pulsing animation
- Keep same "Loading your data..." text but with better typography
- Use Material 3 colors with pulsing effect

## 2. DATASETS SCREEN MODIFICATIONS

### **a. Leading Icons & Entry Count Fix**
**File**: `DatasetsScreen.kt` (lines 280-291, ListCard implementation)
**Current**: `ListCard` with `"Entries: ${dataset.instanceCount}"` 
**Plan**:
- Add `leadingContent` parameter to ListCard with dataset type icon
- Change entry count from `"Entries: ${dataset.instanceCount}"` to `"${dataset.instanceCount} entries"`
- Use placeholder icons like `Icons.Default.Dataset` with TODO comment for customization

### **b. Pull-Down Filter Section**
**File**: `DatasetsScreen.kt` (lines 183-211, current filter button)  
**Current**: IconButton that opens dialog
**Plan**:
- Remove current IconButton filter approach
- Add expandable section below header bar that slides down
- Include search field, filter chips, and sort options in this section
- Animate expansion/collapse with slide animation
- Match DHIS2 Capture app's pull-down behavior

## 3. DATASET INSTANCES SCREEN MODIFICATIONS  

### **a. Pull-Down Filter (Same as Datasets)**
**File**: `DatasetInstancesScreen.kt` (similar to datasets filter approach)
**Plan**: Implement identical pull-down section as datasets screen

### **b. Completion & Sync Status Icons**
**File**: `DatasetInstancesScreen.kt` (lines 294-315, current StatusInfo implementation)
**Current**: Icon-based status display
**Plan**:
- **Complete**: Green "Complete" text + two green checkmark icons  
- **Incomplete**: Nothing shown
- **Sync Status**: Grey sync icon at far right (only for unsynced items)
- Remove current StatusInfo visual structure, keep data structure

### **c. Last Updated Date**
**File**: `DatasetInstancesScreen.kt` (in ListCard description area)
**Plan**: 
- Move date to top-right corner of ListCard
- Remove "Last updated:" prefix, show plain date only
- Make text faint and right-justified
- Use `SimpleDateFormat` for consistent date display

### **d. Attribute Option Combo Position**
**File**: `DatasetInstancesScreen.kt` (ListCard title/description structure)
**Plan**:
- Move attribute option combo from title to second row (description area)
- Keep period and org unit in title
- Restructure ListCardDescriptionModel accordingly

### **e. Header Icons During Loading**
**File**: `DatasetInstancesScreen.kt` + other screens with this issue
**Plan**:
- Ensure header icons remain visible during loading states
- Add `enabled = !state.isLoading` instead of hiding icons
- Apply consistent approach across all screens

### **f. Edit Entry Transition Fix**
**File**: `DatasetInstancesScreen.kt` (lines 260-267, navigation logic)
**Plan**:
- Add proper loading state management for navigation
- Show loading indicator immediately on click
- Use LaunchedEffect to monitor navigation completion
- Only hide loading when EditEntryScreen is fully loaded
- Implement smooth transition animations

---

## TECHNICAL APPROACH:

### **File Modifications Needed**:
1. `LoginScreen.kt` - Icon + loading animation  
2. `DatasetsScreen.kt` - Icons, entry count, pull-down filter
3. `DatasetInstancesScreen.kt` - All status/layout changes + pull-down filter
4. Potentially create new `PullDownFilterSection.kt` composable for reuse

### **New Dependencies** (if needed):
- None required - will use existing Compose Animation APIs

### **Testing Strategy**:
- Build after each screen modification
- Test loading states and transitions manually
- Ensure all existing functionality preserved

### **Modification Points Documentation**:
- Clear TODO comments for icon customization points
- Inline documentation for filter section customization

---

## IMPLEMENTATION ORDER:
1. Login Screen modifications (icon + loading animation)
2. Datasets Screen (icons, entry count, pull-down filter)
3. Dataset Instances Screen (all modifications)
4. Cross-screen testing and refinement