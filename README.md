# GitHub Repository Activity Connector

A Spring Boot application that connects to the GitHub API to retrieve repository and commit activity for any public GitHub user or organization.

## Features

- **GitHub API Integration**: Authenticates using a personal access token to access the GitHub REST API
- **Repository Listing**: Retrieves all public repositories for a specified GitHub user or organization
- **Commit History**: Fetches the 20 most recent commits for each repository
- **Rate Limit Handling**: Implements exponential backoff and respects GitHub API rate limits
- **Comprehensive REST API**: Provides endpoints for fetching activity, summaries, and repository-specific data
- **Error Handling**: Gracefully handles various error conditions (rate limits, empty repositories, etc.)
- **Validation**: Input validation for GitHub usernames and other parameters
- **Monitoring**: Health check endpoint and Spring Boot Actuator integration
- **Comprehensive Testing**: Includes both unit and integration tests

## Prerequisites

- Java 17 or higher
- Gradle 7.0+ (or use the included Gradle wrapper)
- GitHub Personal Access Token with `repo` scope

## Setup

### 1. Clone the repository

```bash
git clone https://github.com/iamanandalok/github-connector.git
cd github-connector
```

### 2. Configure GitHub token

Set your GitHub token as an environment variable:

```bash
export GITHUB_TOKEN=your_github_personal_access_token
```

Alternatively, you can modify `application.yml` to include your token, but this is not recommended for security reasons.

### 3. Build the application

```bash
./gradlew clean build
```

### 4. Run the application

```bash
./gradlew bootRun
```

Or run the JAR file directly:

```bash
java -jar build/libs/github-connector-0.0.1-SNAPSHOT.jar
```

The application will start on port 8080 by default.

## API Documentation

### Base URL

```
http://localhost:8080
```

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/github/{userOrOrg}` | Fetch activity for all repositories |
| GET | `/api/github/{userOrOrg}/quick` | Quick test: fetch only first repository for fast testing |
| GET | `/api/github/{userOrOrg}/summary` | Get metadata summary only |
| GET | `/api/github/{userOrOrg}/{repoName}` | Get commits for a specific repository |
| POST | `/api/github/{userOrOrg}/refresh` | Force refresh cached data |
| GET | `/api/github/health` | Health check endpoint |
| GET | `/api/github/status` | Check current GitHub API rate limit status |
| GET | `/api/github/test-token` | Validate GitHub token configuration |
| GET | `/actuator/health` | Spring Boot health status |

### Response Format

#### GET `/api/github/{userOrOrg}`

```json
{
  "meta": {
    "totalRepos": 42,
    "totalCommits": 840,
    "fetchedAtIso": "2023-05-29T14:22:18.013Z"
  },
  "data": [
    {
      "repositoryName": "example-repo",
      "commits": [
        {
          "message": "Fix authentication bug",
          "author": "John Doe",
          "timestamp": "2023-05-27T19:53:01Z"
        },
        // More commits...
      ]
    },
    // More repositories...
  ]
}
```

#### GET `/api/github/{userOrOrg}/summary`

```json
{
  "totalRepos": 42,
  "totalCommits": 840,
  "fetchedAtIso": "2023-05-29T14:22:18.013Z"
}
```

#### GET `/api/github/{userOrOrg}/{repoName}`

```json
[
  {
    "message": "Fix authentication bug",
    "author": "John Doe",
    "timestamp": "2023-05-27T19:53:01Z"
  },
  // More commits...
]
```

## Error Handling

The API returns appropriate HTTP status codes:

- `200 OK`: Request successful
- `400 Bad Request`: Invalid input parameters
- `404 Not Found`: Resource not found
- `429 Too Many Requests`: GitHub API rate limit exceeded
- `500 Internal Server Error`: Server-side error

## Configuration Options

The application can be configured via `application.yml`:

```yaml
github:
  token: ${GITHUB_TOKEN:}           # Personal access token from env var
  api-base-url: https://api.github.com
  repos-page-size: 100              # Repositories per page
  commits-page-size: 20             # Commits per repository

  # Performance / rate-limit controls
  # Note: Current values are optimized for fast testing
  max-repos: 5                      # Max repositories processed per request (reduced from 20)
  max-wait-time-ms: 30000           # Max time to wait on a single rate-limit (30s, reduced from 120s)
  request-timeout-ms: 60000         # Overall timeout for a user/org request (1min, reduced from 5min)

server:
  port: 8080                        # Application port

logging:
  level:
    root: INFO
    com.github_connector: DEBUG     # Package-specific logging
```


These changes will prioritize completeness over speed for production use.

## Examples

### Curl Examples

Fetch activity for a user:
```bash
curl http://localhost:8080/api/github/octocat
```

Quick test with just one repository:
```bash
curl http://localhost:8080/api/github/octocat/quick
```

Get repository-specific commits:
```bash
curl http://localhost:8080/api/github/octocat/Hello-World
```

Force refresh data:
```bash
curl -X POST http://localhost:8080/api/github/octocat/refresh
```



## Acknowledgments

- [Spring Boot](https://spring.io/projects/spring-boot)
- [GitHub REST API](https://docs.github.com/en/rest)
