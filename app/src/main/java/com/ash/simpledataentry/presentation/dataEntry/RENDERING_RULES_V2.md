# RENDERING_RULES_V2.md

## Data Entry UI Rendering Rules (Category-Order-Driven)

### Overview

This document defines the **new, category-order-driven approach** for rendering nested accordions and entry fields in the data entry UI. It supersedes all previous logic that relied on special handling for sex/gender or two-option categories. The rules here ensure that the UI structure is always determined by the order of categories in the category combination metadata.

---

## 1. Category Order Determines UI Structure

- The order of categories in the category combination (as defined in metadata) **dictates the nesting of accordions and the placement of entry fields**.
- The **first category** in the list is always the outermost accordion header.
- The **last category** in the list determines the set of entry fields shown at the deepest level.
- Any **intermediate categories** (if present) create further nested accordions.

### Example
For a category combination `[A, B, C]`:
- UI structure:
  - Accordion for each value of `A`
    - Accordion for each value of `B`
      - Entry fields for each value of `C` (deepest level)

If only one category, just one level of accordion, then entry fields.

---

## 2. Entry Field Placement

- **Only the last category** in the combination determines the entry fields at the deepest level.
- For each unique value of the last category, show the entry fields for the corresponding data values.
- No entry fields are shown at intermediate accordion levels.

---

## 3. Mixed Category Combinations in a Section

- If a section contains data elements with different category combinations:
  - Data elements with the **default (zero) category** are rendered as a flat list.
  - All others use the recursive accordion logic as above, based on their own category combination order.

---

## 4. No Special Cases

- **Remove all logic** that checks for "sex", "gender", or two-option categories.
- No more "side-by-side" or "grid" logic based on category names or option counts.
- The UI is generic and always follows the order from the category combination.

---

## 5. Implementation Guidelines

### Data Preparation
- For each data element, get its category combination structure as an **ordered list** of categories and their options.
- Group data values by their category combination structure.

### Recursive Accordion Rendering
- For each group (i.e., each unique category combination structure):
  - If the structure is empty (default), render as a flat list.
  - Otherwise, recursively render accordions:
    - At each level, use the next category in the order.
    - At the deepest level (last category), render entry fields for each option.

#### Pseudocode
```kotlin
fun renderAccordion(
    categories: List<Category>,
    values: List<DataValue>,
    path: List<String>
) {
    if (categories.isEmpty()) {
        // Render entry fields for values
    } else {
        val currentCategory = categories.first()
        val restCategories = categories.drop(1)
        for (option in currentCategory.options) {
            // Accordion header for option
            // On expand: renderAccordion(restCategories, filteredValues, path + option.uid)
        }
    }
}
```
- At the deepest level, show entry fields for all values matching the full path.

### SectionContent/CategoryAccordionRecursive
- Refactor so that the recursion is always based on the order of categories.
- The recursion should always follow the order from the category combination, with no special cases.

---

## 6. Rationale

- This approach ensures a **predictable, metadata-driven UI** that is robust to any category combination, regardless of naming or option count.
- It eliminates the need for hardcoded logic for specific categories, making the codebase easier to maintain and extend.
- The UI will always match the structure defined in the DHIS2 metadata, providing a consistent experience for all datasets.

---

## 7. Migration Notes

- Remove all "sex/gender" and "two-option" logic from the codebase.
- Update all recursive rendering logic to use the new approach.
- Refer to this document for all future changes to the data entry UI rendering logic. 