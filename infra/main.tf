########################################################
# 마지막 수정: 250929
# 작성자: gooraeng
#
# AWS 인프라를 코드로 관리하기 위한 Terraform 설정 파일
########################################################

terraform {
  // aws 라이브러리 불러옴
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

locals {
  http_port  = 80
  https_port = 443
  npm_port   = 81

  # 공통 태그 정의
  common_tags = {
    Team = "devcos-team01"
  }

  ec2_user_data = templatefile("${path.module}/ec2_user_data.tpl", {
    password           = var.default_password,
    app_back_domain    = var.back_domain,
    app_front_domain   = var.front_domain,
    github_token_owner = var.github_token_owner,
    github_token       = var.github_token,
    nginx_admin_email  = var.nginx_admin_email,
    timezone           = var.timezone,
  })

  all_protocol = "-1"
}

################
# AWS 설정
################
provider "aws" {
  region = var.region
}

############
# VPC 설정
############
resource "aws_vpc" "vpc_1" {
  cidr_block           = "10.0.0.0/16"

  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = merge(local.common_tags, {
    Name = "${var.prefix}-vpc"
  })
}

##############
# Subnet 설정
##############
resource "aws_subnet" "subnet_1" {
  vpc_id                  = aws_vpc.vpc_1.id
  cidr_block              = "10.0.0.0/24"
  availability_zone       = "${var.region}a"
  map_public_ip_on_launch = true

  tags = merge(local.common_tags, {
    Name = "${var.prefix}-subnet"
  })
}

resource "aws_subnet" "subnet_2" {
  vpc_id                  = aws_vpc.vpc_1.id
  cidr_block              = "10.0.1.0/24"
  availability_zone       = "${var.region}b"
  map_public_ip_on_launch = true

  tags = merge(local.common_tags, {
    Name = "${var.prefix}-subnet-2"
  })
}

resource "aws_subnet" "subnet_3" {
  vpc_id                  = aws_vpc.vpc_1.id
  cidr_block              = "10.0.2.0/24"
  availability_zone       = "${var.region}c"
  map_public_ip_on_launch = true

  tags = merge(local.common_tags, {
    Name = "${var.prefix}-subnet-3"
  })
}

resource "aws_subnet" "subnet_4" {
  vpc_id                  = aws_vpc.vpc_1.id
  cidr_block              = "10.0.3.0/24"
  availability_zone       = "${var.region}d"
  map_public_ip_on_launch = true

  tags = merge(local.common_tags, {
    Name = "${var.prefix}-subnet-4"
  })
}

####################
# 인터넷 게이트웨이
####################
resource "aws_internet_gateway" "igw_1" {
  vpc_id = aws_vpc.vpc_1.id

  tags = merge(local.common_tags, {
    Name = "${var.prefix}-internet-gateway"
  })
}

##################
# 라우팅 테이블
##################
resource "aws_route_table" "rt_1" {
  vpc_id = aws_vpc.vpc_1.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.igw_1.id
  }

  tags = merge(local.common_tags, {
    Name = "${var.prefix}-router-table"
  })
}

################################
# 서브넷 <-> 라우팅 테이블 연결
################################
resource "aws_route_table_association" "association_1" {
  subnet_id      = aws_subnet.subnet_1.id
  route_table_id = aws_route_table.rt_1.id
}

resource "aws_route_table_association" "association_2" {
  subnet_id      = aws_subnet.subnet_2.id
  route_table_id = aws_route_table.rt_1.id
}

resource "aws_route_table_association" "association_3" {
  subnet_id      = aws_subnet.subnet_3.id
  route_table_id = aws_route_table.rt_1.id
}

resource "aws_route_table_association" "association_4" {
  subnet_id      = aws_subnet.subnet_4.id
  route_table_id = aws_route_table.rt_1.id
}

######################
# 기본 Security Group
######################
resource "aws_security_group" "sg_1" {
  name        = "${var.prefix}-security-group"
  vpc_id      = aws_vpc.vpc_1.id

  description = "Default security group for EC2 instances"

  #################################
  # INGRESS : 외부 -> aws 내부
  #################################

  # HTTP
  ingress {
    from_port   = local.http_port
    to_port     = local.http_port
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "HTTP traffic"
  }

  # HTTPS
  ingress {
    from_port   = local.https_port
    to_port     = local.https_port
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "HTTPS traffic"
  }

  # RDS
  dynamic "ingress" {
    for_each = var.expose_rds_port ? [1] : []
    content {
      from_port   = var.db_port
      to_port     = var.db_port
      protocol    = "tcp"
      cidr_blocks = ["0.0.0.0/0"]
      description = "RDS public access"
    }
  }

  # Nginx Proxy Manager
  dynamic "ingress" {
    for_each = var.expose_npm_config ? [1] : []
    content {
      from_port   = local.npm_port
      to_port     = local.npm_port
      protocol    = "tcp"
      cidr_blocks = ["0.0.0.0/0"]
      description = "Nginx Proxy Manager public access"
    }
  }

  #################################
  # EGRESS : 외부로 통신
  #################################
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = local.all_protocol
    cidr_blocks = ["0.0.0.0/0"]
    description = "Allow all outbound traffic"
  }

  tags = merge(local.common_tags, {
    Name = "${var.prefix}-security-group"
  })
}

