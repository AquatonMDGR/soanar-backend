# Quick Reference - Supabase CLI Commands

## After Installation ✅

All commands work from `soanar-backend` directory using:
```bash
npm run supabase:<command>
# OR
npx supabase <command>
```

---

## Essential Commands

### Check Projects
```bash
npm run supabase:projects
```
Shows your "Thesis" project in Singapore region (WORKING ✅)

### Check CLI Version
```bash
npx supabase --version
# Output: 2.72.7
```

### List Migrations
```bash
npm run supabase:migrations-list
```

### View All Available Commands
```bash
npm run supabase:help
```

---

## Database Connection Info

**From .env file:**
- URL: `jdbc:postgresql://aws-1-ap-southeast-1.pooler.supabase.com:5432/postgres`
- User: `postgres.zhmpxqstltaatcjxgvly`
- Password: (in .env file)

**Status:** ✅ Connected and working

---

## Supabase Dashboard Access

Visit: https://supabase.com/dashboard

Select "Thesis" project to:
- View tables and data
- Run SQL queries
- Manage users
- Configure settings

---

## Backend Commands

```bash
# Build the project
mvn clean package -DskipTests

# Run the backend (from soanar-backend/)
mvn spring-boot:run

# Or run the compiled JAR
java -jar target/thesis-0.0.1-SNAPSHOT.jar
```

Backend runs on: http://localhost:8080

---

## Important Notes

✅ **Supabase CLI is now fully installed and working**
✅ **Database authentication is configured**
✅ **Your project is linked and ready to use**

⚠️ **Docker is optional** - Only needed for local `supabase start`
⚠️ **Your remote database works without Docker**

---

**Last Updated:** January 17, 2026
