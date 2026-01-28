# AGENTS.md - AI Coding Agent Guidelines

## Project Overview

Expense Tracking API - A Spring Boot REST API for personal finance management with JWT authentication, bank webhook integration (Casso), and transaction categorization.

**Tech Stack:** Java 21, Spring Boot 3.5.9, PostgreSQL 16, Maven, Flyway, Lombok, JWT

---

## Build & Run Commands

```bash
# Build (skip tests)
./mvnw clean package -DskipTests

# Build with tests
./mvnw clean package

# Run application
./mvnw spring-boot:run

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=ExpenseTrackingApplicationTests

# Run a single test method
./mvnw test -Dtest=ExpenseTrackingApplicationTests#contextLoads

# Run tests matching pattern
./mvnw test -Dtest="*Service*"

# Clean build artifacts
./mvnw clean
```

---

## Development Setup

```bash
# Start PostgreSQL database
docker-compose up -d

# Database: localhost:5432/postgres (user: postgres)
```

Flyway migrations run automatically on startup from `src/main/resources/db/migration/`.

---

## Project Structure

```
src/main/java/com/example/expense_tracking/
├── config/          # Spring configuration (Security, JWT filter, beans)
├── controller/      # REST controllers (@RestController)
├── dto/             # Data Transfer Objects (requests/responses)
├── entity/          # JPA entities (@Entity)
├── exception/       # Global exception handlers
├── repository/      # Spring Data JPA repositories
├── service/         # Business logic (@Service)
└── utils/           # Utility classes (JWT utils)

src/main/resources/
├── application.yaml           # App configuration
└── db/migration/              # Flyway SQL migrations (V1__, V2__, etc.)
```

---

## Code Style Guidelines

### Imports

- Group: java.* → jakarta.* → third-party → project imports
- Use wildcards for same-package DTOs/entities only

### Lombok Usage (Required)

```java
// Entities
@Entity
@Table(name = "table_name")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EntityName { }

// Services/Controllers
@Service
@RequiredArgsConstructor
public class ServiceName {
    private final DependencyRepository repository;  // Constructor injection
}

// DTOs
@Data
public class RequestDTO { }

@Data @Builder
public class ResponseDTO { }
```

### Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Entity | Singular PascalCase | `Transaction`, `User`, `BankConfig` |
| Repository | `{Entity}Repository` | `TransactionRepository` |
| Service | `{Domain}Service` | `TransactionService` |
| Controller | `{Domain}Controller` | `TransactionController` |
| DTO Request | `{Action}Request` | `LoginRequest`, `TransactionRequest` |
| DTO Response | `{Action}Response` | `LoginResponse`, `DashBoardResponse` |
| Enum | PascalCase, values UPPER_CASE | `TransactionType.IN` |

### Entity Pattern

```java
@Entity
@Table(name = "table_name")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EntityName {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "foreign_id", nullable = false)
    private OtherEntity relation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SomeEnum enumField;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
```

### Repository Pattern

```java
@Repository
public interface EntityRepository extends JpaRepository<Entity, Long> {
    Optional<Entity> findByField(String field);
    boolean existsByField(String field);

    @Query("SELECT e FROM Entity e WHERE e.user = :user AND (:filter IS NULL OR e.field = :filter)")
    Page<Entity> findFiltered(@Param("user") User user, @Param("filter") String filter, Pageable pageable);
}
```

### Service Pattern

```java
@Service
@RequiredArgsConstructor
public class DomainService {
    private final EntityRepository repository;

    public Entity create(RequestDTO request, User user) {
        if (invalidCondition) {
            throw new RuntimeException("Error message");
        }
        Entity entity = Entity.builder()
                .field(request.getField())
                .user(user)
                .build();
        return repository.save(entity);
    }
}
```

### Controller Pattern

```java
@RestController
@RequestMapping("/api/resource")
@RequiredArgsConstructor
public class ResourceController {
    private final ResourceService service;

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
}
```

### DTO Validation

```java
@Data
public class SomeRequest {
    @NotNull(message = "Field cannot be empty")
    private String requiredField;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    private BigDecimal amount;

    private String optionalField;  // No annotation = optional
}
```

### Error Handling

- Throw `RuntimeException` with descriptive messages for business errors
- `GlobalExceptionHandler` catches validation and runtime exceptions
- Always verify ownership: `entity.getUser().getId().equals(user.getId())`

```java
if (!entity.getUser().getId().equals(user.getId())) {
    throw new RuntimeException("You do not own this resource");
}
```

### Types

- `BigDecimal` for money (never float/double)
- `LocalDateTime` for timestamps
- `Long` for entity IDs
- Enums for fixed values (`TransactionType.IN`, `TransactionType.OUT`)

---

## Database Migrations

- Location: `src/main/resources/db/migration/`
- Naming: `V{version}__{description}.sql` (e.g., `V2__Add_new_table.sql`)
- Use snake_case for table/column names
- Add `ON DELETE CASCADE` for foreign keys where appropriate

---

## Testing

```java
@SpringBootTest
class SomeServiceTests {
    @Test
    void shouldDoSomething() {
        // Arrange, Act, Assert
    }
}
```

---

## Security

- JWT auth for all endpoints except `/api/auth/**` and `/api/webhook/**`
- Always verify resource ownership before update/delete
- Webhook endpoints validate secure tokens from request headers
