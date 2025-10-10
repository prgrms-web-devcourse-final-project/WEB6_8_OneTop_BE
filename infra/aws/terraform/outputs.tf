########################################################
# 출력값 정의
# Terraform 실행 후 출력할 리소스 정보
########################################################

##################
# EC2 출력
##################
output "ec2_public_ip" {
  description = "EC2 Public IP"
  value       = aws_eip.ec2_1_eip.public_ip
  sensitive   = false
}

##################
# RDS 출력
##################
output "rds_endpoint" {
  description = "RDS Endpoint"
  value       = aws_db_instance.postgres_rds_1.endpoint
  sensitive   = false
}

##################
# CloudFront 출력
##################
output "cloudfront_domain" {
  description = "CloudFront Domain"
  value       = aws_cloudfront_distribution.cloudfront_distribution.domain_name
  sensitive   = false
}

##################
# ACM 인증서 출력 (CDN 도메인 설정 시)
##################
output "cdn_domain_cert_arn" {
  description = "ACM Certificate ARN"
  value       = aws_acm_certificate.cdn_domain_cert.arn
}

output "cdn_domain_cert_status" {
  description = "Certificate Status"
  value       = aws_acm_certificate.cdn_domain_cert.status
}

output "cdn_domain_cert_validation_records" {
  description = "DNS validation records for certificate"
  value = {
    for dvo in aws_acm_certificate.cdn_domain_cert.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  }
}