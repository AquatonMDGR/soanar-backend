# SONAR Backend

Spring Boot backend for the SONAR announcement system.

## Quick Start

```bash
./mvnw spring-boot:run
```

Runs on http://localhost:8080

## Architecture

### Controllers
- **AuthController.js** - OAuth login and JWT generation
- **AnnouncementController.js** - Announcement CRUD and approval
- **NotificationController.js** - Notification management
- **AdminController.js** - Admin operations
- **DbController.js** - Health check endpoints
- **EmailController.js** - Email operations
- **SocialMediaController.js** - Social media integration (placeholder)

### Services
- **AuthService** - User authentication logic
- **AnnouncementService** - Announcement business logic
- **NotificationService** - Notification and email sending
- **EmailService** - SMTP email integration
- **UserService** - User management

### Repositories
- Data access layer using Spring Data JPA
- Custom queries with @Query annotations
- Ordered results (latest first)

### Models
- **User** - Student, OSAS, Academic, Student Organization, Super Admin roles
- **Announcement** - Title, description, status, distribution groups
- **Notification** - In-app notifications
- **Email** - Email delivery logs
- **DistributionGroup** - Group memberships
- **SocialMediaPage** - Social media integrations

## Environment Variables

Create `.env` file:
```
DB_URL=jdbc:postgresql://host:port/database?sslmode=require
DB_USER=postgres
DB_PASSWORD=password

GOOGLE_CLIENT_ID=client_id
GOOGLE_CLIENT_SECRET=client_secret

JWT_SECRET=256-bit-secret-in-base64

MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=email@gmail.com
MAIL_PASSWORD=app-specific-password
```

## Key Features

- Google OAuth 2.0 with @iacademy.edu.ph validation
- JWT authentication on all protected endpoints
- Role-based access control
- SMTP email integration (Gmail)
- Distribution group email targeting
- Database logging for emails
- Auto schema management with Hibernate
- HikariCP connection pooling to Supabase

## Authentication Flow

1. Frontend sends Google credential token to `POST /api/login`
2. Backend validates via Google's tokeninfo API
3. Backend checks @iacademy.edu.ph domain
4. Backend creates/retrieves user from database
5. Backend returns JWT token
6. JwtAuthenticationFilter validates token on every request

## API Endpoints

All documented in [../docs/API_REFERENCE.md](../docs/API_REFERENCE.md)

## Database

- PostgreSQL via Supabase
- 9 tables with auto schema management
- Seed data in [../scripts/seed-data.sql](../scripts/seed-data.sql)

For full documentation: [../docs/README.md](../docs/README.md)
