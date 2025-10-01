##############################################
# 마지막 수정: 250929
# 작성자: gooraeng
#
# terraform 으로 EC2 생성 시 user_data 로 쓰일 스크립트
# 변수는 terraform 에서 치환됨
##############################################

#!/bin/bash
# EC2 에서 쓰일 스크립트

# 가상 메모리 4GB 설정 (128M * 32)
if ! grep -q "/swapfile" /etc/fstab; then
  dd if=/dev/zero of=/swapfile bs=128M count=32
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  sh -c echo "/swapfile swap swap defaults 0 0" >> /etc/fstab
fi

# 타임존 설정
timedatectl set-timezone ${timezone}

# 환경변수 세팅(/etc/environment)
echo "PASSWORD_1=${password}" >> /etc/environment
echo "APP_BACK_DOMAIN=api.${base_domain}" >> /etc/environment
echo "GITHUB_TOKEN_OWNER=${github_token_owner}" >> /etc/environment
echo "GITHUB_TOKEN=${github_token}" >> /etc/environment

source /etc/environment

# 도커 설치 및 실행/활성화
yum install docker -y
systemctl enable docker
systemctl start docker

# 도커 네트워크 생성
docker network create common

# Nginx Proxy Manager 설치
docker run -d \
--name npm_1 \
--restart unless-stopped \
--network common \
-p 80:80 \
-p 443:443 \
-p 81:81 \
-e TZ=${timezone} \
-e INITIAL_ADMIN_EMAIL=${nginx_admin_email} \
-e INITIAL_ADMIN_PASSWORD=${password} \
-v /dockerProjects/npm_1/volumes/data:/data \
-v /dockerProjects/npm_1/volumes/etc/letsencrypt:/etc/letsencrypt \
jc21/nginx-proxy-manager:latest

# redis
docker run -d \
--name=redis_1 \
--restart unless-stopped \
--network common \
-p 6379:6379 \
-e TZ=${timezone} \
-v /dockerProjects/redis_1/volumes/data:/data \
redis --requirepass ${password}

echo "${github_token}" | docker login ghcr.io -u ${github_token_owner} --password-stdin