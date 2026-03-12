#!/usr/bin/env bash
# Post-create setup script for GitHub Codespaces
set -euo pipefail

# Make Gradle wrapper executable
chmod +x ./gradlew

# Create .env from example if it doesn't already exist
if [ ! -f .env ]; then
  cp .env.example .env
  echo "✅ Created .env from .env.example — open it and fill in your secrets before running the app."
else
  echo "ℹ️  .env already exists, skipping copy."
fi

echo ""
echo "══════════════════════════════════════════════════════"
echo " Next steps:"
echo "  1. Edit .env with your JWT_SECRET, SMTP, Cloudinary"
echo "  2. source .env (or: set -o allexport && source .env && set +o allexport)"
echo "  3. docker compose up -d   # start PostgreSQL"
echo "  4. ./gradlew bootRun      # start the Spring Boot app"
echo "══════════════════════════════════════════════════════"
