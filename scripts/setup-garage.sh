#!/bin/bash
set -e

echo "=== [Garage S3] Khởi chạy và cấu hình Garage qua Docker Compose ==="

# Khởi động dịch vụ Garage và Garage-init
docker compose up -d --build garage garage-init

echo "Chờ dịch vụ Garage sẵn sàng..."
sleep 2

echo "Kiểm tra trạng thái cụm Garage:"
docker exec locket-clone-garage /garage -c /etc/garage.toml status || true

echo "Kiểm tra danh sách bucket:"
docker exec locket-clone-garage /garage -c /etc/garage.toml bucket list || true

echo "=== Garage S3 đã sẵn sàng! ==="
echo "- S3 API Endpoint: http://localhost:3900"
echo "- S3 Web CDN:      http://localhost:3902/locket-photos/"
echo "- Admin API:       http://localhost:3903"
