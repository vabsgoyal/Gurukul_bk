# Gurukul_bk

A minimal Spring Boot backend service built with Maven and Java.

## Prerequisites

- Java 25+
- Maven, or the included Maven wrapper
- PostgreSQL, if running with database features enabled

## Setup

```bash
git clone https://github.com/vabsgoyal/Gurukul_bk.git
cd Gurukul_bk
```

Install dependencies and build the project:

```bash
./mvnw clean install
```

If Maven is installed globally, you can also use:

```bash
mvn clean install
```

## Run locally

```bash
# Start the Spring Boot application
./mvnw spring-boot:run
```

Or run the packaged jar:

```bash
./mvnw clean package
java -jar target/digital-school-backend-0.0.1-SNAPSHOT.jar
```

The application starts on:

```text
http://localhost:8080
```

## Database configuration

This project includes Spring Data JPA and the PostgreSQL driver. Add database settings in:

```text
src/main/resources/application.properties
```

Example:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/gurukul
spring.datasource.username=postgres
spring.datasource.password=your_password
spring.jpa.hibernate.ddl-auto=update
```

For a temporary local run without database auto-configuration:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
```

## Scripts

| Command | Description |
|---------|-------------|
| `./mvnw spring-boot:run` | Start the backend server |
| `./mvnw clean install` | Build, test, package, and install locally |
| `./mvnw clean install -DskipTests` | Build and install without running tests |
| `./mvnw test` | Run unit tests |
| `./mvnw clean package` | Build the executable jar |

## Project structure

```text
.
├── pom.xml
├── mvnw
├── mvnw.cmd
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/digitalschool/digital_school_backend/
│   │   │       └── DigitalSchoolBackendApplication.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/
│           └── com/digitalschool/digital_school_backend/
│               └── DigitalSchoolBackendApplicationTests.java
└── README.md
```

## Tech stack

- Spring Boot 4.1.0
- Java 25
- Maven
- Spring Web MVC
- Spring Security
- Spring Data JPA
- PostgreSQL Driver
- Bean Validation
- ActiveMQ
- Lombok

## License

MIT