########################
# EC2 <-> RDS 보안 그룹
# 참고 - https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/ec2-rds-connect.html
########################
# 1. EC2 -> RDS
resource "aws_security_group" "ec2_to_rds_sg" {
  name        = "ec2-rds-1"
  vpc_id      = aws_vpc.vpc_1.id

  description = "Security group for EC2 to RDS communication"

  tags = merge(local.common_tags, {
    Name = "${var.prefix}-security-group-ec2-to-rds"
  })
}

resource "aws_vpc_security_group_egress_rule" "ec2_to_rds_rule" {
  security_group_id = aws_security_group.ec2_to_rds_sg.id
  description = "EC2 to RDS egress rule"

  from_port                    = var.db_port
  to_port                      = var.db_port
  ip_protocol                  = "tcp"
  referenced_security_group_id = aws_security_group.rds_to_ec2_sg.id

  tags = merge(local.common_tags, {
    Name = "${var.prefix}-ec2-to-rds-egress-rule"
  })
}

# 2. RDS -> EC2
resource "aws_security_group" "rds_to_ec2_sg" {
  name        = "rds-ec2-1"
  vpc_id      = aws_vpc.vpc_1.id

  description = "Security group for RDS to EC2 communication"

  tags = merge(local.common_tags, {
    Name = "${var.prefix}-security-group-rds-to-ec2"
  })
}

resource "aws_vpc_security_group_ingress_rule" "rds_to_ec2_rule" {
  security_group_id = aws_security_group.rds_to_ec2_sg.id
  description       = "RDS to EC2 ingress rule"

  from_port                    = var.db_port
  to_port                      = var.db_port
  ip_protocol                  = "tcp"
  referenced_security_group_id = aws_security_group.ec2_to_rds_sg.id

  tags = merge(local.common_tags, {
    Name = "${var.prefix}-rds-to-ec2-ingress-rule"
  })
}

###############
# EC2 설정
###############
# EC2 IAM 역할 생성
resource "aws_iam_role" "ec2_role_1" {
  name = "${var.prefix}-ec2-role"

  # 이 역할에 대한 신뢰 정책 설정. EC2 서비스가 이 역할을 가정할 수 있도록 설정
  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Sid = "",
        Action = "sts:AssumeRole",
        Principal = {
          Service = "ec2.amazonaws.com"
        },
        Effect = "Allow"
      }
    ]
  })

  tags = merge(local.common_tags, {
    Name = "${var.prefix}-ec2-role"
  })
}

####################
# EC2 - Policy 설정
####################
# EC2 역할에 AmazonS3FullAccess 정책을 부착
# 생성된 인스턴스는 S3에 대한 완전한 액세스 권한을 가짐.
resource "aws_iam_role_policy_attachment" "s3_full_access" {
  role       = aws_iam_role.ec2_role_1.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonS3FullAccess"
}

# EC2 역할에 AmazonEC2RoleforSSM 정책을 부착
# Session Manager로 접속할 수 있도록 권한 부여
resource "aws_iam_role_policy_attachment" "ec2_ssm" {
  role       = aws_iam_role.ec2_role_1.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonEC2RoleforSSM"
}

# IAM 인스턴스 프로파일 생성
resource "aws_iam_instance_profile" "instance_profile_1" {
  name = "${var.prefix}-instance-profile"
  role = aws_iam_role.ec2_role_1.name

  tags = merge(local.common_tags, {
    Name = "${var.prefix}-instance-profile"
  })
}

