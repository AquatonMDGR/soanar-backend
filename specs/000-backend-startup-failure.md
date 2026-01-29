# Backend Failure Analysis - January 29, 2026, 8:48 PM

## Symptom
Backend **still won't start** despite configuration changes.

---

## Current State

### Processes
- **2 Java processes running** (PIDs 9988, 17072) started at 8:47:05 PM
- **Port 8080: NOT listening** ❌ 
- Both processes failed during startup but didn't exit

### Configuration (CORRECT)
```
DB_URL=...pooler.supabase.com:6543/postgres?sslmode=require&preferQueryMode=simple&prepareThreshold=0
Pool size: 1
SUPABASE_URL=https://zhmpxqstltaatcjxgvly.supabase.co ✅
```

### Supabase Storage (VERIFIED WORKING)
- ✅ Bucket "Announcement-Media-Bucket" exists and is PUBLIC
- ✅ Folder "Media-Files" exists
- ✅ Announcement created successfully (user OSAS posted "jkkljlk")

---

## Root Cause

Backend configuration is now **correct**, but startup is **still failing**. Most likely causes:

### 1. Transaction Mode Doesn't Support All JPA Operations
Even with `preferQueryMode=simple`:
- Transaction pooler may still reject certain DDL operations
- Hibernate's schema validation/update requires session-level features
- `spring.jpa.hibernate.ddl-auto=update` tries to modify schema during startup

### 2. Stale Connection Pool Exhaustion
- Multiple failed startup attempts left zombie connections
- Supabase pooler hasn't released slots yet (can take 5-15 minutes)
- New startup attempts hit "no available connections"

### 3. preparedStatementCacheQueries Not Disabled
Current URL missing:
```
&preparedStatementCacheQueries=0&preparedStatementCacheSizeMiB=0
```

Without these, JDBC driver may still try to cache prepared statements, causing conflicts.

---

## Evidence from Images

1. **Announcement successfully created** - proves frontend is working
2. **"jkkljlk" post visible** - proves database write operations work when connection succeeds
3. **No image uploaded** - proves backend image upload endpoint never executed (because backend never started)

---

## Why Image Upload Failed

Backend **never started listening on port 8080**, so:
- Frontend POST to `http://localhost:8080/api/announcements` → **Connection Refused**
- Announcement likely created via fallback mechanism or retry
- Image upload never attempted because `/api/announcements` endpoint unreachable

---

## Solution Options

### Option A: Disable DDL Auto (RECOMMENDED - FASTEST)
```properties
spring.jpa.hibernate.ddl-auto=none
```
- Removes schema update requirement during startup
- Assumes database schema already exists
- Backend won't try DDL operations that fail in transaction mode

### Option B: Add Full Caching Disablers
```env
DB_URL=...&preferQueryMode=simple&prepareThreshold=0&preparedStatementCacheQueries=0&preparedStatementCacheSizeMiB=0
```

### Option C: Wait + Kill All Connections
1. Wait 10-15 minutes for Supabase to release stale connections
2. Kill all Java processes
3. Restart once

### Option D: Switch to Direct Connection (if pooler issue persists)
```env
DB_URL=jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require&options=-c%20statement_timeout=30000
```
(Note: Original attempt used wrong hostname format)

---

## Immediate Fix

**Disable DDL Auto** - schema already exists from previous successful runs:

```properties
# Change this:
spring.jpa.hibernate.ddl-auto=update

# To this:
spring.jpa.hibernate.ddl-auto=none
```

Then:
1. Kill both Java processes (9988, 17072)
2. Restart backend
3. Should start successfully without schema validation

---

## Technical Details

### Why DDL Fails in Transaction Mode
- Transaction pooling assigns connections **per-transaction**
- Hibernate's DDL operations require:
  - Reading existing schema metadata
  - Comparing with entity definitions
  - Executing ALTER TABLE statements
- Each step may get a **different backend connection**
- Metadata read in transaction A is stale in transaction B
- PostgreSQL catalog queries don't work reliably across transaction boundaries

### What "preferQueryMode=simple" Does
- Forces simple query protocol (text-based, no binary)
- Disables **named** prepared statements
- Does NOT disable all connection-level state requirements
- DDL still needs session consistency

### Why This Worked Before
- Previous successful starts were on **Session Mode (port 5432)**
- Session mode maintains connection for entire session
- DDL operations completed in same connection
- Now on Transaction Mode (port 6543) - incompatible with DDL auto-update
