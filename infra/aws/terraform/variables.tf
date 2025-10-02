#########################
# 마지막 수정: 250929
# 작성자: gooraeng
#
# 변수 정의 파일
#########################

##################
# Common
##################
variable "prefix" {
  description = "Prefix for all resources"
  type        = string
  default     = "relife"
}

variable "default_password" {
  description = "서비스 기본 비밀번호"
  sensitive   = true
}

variable "base_domain" {
  description = "backend domain"
  type        = string
  # 25.09.29 개발 목적으로 설정
  # 추후 배포용으로 변경 예정
  default     = "relife.kr"
}

# fixme: CDN 도메인 변수
# variable "cdn_domain" {
#   description = "cdn domain"
#   type        = string
#   default = "cdn.gooraeng.xyz"
# }

variable "encryption_type" {
  description = "S3 암호화 타입 (AES256, aws:kms)"
  type        = string
  default     = "AES256"  # 프리티어에서는 AES256 권장

  validation {
    condition     = contains(["AES256"], var.encryption_type)
    error_message = "encryption_type은 AES256만 가능합니다."
  }
}

variable "timezone" {
  description = "컨테이너 Timezone"
  type        = string
  default     = "Asia/Seoul"
}

##################
# Github
##################
variable "github_token_owner" {
  description = "Github Access Token 소유자"
  type        = string
  sensitive   = true
}

variable "github_token" {
  description = "Github Access Token, read:packages only 속성"
  type        = string
  sensitive   = true
}

##################
# EC2
##################
variable "region" {
  description = "AWS 리전"
  sensitive   = true
}

##################
# S3
##################
variable "s3_bucket_name" {
  description = "S3 버킷 이름"
  sensitive   = true
}

##################
# RDS
##################
variable "db_name" {
  description = "RDS DB 이름"
  sensitive   = true
}

variable "db_port" {
  description = "RDS 포트"
  sensitive   = true
  type        = number
  default     = 5432
}

variable "db_username" {
  description = "RDS DB 사용자명"
  sensitive   = true
  type        = string
}

##################
# Nginx
##################
variable "nginx_admin_email" {
  description = "Nginx Proxy Manager 관리자 이메일"
  sensitive   = true
}

##################
# Toggles
##################
variable "expose_rds_port" {
  description = "RDS 포트 외부 노출 여부. True로 설정하면 외부에서 접근 가능"
  type        = bool
  default     = true
}

variable "expose_npm_config" {
  description = "Nginx Proxy Manager 설정 페이지 외부 노출 여부"
  type        = bool
  default     = true
}

variable "expose_redis_port" {
  description = "Redis 포트 외부 노출 여부. True로 설정하면 외부에서 접근 가능"
  type        = bool
  default     = true
}

variable "bucket_key_enabled" {
  description = "S3 버킷 키 활성화 여부"
  type        = bool
  default     = false  # 화면에서 "활성화" 선택됨
}

variable "is_s3_private" {
  description = "S3 버킷 퍼블릭 액세스 차단 해제 여부"
  type        = bool
  default     = true
}

variable "enable_s3_acl" {
  description = "S3 버킷 ACL 활성화 여부"
  type        = bool
  default     = false
}