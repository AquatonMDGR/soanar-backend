# Supabase Storage Setup Guide

## Issue
The file upload is failing because the Supabase Storage is not fully configured.

## Steps to Fix

### 1. Get Your Supabase Service Role Key

1. Go to your Supabase Dashboard: https://supabase.com/dashboard
2. Select your project: **Thesis** (zhmpxqstltaatcjxgvly)
3. Go to **Project Settings** (gear icon in the left sidebar)
4. Click on **API** in the left submenu
5. Scroll down to **Project API keys**
6. Find the **service_role** key (marked as "secret")
7. Click **Reveal** and copy the key

### 2. Create Storage Bucket

1. In your Supabase Dashboard, go to **Storage** (left sidebar)
2. Click **Create a new bucket**
3. Bucket name: `Announcement-Media-Bucket`
4. Set it to **Public** (so URLs are accessible)
5. Click **Create bucket**

### 3. Create Folder in Bucket

1. Click on the `Announcement-Media-Bucket` bucket
2. Click **Upload** → **Create folder**
3. Folder name: `Media-Files`
4. Click **Create**

### 4. Update .env File

Open `soanar-backend/.env` and replace the placeholder with your actual key:

```env
SUPABASE_SERVICE_ROLE_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...your-actual-key-here
```

### 5. Restart Backend

Stop the backend (Ctrl+C) and restart it:
```bash
cd soanar-backend
.\mvnw.cmd spring-boot:run
```

## Alternative: Skip File Upload (Temporary)

If you want to test without setting up storage, the backend is now configured to continue creating announcements even if file upload fails. The post will be created without an image.

## Verify Setup

After setup, when you upload a file, check the backend console for:
- `Uploading to Supabase Storage: https://zhmpxqstltaatcjxgvly.supabase.co/storage/v1/object/...`
- `Supabase response status: 200`
- `Upload successful, public URL: https://...`

If you see errors like "Bucket not found" or "401 Unauthorized", verify steps 1-3 above.
