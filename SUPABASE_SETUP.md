# Supabase CLI Setup and Usage Guide

## ✅ Installation Complete

The Supabase CLI has been installed as a local dev dependency in this project. You can now use Supabase commands without needing to install it globally.

---

## 🚀 Quick Commands

### Check Supabase Projects
```bash
npm run supabase:projects
```
Shows all linked Supabase projects. Your project:
- **Name:** Thesis
- **Reference ID:** zhmpxqstltaatcjxgvly
- **Region:** Southeast Asia (Singapore)

### View Supabase Status
```bash
npm run supabase:status
```
Check the status of your Supabase local services (requires Docker).

### List Database Migrations
```bash
npm run supabase:migrations-list
```
View all migrations in your project.

### Get Help
```bash
npm run supabase:help
```
Display all available Supabase commands.

---

## 📋 Database Connection

Your backend is configured to connect to the **remote Supabase project** with:
- **Database:** PostgreSQL (hosted on Supabase)
- **Pool Mode:** Transaction (for better connection management)
- **Host:** aws-1-ap-southeast-1.pooler.supabase.com
- **Port:** 5432
- **Database:** postgres

Credentials are loaded from `.env` file:
```
DB_URL=jdbc:postgresql://aws-1-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require
DB_USER=postgres.zhmpxqstltaatcjxgvly
DB_PASSWORD=H7esw7SpCOQlp3wX
```

---

## 🐳 Docker (Optional for Local Development)

Some Supabase CLI commands require Docker (like `supabase start`, `db pull`, `db push`).

### Install Docker
- **Windows:** [Docker Desktop](https://www.docker.com/products/docker-desktop)
- **Mac:** [Docker Desktop](https://www.docker.com/products/docker-desktop)
- **Linux:** [Docker Engine](https://docs.docker.com/engine/install/)

### Start Supabase Locally (requires Docker)
```bash
npm run supabase:status
```
If Docker is running, this will show local service status.

---

## 🔐 Authentication

The Supabase CLI is already authenticated via your Supabase access token. This token is stored in:
```
~/.supabase/config.toml
```

### Re-authenticate (if needed)
```bash
npx supabase login
```

---

## 🗄️ Database Operations

### Pull Remote Schema
Requires Docker to be running:
```bash
npm run supabase:db-pull
```
This creates local migration files from your remote database.

### Push Local Migrations
Requires Docker to be running:
```bash
npm run supabase:db-push
```
This applies migrations to your remote database.

---

## 📝 Common Tasks

### 1. Check Database Connection from Spring Boot
Run the backend:
```bash
./mvnw spring-boot:run
```
If the backend starts successfully without connection errors, your database connection is working!

### 2. View Database Tables
Use Supabase Studio at https://supabase.com/dashboard
- Select your project "Thesis"
- Go to SQL Editor or Table Editor
- View your tables and data

### 3. Run Database Migrations
From your Supabase Dashboard:
1. Go to SQL Editor
2. Create or paste SQL migration
3. Execute the query

---

## ✨ Next Steps

1. **Verify Backend Connection:** `./mvnw spring-boot:run`
2. **Check Database:** Visit Supabase dashboard
3. **Run Seeds:** Execute `seed-data.sql` in Supabase SQL Editor
4. **Install Docker** (optional): For advanced local development features

---

## 🆘 Troubleshooting

### "Docker daemon is not running"
- This is only needed for local development features (`db pull`, `db push`, `supabase start`)
- Your **remote Supabase project is already working** without Docker
- To use Docker features, install Docker Desktop and ensure it's running

### "supabase command not found"
- Use `npx supabase` or `npm run supabase:*` commands from this directory
- The CLI is installed locally, not globally

### "Authentication failed"
- Run `npx supabase login` to re-authenticate
- Make sure you have a valid Supabase account at https://supabase.com

### "Connection refused" from Spring Boot
- Verify `.env` file has correct credentials
- Check network connectivity to Supabase servers
- Verify IP is not blocked by Supabase network restrictions

---

## 📚 Resources

- **Supabase CLI Documentation:** https://supabase.com/docs/guides/cli
- **Supabase Dashboard:** https://supabase.com/dashboard
- **PostgreSQL Documentation:** https://www.postgresql.org/docs/
- **Spring Boot Database Guide:** https://spring.io/guides/gs/relational-data-access/

---

**Last Updated:** January 17, 2026