# 최신 Amazon Linux 2023 AMI 조회 (프리 티어 호환)
data "aws_ami" "latest_amazon_linux" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }

  filter {
    name   = "architecture"
    values = ["x86_64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }

  filter {
    name   = "root-device-type"
    values = ["ebs"]
  }
}

####################
# EC2 인스턴스 생성
####################
resource "aws_instance" "ec2_1" {
  # 사용할 AMI ID
  ami                         = data.aws_ami.latest_amazon_linux.id
  # EC2 인스턴스 유형
  instance_type               = "t3.micro"
  # 사용할 서브넷 ID
  subnet_id                   = aws_subnet.subnet_1.id
  # 적용할 보안 그룹 ID
  vpc_security_group_ids      = [aws_security_group.sg_1.id, aws_security_group.ec2_to_rds_sg.id]
  # 퍼블릭 IP 연결 설정
  associate_public_ip_address = true

  # 인스턴스에 IAM 역할 연결
  iam_instance_profile        = aws_iam_instance_profile.instance_profile_1.name

  # 인스턴스에 태그 설정
  tags = merge(local.common_tags, {
    Name = "${var.prefix}-ec2-1"
  })

  # 루트 볼륨 설정
  root_block_device {
    volume_type = "gp2"
    volume_size = 30 # 볼륨 크기를 30GB로 설정
    encrypted   = true
  }

  user_data = local.ec2_user_data
}

###############
# EIP 설정
###############
resource "aws_eip" "ec2_1_eip" {
  instance = aws_instance.ec2_1.id
  domain   = "vpc"

  tags = merge(local.common_tags, {
    Name = "${var.prefix}-ec2-eip"
  })
}

###############
# RDS 설정
###############
# RDS 서브넷 그룹 (최소 2개의 서로 다른 AZ 필요)
resource "aws_db_subnet_group" "rds_subnet_group" {
  name       = "${var.prefix}-rds-subnet-group"
  subnet_ids = [aws_subnet.subnet_1.id, aws_subnet.subnet_2.id]

  tags = merge(local.common_tags, {
    Name = "${var.prefix}-rds-subnet-group"
  })
}

# PostgreSQL RDS 인스턴스 (프리 티어)
resource "aws_db_instance" "postgres_rds_1" {
  identifier = "${var.prefix}-postgres"

  # 엔진 설정
  engine         = "postgres"
  engine_version = "17.6"
  instance_class = "db.t3.micro"  # 프리 티어
  port           = var.db_port

  # 데이터베이스 설정
  db_name  = var.db_name
  username = var.db_username
  password = var.default_password

  # 스토리지 설정 (프리 티어: 최대 20GB)
  allocated_storage     = 20
  max_allocated_storage = 0
  storage_type          = "gp2"
  storage_encrypted     = true

  # 네트워크 설정
  db_subnet_group_name   = aws_db_subnet_group.rds_subnet_group.name
  vpc_security_group_ids = [aws_security_group.rds_to_ec2_sg.id, aws_security_group.sg_1.id]
  publicly_accessible    = true # RDS 퍼블릭 액세스 설정 (개발 목적으로 한시적으로 허용)
  availability_zone      = "${var.region}a"  # EC2와 같은 AZ로 강제 배치
  multi_az               = false

  # 백업 설정 (프리 티어)
  skip_final_snapshot        = true
  deletion_protection        = false
  auto_minor_version_upgrade = false

  # 파라미터 그룹
  parameter_group_name = aws_db_parameter_group.postgres_rds_1_param_group.name
  apply_immediately    = true

  # 성능 모니터링 (프리 티어)
  performance_insights_enabled          = true
  performance_insights_retention_period = 7

  tags = merge(local.common_tags, {
    Name = "${var.prefix}-rds-postgres"
  })
}

resource "aws_db_parameter_group" "postgres_rds_1_param_group" {
  name = "${var.prefix}-rds-param-group"
  description = "TEAM01 ${var.prefix} project RDS parameter group"
  family      = "postgres17"

  parameter {
    name  = "timezone"
    value = var.timezone
  }

  tags = merge(local.common_tags, {
    Name = "${var.prefix}-rds-param-group"
  })
}

###############
# S3 설정
###############
resource "aws_s3_bucket" "s3_1" {
  bucket = var.s3_bucket_name

  force_destroy = true

  lifecycle {
    prevent_destroy = false
  }

  tags = merge(local.common_tags, {
    name = "${var.prefix}-s3"
  })
}

resource "aws_s3_bucket_ownership_controls" "s3_1_ownership" {
  bucket = aws_s3_bucket.s3_1.id

  rule {
    # true: ACL 활성화, false: ACL 비활성화 (권장)
    object_ownership = var.enable_s3_acl ? "BucketOwnerPreferred" : "BucketOwnerEnforced"
  }
}

# S3 버킷 정책 설정
# CloudFront OAI가 S3 버킷에 접근할 수 있도록 허용
# Presigned URL을 통한 접근 차단
resource "aws_s3_bucket_policy" "s3_1_policy" {
  bucket = aws_s3_bucket.s3_1.id
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      # CloudFront가 S3 버킷에 접근할 수 있도록 허용
      {
        Sid = "AllowCloudFrontOAIReadOnly",
        Effect = "Allow",
        Principal = {
          AWS = aws_cloudfront_origin_access_identity.oai_1.iam_arn
        },
        Action = "s3:GetObject",
        Resource = "${aws_s3_bucket.s3_1.arn}/*"
      },
      # Presigned URL을 통한 접근 차단
      {
        Sid = "DenyPresignedUrls",
        Effect = "Deny",
        Principal = "*",
        Action = "s3:*",
        Resource = [
          aws_s3_bucket.s3_1.arn,
          "${aws_s3_bucket.s3_1.arn}/*"
        ],
        Condition = {
          StringLike = {
            "s3:authType" = "REST-QUERY-STRING"
          }
        }
      }
    ]
  })

  depends_on = [aws_s3_bucket_public_access_block.s3_1_public_access]
}

