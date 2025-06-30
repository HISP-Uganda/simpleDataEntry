# Section Rendering Logic for Data Entry Screens

## Overview
This document defines the rules and rationale for rendering data entry sections in the Android app, ensuring consistency with the web interface and robust handling of all category combination scenarios.

---

## 1. Default Category Combination (Zero Categories)
- **Definition:** Data elements with a category combination of "default" (no categories).
- **Rendering:**
  - Render as a flat list.
  - Never render inside an accordion, even if mixed with other elements in the section.
  - If a section contains only default category elements, the entire section is a flat list.

## 2. Single Category (Non-default)
- **Examine the category options:**
  - **If the category is a gender category or has exactly two options:**
    - Render two fields side by side (e.g., Male/Female), with clear labels directly above each field.
  - **If the category has more than two options:**
    - Render a single accordion piece with the category name as the header.
    - Inside, render the data elements as a list (one per option).

## 3. Two Categories
- **Parsing:**
  - Check the number of options in each category.
  - **If either category has more than two options:**
    - The category with more options becomes the header for nested accordions (each option gets its own accordion).
    - The other category is checked:
      - If it has two options, render side by side fields for each pair.
      - If it has more than two, render as a list or grid as appropriate.
  - **If both categories have two options:**
    - Render as a grid: rows for one category, columns for the other, with clear headers.

## 4. Uniform Category Combination in Section
- **If all elements in a section share the same category combination:**
  - Apply the above logic once for the whole section (not per element).

## 5. Mixed Category Combinations in Section
- **If a section has elements with different category combinations:**
  - Individually check each element and render according to its own category combination:
    - Flat list for default (zero) category combination elements.
    - Accordions or grids for others, as per above rules.

---

## Examples

### Section B: Learner Enrolment (Grid)
- Two categories: "Class" (P1–P7) and "Sex (M/F)".
- Render as a grid: columns for each class/sex pair, rows for age groups.

### Section E: Number of Non-Citizen Learners
- Two categories: "Citizenship" (Refugees, Non-Refugees) and "Sex (M/F)".
- Render as a grid: columns for each citizenship/sex pair.

### Section K1: Sanitation Information
- Two categories: "Permanent/Temporary" and "Latrine Sex" (Male, Female, Mixed).
- Render as a grid: columns for each permanent/temporary and sex combination, rows for category.

### Section K, O1: Flat List
- Default (zero categories).
- Render as a flat list.

---

## Summary Table

| Category Combo Type         | Render As                | Accordion? | Example Section |
|----------------------------|--------------------------|------------|-----------------|
| Default (zero categories)  | Flat list                | No         | K, O1           |
| Single, 2 options          | Side-by-side fields      | No         | (M/F)           |
| Single, >2 options         | Accordion, list inside   | Yes        | (rare)          |
| Two categories, both 2 opt | Grid (rows/columns)      | No         | E               |
| Two categories, one >2 opt | Accordion per group,     | Yes        | K1              |
|                            | side-by-side/list inside |            |                 |
| Mixed combos in section    | Per-element logic        | As above   | (rare)          |

---

**This structure must be strictly followed in all future edits. Any deviation must be justified and documented.** 