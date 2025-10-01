########################################################
# 마지막 수정: 251001
# 작성자: gooraeng
#
# AWS 인프라를 코드로 관리하기 위한 Terraform 메인 설정 파일
# 리소스별 정의는 각 파일을 참고하세요:
# - network.tf: VPC, Subnet, IGW, Route Table
# - security_groups.tf: Security Groups
# - ec2.tf: EC2 Instance, IAM
# - rds.tf: RDS Database
# - s3.tf: S3 Bucket
# - cloudfront.tf: CloudFront CDN
# - outputs.tf: Output Values
########################################################

terraform {
  // aws 라이브러리 불러옴
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

################
# AWS 설정
################
provider "aws" {
  region = var.region
}

################
# 로컬 변수
################
locals {
  http_port  = 80
  https_port = 443
  npm_port   = 81
  redis_port = 6379

  # 공통 태그 정의
  common_tags = {
    Team = "devcos-team01"
  }

  ec2_user_data = templatefile("${path.module}/ec2_user_data.tpl", {
    password           = var.default_password,
    base_domain   = var.base_domain,
    github_token_owner = var.github_token_owner,
    github_token       = var.github_token,
    nginx_admin_email  = var.nginx_admin_email,
    timezone           = var.timezone,
  })

  all_protocol = "-1"
}