resource "aws_s3_bucket_public_access_block" "s3_1_public_access" {
  bucket = aws_s3_bucket.s3_1.id

  block_public_acls       = var.is_s3_private
  block_public_policy     = var.is_s3_private
  ignore_public_acls      = var.is_s3_private
  restrict_public_buckets = var.is_s3_private
}

resource "aws_s3_bucket_server_side_encryption_configuration" "s3_1_encryption" {
  bucket = aws_s3_bucket.s3_1.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = var.encryption_type
    }

    bucket_key_enabled = var.bucket_key_enabled
  }
}

##################
# CloudFront 설정
##################
resource "aws_cloudfront_distribution" "cloudfront_distribution" {
  enabled = true

  origin {
    domain_name = aws_s3_bucket.s3_1.bucket_regional_domain_name
    origin_id   = "S3-${aws_s3_bucket.s3_1.id}"

    s3_origin_config {
      origin_access_identity = aws_cloudfront_origin_access_identity.oai_1.cloudfront_access_identity_path
    }
  }

  default_cache_behavior {
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "S3-${aws_s3_bucket.s3_1.id}"
    viewer_protocol_policy = "redirect-to-https"

    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }

    # 캐시 설정
    min_ttl     = 0
    default_ttl = 86400
    max_ttl     = 31536000
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  custom_error_response {
    error_caching_min_ttl = 600
    error_code            = 404
  }

  # 과금 정책
  # PriceClass_100: 미국, 캐나다, 유럽
  # PriceClass_200: PriceClass_100 + 아시아, 중동, 아프리카
  # PriceClass_All: 전세계
  price_class = "PriceClass_100"

  # fixme: CDN 도메인 설정
  # aliases = [var.cdn_domain]

  viewer_certificate {
    cloudfront_default_certificate = true
    # fixme: ACM 인증서 사용(CDN 도메인 설정) 시 아래 주석 해제
    # acm_certificate_arn = aws_acm_certificate.cdn_domain_cert.arn
    # ssl_support_method = "sni-only"
    # minimum_protocol_version = "TLSv1.2_2021"
  }

  tags = merge(local.common_tags, {
    Name = "${var.prefix}-cloudfront-distribution"
  })
}

# CloudFront가 S3 버킷에 접근할 수 있도록 OAI 생성
resource "aws_cloudfront_origin_access_identity" "oai_1" {
  comment = "${var.prefix}-s3-oai"
}

# ACM 인증서 (CloudFront는 us-east-1 리전에 있어야 함)
# fixme: CDN 도메인 설정 시 주석 해제
# resource "aws_acm_certificate" "cdn_domain_cert" {
#   provider = "us-east-1"
#   domain_name = var.cdn_domain
#   validation_method = "DNS"
#
#   lifecycle {
#     create_before_destroy = true
#   }
#
#   tags = merge(local.common_tags, {
#     Name = "${var.prefix}-cdn-domain-cert"
#   })
# }

##################
# Outputs
##################
output "ec2_public_ip" {
  description = "EC2 Public IP"
  value       = aws_eip.ec2_1_eip.public_ip
  sensitive   = false
}

output "rds_endpoint" {
  description = "RDS Endpoint"
  value       = aws_db_instance.postgres_rds_1.endpoint
  sensitive   = false
}

output "cloudfront_domain" {
  description = "CloudFront Domain"
  value       = aws_cloudfront_distribution.cloudfront_distribution.domain_name
  sensitive   = false
}
# fixme: CDN 도메인 설정 시 주석 해제
# output "cdn_domain_cert_arn" {
#   description = "ACM Certificate ARN"
#   value       = aws_acm_certificate.cdn_domain_cert.arn
# }
#
# output "cdn_domain_cert_status" {
#   description = "Certificate Status"
#   value       = aws_acm_certificate.cdn_domain_cert.status
# }
#
# output "cdn_domain_cert_validation_records" {
#   description = "DNS validation records for certificate"
#   value = {
#     for dvo in aws_acm_certificate.cdn_domain_cert.domain_validation_options : dvo.domain_name => {
#       name   = dvo.resource_record_name
#       record = dvo.resource_record_value
#       type   = dvo.resource_record_type
#     }
#   }
# }