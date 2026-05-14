# AGENTS.md - AI Coding Agent Guidelines

## Project Overview
Expense Tracking Global — a personal finance REST API with JWT auth, Open Banking (Plaid), and React SPA.

**Tech Stack:** Java 21, Spring Boot 3.5.9, PostgreSQL 16, Maven, Flyway, Lombok, JWT · React 19, Vite 7, Ant Design 6

## Build & Test Commands

```bash
./mvnw clean package -DskipTests    # Build without tests
./mvnw clean package                # Build with tests
./mvnw spring-boot:run              # Run application
./mvnw test                         # Run all tests
./mvnw test -Dtest=ClassName        # Run single test class
./mvnw test -Dtest=ClassName#method # Run single test method
./mvnw test -Dtest="*Pattern*"      # Run tests matching pattern
./mvnw clean                        # Clean build artifacts
```

## Development Setup

```bash
# Start PostgreSQL via Docker Compose (host port 5433 → container port 5432)
docker-compose up -d

# Run backend (requires DB_USERNAME, DB_PASSWORD env vars — see .env.example)
./mvnw spring-boot:run

# Run frontend (port 5173, proxies /api to localhost:8080)
cd frontend && npm run dev
```
Flyway migrations auto-run on startup from `src/main/resources/db/migration/`.

## Project Structure

```
src/main/java/com/example/expense_tracking/
├── config/       # Security, JWT filter, beans
├── controller/   # REST controllers
├── dto/          # Request/Response DTOs
├── entity/       # JPA entities
├── exception/    # Global exception handler
├── repository/   # Spring Data JPA repositories
├── service/      # Business logic
└── utils/        # Utilities (JWT)

src/main/resources/
├── application.yaml
└── db/migration/   # Flyway migrations (V1__, V2__, etc.)
```

## Code Style

### Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Entity | Singular PascalCase | `Transaction`, `BankConfig` |
| Repository | `{Entity}Repository` | `TransactionRepository` |
| Service | `{Domain}Service` | `TransactionService` |
| Controller | `{Domain}Controller` | `TransactionController` |
| Request DTO | `{Action}Request` | `LoginRequest` |
| Response DTO | `{Action}Response` | `DashBoardResponse` |
| Enum | PascalCase, UPPER_CASE values | `TransactionType.IN` |

### Imports
- Group: `java.*` -> `jakarta.*` -> third-party -> project imports
- Wildcards allowed for same-package DTOs/entities only

### Types
- `BigDecimal` for money (never float/double)
- `LocalDateTime` for timestamps
- `Long` for entity IDs
- Enums for fixed values

## Lombok Patterns

```java
// Entity
@Entity @Table(name = "table_name")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EntityName {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private TransactionType type;

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}

// Service - constructor injection via @RequiredArgsConstructor
@Service @RequiredArgsConstructor
public class DomainService {
    private final EntityRepository repository;
    private final OtherService otherService;
}

// Controller
@RestController @RequestMapping("/api/resource") @RequiredArgsConstructor
public class ResourceController {
    private final ResourceService service;
}

// DTOs
@Data
public class SomeRequest { }

@Data @Builder
public class SomeResponse { }
```

## Common Patterns

### Get Current User
```java
// Preferred: Spring injects the User from the SecurityContext automatically
@GetMapping("/profile")
public ResponseEntity<UserProfileResponse> getProfile(@AuthenticationPrincipal User user) {
    return ResponseEntity.ok(userService.getProfile(user));
}
```

### Ownership Verification (Required for update/delete)
```java
if (!entity.getUser().getId().equals(user.getId())) {
    throw new ForbiddenException("You do not own this resource");
}
```

### DTO Validation
```java
@Data
public class SomeRequest {
    @NotNull(message = "Field required")
    private String requiredField;

    @NotNull @Positive(message = "Must be > 0")
    private BigDecimal amount;

    private String optionalField;  // No annotation = optional
}
```

### Repository with Custom Query
```java
@Repository
public interface EntityRepository extends JpaRepository<Entity, Long> {
    Optional<Entity> findByField(String field);
    boolean existsByField(String field);

    @Query("SELECT e FROM Entity e WHERE e.user = :user AND (:filter IS NULL OR e.field = :filter)")
    Page<Entity> findFiltered(@Param("user") User user, @Param("filter") String filter, Pageable pageable);
}
```

### Service Method
```java
public Entity create(RequestDTO request, User user) {
    if (invalidCondition) {
        throw new RuntimeException("Descriptive error message");
    }
    Entity entity = Entity.builder()
            .field(request.getField())
            .user(user)
            .build();
    return repository.save(entity);
}
```

### Controller Endpoint
```java
@PostMapping
public ResponseEntity<Resource> create(@Valid @RequestBody RequestDTO request) {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return ResponseEntity.ok(service.create(request, user));
}

@GetMapping
public ResponseEntity<Page<ResponseDTO>> getAll(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size) {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return ResponseEntity.ok(service.getAll(user, page, size));
}
```

## Error Handling
- Use custom exceptions (`BadRequestException`, `ForbiddenException`, `ResourceNotFoundException`, `ConflictException`) with their correct HTTP status codes
- `GlobalExceptionHandler` catches and formats validation and business exceptions
- Always verify resource ownership before modifications

## Database Migrations
- Location: `src/main/resources/db/migration/`
- Naming: `V{version}__{description}.sql` (double underscore)
- Current migrations: V1 (init), V2 (GoCardless), V3 (adjustments), V4 (link reference UUID), V5 (category unique constraint), V6 (Plaid), V7 (Plaid Naming)
- Use snake_case for table/column names
- JPA is `ddl-auto: validate` — Flyway owns the schema

## Security
- JWT auth required for all endpoints except `/api/auth/**` and `/api/banks/callback`
- Plaid webhook callback is public — used for background transaction syncing.
- Always verify resource ownership before update/delete operations
- `@AuthenticationPrincipal` is the preferred way to get the current user

## Testing
```java
@SpringBootTest
class ServiceTests {
    @Test
    void shouldDoSomething() {
        // Arrange, Act, Assert
    }
}
```
