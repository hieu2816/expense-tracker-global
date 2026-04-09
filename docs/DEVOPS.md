# Expense Tracking — DevOps Technical Reference

> **Audience:** Developers, DevOps learners, technical leads
> **Last Updated:** 2026-04-09
> **Stack:** AWS EC2 · Ubuntu · Docker · Docker Compose · Nginx · Certbot · GitHub Actions · Git/JWT · PostgreSQL 16

---

## Table of Contents

1. [Scope](#scope)
2. [Current DevOps Architecture](#current-devops-architecture)
3. [Infrastructure Decisions](#infrastructure-decisions)
4. [Server Setup](#server-setup)
5. [Containerization](#containerization)
6. [Nginx & HTTPS](#nginx--https)
7. [DNS & Domain Routing](#dns--domain-routing)
8. [Security Model](#security-model)
9. [CI/CD Pipeline](#cicd-pipeline)
10. [Deployment Flow](#deployment-flow)
11. [Operational Tasks](#operational-tasks)
12. [Troubleshooting](#troubleshooting)
13. [Current Limitations](#current-limitations)
14. [Next Steps](#next-steps)

---

## Scope

This document covers the DevOps work completed for the Expense Tracker project:

- AWS EC2 server provisioning
- Ubuntu server setup
- Docker and Docker Compose deployment
- Nginx reverse proxy setup
- HTTPS certificates with Certbot / Let's Encrypt
- DNS routing for `spendwiser.me`
- Security Groups and network exposure
- GitHub Actions CI/CD deployment
- Swap configuration for low-memory instances
- Operational commands and troubleshooting

It is both a **deployment record** and a **learning reference**.

---

## Current DevOps Architecture

```mermaid
flowchart TB
    User[Browser] -->|HTTPS 443| DNS[DNS: spendwiser.me]
    DNS --> SG{AWS Security Group}
    SG --> Nginx[Nginx on EC2]
    Nginx -->|/| Frontend[React static files]
    Nginx -->|/api/*| Backend[Spring Boot container]
    Backend --> DB[(PostgreSQL container)]
    GitHub[GitHub Actions] -->|SSH deploy| EC2[Ubuntu EC2 instance]
    EC2 --> Docker[Docker Compose]
    Docker --> Nginx
    Docker --> Backend
    Docker --> DB
    Certbot[Certbot/Let's Encrypt] --> Nginx
```

### Service Layout

| Service | Role | Exposed | Notes |
|---------|------|---------|-------|
| Nginx | Reverse proxy, static hosting, HTTPS termination | Yes | Public entry point |
| Spring Boot | API backend | No | Internal only |
| PostgreSQL | Database | No | Internal only, persisted via volume |
| GitHub Actions | CI/CD automation | N/A | Triggers on push to `main` |

---

## Infrastructure Decisions

### Why AWS EC2?

| Choice | Why |
|--------|-----|
| **EC2** | Full control over the server, low cost, good learning value |
| **Ubuntu** | Familiar Linux distribution, strong package support |
| **t2.micro** | Cheap, enough for a small personal project, free-tier friendly |
| **Single instance** | Simpler than multi-node orchestration for current scale |

### Why Docker?

Docker solves the classic deployment problem:

- same app, different machine
- dependency drift
- manual setup on every server
- unclear runtime environment

Containers make the backend and database reproducible.

### Why Nginx?

Nginx handles the public edge:

- serves the frontend
- proxies API requests
- terminates HTTPS
- keeps backend private

### Why GitHub Actions?

GitHub Actions gives you automated deployment without needing a separate CI server.

---

## Server Setup

### Base Setup Steps

1. Launch EC2 instance
2. Select Ubuntu
3. Add Security Group rules
4. SSH into the instance
5. Update packages
6. Install Docker
7. Add the user to the docker group
8. Add swap for memory pressure

### Commands Used

```bash
sudo apt update && sudo apt upgrade -y
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

### Why Swap Was Needed

The instance is small, so Docker builds and Java processes can exceed available RAM. Swap prevents the system from killing processes during build or startup.

---

## Containerization

### Runtime Model

The app runs as three containers managed by Docker Compose:

- frontend container for Nginx and static files
- backend container for the Spring Boot API
- database container for PostgreSQL

### Why This Works Well

| Problem | Docker Solution |
|---------|-----------------|
| Manual service setup | Services defined in `docker-compose.yml` |
| Port conflicts | Isolated container ports |
| Dependency mismatch | Each service has its own image |
| Hard to restart | One command restarts the stack |

### Compose Responsibilities

- create the internal network
- provide service-name DNS between containers
- persist database data with volumes
- manage restart behavior

---

## Nginx & HTTPS

### Nginx Role

Nginx is the public-facing web server.

It:
- listens on ports 80 and 443
- redirects HTTP to HTTPS
- serves the React build output
- proxies `/api/*` to the backend container
- applies security headers

### HTTPS Setup

Certbot is used with Let's Encrypt to obtain and renew certificates.

Certificate files are mounted into Nginx from the host:

```bash
/etc/letsencrypt/live/spendwiser.me/fullchain.pem
/etc/letsencrypt/live/spendwiser.me/privkey.pem
```

### Why HTTPS Matters

- encrypts login and transaction data
- avoids browser security warnings
- required for secure public deployment
- improves trust for users

---

## DNS & Domain Routing

### Domain Setup

The domain `spendwiser.me` points to the EC2 public IP.

Records used:

| Record | Value | Purpose |
|--------|-------|---------|
| `A @` | EC2 public IP | Root domain |
| `A www` | EC2 public IP | `www` alias |

### Why DNS Was Required

Let's Encrypt needs a real domain. Users also need a stable, memorable URL instead of a raw IP address.

---

## Security Model

### Network Exposure

| Port | Exposure | Why |
|------|----------|-----|
| 22 | Restricted | SSH access for admin only |
| 80 | Open | HTTP redirect and certificate checks |
| 443 | Open | Public website traffic |
| 8080 | Closed | Backend only via Nginx |
| 5432 | Closed | Database only inside Docker network |

### Security Layers

1. AWS Security Groups block unwanted traffic
2. Nginx is the only public entry point
3. Backend and database are private
4. SSH is limited to trusted IPs
5. Certificates and secrets stay on the host

### Why This Design

The goal is to reduce attack surface while keeping deployment simple.

---

## CI/CD Pipeline

### Current Flow

```mermaid
flowchart LR
    A[Push to main] --> B[GitHub Actions]
    B --> C[Run tests]
    C --> D{Pass?}
    D -->|Yes| E[SSH into EC2]
    E --> F[Pull latest code]
    F --> G[Rebuild containers]
    G --> H[Restart stack]
    D -->|No| I[Fail workflow]
```

### GitHub Actions Workflow

The workflow:

- checks out the code
- runs build/tests
- connects to EC2 with SSH
- updates the deployment
- restarts the Docker Compose stack

### Secrets Used

| Secret | Purpose |
|--------|---------|
| `EC2_HOST` | Server address |
| `SSH_PRIVATE_KEY` | SSH authentication |

### Why CI/CD Helps

- reduces manual deploy errors
- gives repeatable deployments
- makes release process faster
- ensures code is tested before deployment

---

## Deployment Flow

### Manual Flow

1. Push code to GitHub
2. GitHub Actions runs
3. Tests pass
4. SSH into EC2
5. Pull new code
6. Rebuild containers
7. Restart stack

### What Happens on the Server

```bash
git pull
docker compose up -d --build
```

That is enough because the server already has:

- repository clone
- Docker installed
- environment variables
- certificates mounted
- domain and network configured

---

## Operational Tasks

### Common Commands

```bash
docker compose ps
docker compose logs -f
docker compose restart
docker system prune -af
free -h
df -h
sudo nginx -t
sudo certbot renew --dry-run
```

### What Each One Does

| Command | Purpose |
|---------|---------|
| `docker compose ps` | Check container status |
| `docker compose logs -f` | Follow application logs |
| `docker compose restart` | Restart services |
| `docker system prune -af` | Clean unused Docker resources |
| `free -h` | Check memory usage |
| `df -h` | Check disk usage |
| `nginx -t` | Validate Nginx config |
| `certbot renew --dry-run` | Test certificate renewal |

---

## Troubleshooting

### Common Issues

| Problem | Likely Cause | Fix |
|---------|--------------|-----|
| `502 Bad Gateway` | Backend down or wrong proxy target | Check backend container and `proxy_pass` |
| `Connection timeout` | Security Group or service not running | Check ports and service status |
| `Permission denied` on SSH | Wrong key permissions | `chmod 400 key.pem` |
| Low memory | t2.micro limits | Add swap or scale instance |
| SSL renewal fails | DNS/port 80 issue | Verify domain and open port 80 |

### Debug Order

1. Check containers
2. Check logs
3. Check ports
4. Check Security Groups
5. Check DNS
6. Check certificates

---

## Current Limitations

- single EC2 instance means one point of failure
- manual host maintenance is still required
- database is containerized instead of managed
- deployment can briefly interrupt service
- monitoring and alerting are minimal

---

## Next Steps

Recommended improvements:

1. Add monitoring and alerting
2. Add automated backups
3. Move secrets to a managed service
4. Introduce zero-downtime deployment strategy
5. Build a staging environment
6. Push images to a registry instead of building on server

---

## Related

- [[Projects/Expense Tracker/Overview|Overview]]
- [[Projects/Expense Tracker/Architecture|Architecture]]
- [[Projects/Expense Tracker/Deployment Guide|Deployment Guide]]
- [[DevOps Concepts/_Index|DevOps Knowledge Base]]
