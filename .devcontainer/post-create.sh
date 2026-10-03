#!/usr/bin/env bash
# Post-create setup script for GitHub Codespaces
set -euo pipefail

echo "======================================================"
echo " Starting Codespaces post-creation setup..."
echo "======================================================"

# 1. Make Gradle wrapper executable
chmod +x ./gradlew

# 2. Automatically install Antigravity CLI (agy)
echo "⠋ Installing Antigravity CLI (agy)..."
mkdir -p "$HOME/.local/bin"
if curl -fsSL "https://storage.googleapis.com/antigravity-public/antigravity-cli/1.2.14-4571742832820224/linux-x64/cli_linux_x64.tar.gz" | tar -xz -O antigravity > "$HOME/.local/bin/agy"; then
  chmod +x "$HOME/.local/bin/agy"
  echo 'export PATH="$HOME/.local/bin:$PATH"' >> "$HOME/.bashrc"
  echo 'export PATH="$HOME/.local/bin:$PATH"' >> "$HOME/.zshrc" 2>/dev/null || true
  echo "✓ Antigravity CLI (agy) installed successfully."
else
  echo "⚠️ Warning: Failed to download agy. You can install it manually later."
fi

# 3. Create .env from template if it doesn't already exist
if [ ! -f .env ]; then
  cp .env.example .env
  echo "✓ Created .env template from .env.example"
else
  echo "ℹ️  .env already exists, skipping copy."
fi

echo ""
echo "══════════════════════════════════════════════════════"
echo " 🎉 Environment setup complete!"
echo ""
echo " Next steps:"
echo "  1. Fill in your secrets in .env (JWT, Cloudinary, etc.)"
echo "  2. Start PostgreSQL:  docker compose up -d"
echo "  3. Run Spring Boot:   ./gradlew bootRun"
echo ""
echo " To use Antigravity CLI (agy) in Codespaces:"
echo "  - Paste your auth token to ~/.gemini/antigravity-cli/antigravity-oauth-token"
echo "  - Then run:           agy"
echo "══════════════════════════════════════════════════════"
