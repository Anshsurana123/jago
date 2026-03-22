# Manual Test Guide: Contact Resolution Upgrade

## Setup
1.  **Grant Permissions**: Ensure Jago has `Contacts` and `Phone` permissions.
2.  **Create Contacts**: Add the following dummy contacts to your phone:
    - `Mummy`
    - `Rishabh`
    - `Rishabh ki Mummy`
    - `Rahul Sharma`
    - `Rahul Verma`

## Test Cases

### 1. Exact Match Priority
- **Say**: "Hey Jago, Call Mummy"
- **Expected**: Calls "Mummy".
- **Verify**: Does NOT call "Rishabh ki Mummy".

### 2. Starts With Match
- **Say**: "Hey Jago, Call Rahul"
- **Expected**:
    - If "Rahul" (exact) exists -> Calls "Rahul".
    - If only "Rahul Sharma" and "Rahul Verma" exist -> Says "I found multiple contacts: Rahul Sharma or Rahul Verma...".

### 3. Contains Match
- **Say**: "Hey Jago, Call Sharma"
- **Expected**: Calls "Rahul Sharma" (if unique).

### 4. Fuzzy Match (Typo Tolerance)
- **Say**: "Hey Jago, Call Mammy" (intentional slight mispronunciation)
- **Expected**: Calls "Mummy" (Levenshtein distance is small).

### 5. Ambiguity Handling
- **Say**: "Hey Jago, Call Rishabh"
- **Expected**:
    - If "Rishabh" and "Rishabh ki Mummy" both exist (and "Rishabh" is exact match) -> Calls "Rishabh".
    - If you delete "Rishabh" contact and say "Call Rishabh" -> Says "I found multiple contacts..." or calls "Rishabh ki Mummy" depending on logic.
    - Actually, "Rishabh" starts with "Rishabh", so "Rishabh ki Mummy" is a StartsWith match. If multiple StartsWith matches exist, it should report ambiguity.

### 6. Permission Fallback
- **Action**: Go to Settings -> Apps -> Jago -> Permissions -> Deny "Phone" (Call) permission.
- **Say**: "Hey Jago, Call Mummy"
- **Expected**: Says "Permission denied. Opening dialer." and opens the dialer with the number pre-filled.
