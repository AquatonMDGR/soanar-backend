# Analysis: Backend Configuration Issues

**Date**: January 29, 2026, 7:50 PM  
**Status**: Critical - Both frontend and backend DOWN

---

## Current State

### Backend
- **2 Java processes running** (PIDs 18468, 24220) but **port 8080 NOT listening** ❌
- Startup failed - processes in failed state

### Frontend  
- **No Node processes** - completely stopped ❌
- Port 3000 not listening

---

## Root Cause

Backend won't start because **port 6543 (Transaction mode) is fundamentally incompatible with JPA/Hibernate**:

1. **Transaction pooling doesn't support prepared statements** - each transaction gets a different backend connection, but prepared statements are session-scoped
2. **Missing query mode parameters** - no `preferQueryMode=simple` to force simple query protocol
3. **Configuration mismatch** - .env uses port 6543 but application.properties recommends port 5432

---

## Issue 1: Supabase URL Format

**Current State:**
```env
SUPABASE_URL=https://zhmpxqstltaatcjxgvly.storage.co
```

**Root Cause:**
- URL is missing the `.supabase` subdomain
- Has `.storage.co` instead of `.supabase.co`
- The Storage API requires the base project URL, not a storage-specific subdomain

**Symptoms:**
- Image upload fails with 400 Bad Request
- "Unsupported authorization type" or "InvalidSignature" errors from Supabase Storage API

**Fix Required:**
```env
SUPABASE_URL=https://zhmpxqstltaatcjxgvly.supabase.co
```

---

## Issue 2: PostgreSQL Prepared Statement Error

**Current State:**
```env
DB_URL=jdbc:postgresql://aws-1-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require
```

**Root Cause:**
- Session mode pooling (port 5432) requires `prepareThreshold=0` parameter to prevent prepared statement caching issues
- Without this parameter, Hibernate generates "prepared statement 'S_X' already exists" errors on transaction commits

**Symptoms:**
```
org.postgresql.util.PSQLException: ERROR: prepared statement "S_9" already exists
org.springframework.jdbc.BadSqlGrammarException: Hibernate transaction: Unable to commit against JDBC Connection
```

**Fix Required:**
```env
DB_URL=jdbc:postgresql://aws-1-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require&prepareThreshold=0
```

---

## Technical Details

### Supabase URL Structure
- Project URL: `https://{PROJECT_ID}.supabase.co`
- Storage API: `https://{PROJECT_ID}.supabase.co/storage/v1/object/...`
- Public Storage: `https://{PROJECT_ID}.supabase.co/storage/v1/object/public/...`

The Java code constructs the full Storage API path automatically:
```java
String uploadUrl = supabaseUrl + "/storage/v1/object/" + bucketName + "/" + fullPath;
```

### PostgreSQL Connection Pooling

Supabase offers two pooling modes:

1. **Session Mode (Port 5432)**
   - Supports all PostgreSQL features including prepared statements
   - Requires `prepareThreshold=0` with JPA/Hibernate to disable client-side prepared statement caching
   - Recommended for JPA applications

2. **Transaction Mode (Port 6543)**
   - Lightweight pooling but doesn't support prepared statements
   - Causes errors with JPA/Hibernate
   - Should be avoided for Spring Boot applications

---

## Action Items

1. ✅ Update SUPABASE_URL to correct format
2. ✅ Add prepareThreshold=0 to DB_URL
3. ⏳ Restart backend server
4. ⏳ Test image upload functionality
5. ⏳ Verify no prepared statement errors in logs

---

## References

- [Supabase Storage API Documentation](https://supabase.com/docs/guides/storage)
- [PostgreSQL prepareThreshold Parameter](https://jdbc.postgresql.org/documentation/server-prepare/)
- [Supabase Connection Pooling](https://supabase.com/docs/guides/database/connecting-to-postgres#connection-pooler)
