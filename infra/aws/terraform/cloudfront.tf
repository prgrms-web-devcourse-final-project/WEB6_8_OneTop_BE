########################################################
# CloudFront CDN 설정
# CloudFront Distribution, Origin Access Identity
########################################################

##################
# CloudFront OAI
##################
# CloudFront가 S3 버킷에 접근할 수 있도록 OAI 생성
resource "aws_cloudfront_origin_access_identity" "oai_1" {
  comment = "${var.prefix}-s3-oai"
}

##################
# CloudFront Distribution
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
    compress               = true  # Gzip/Brotli 압축 활성화

    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }

    # 캐시 설정 - 정적 리소스는 길게
    min_ttl     = 0
    default_ttl = 86400    # 1일
    max_ttl     = 31536000 # 1년
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  # 존재하지 않는 오브젝트 요청 시
  custom_error_response {
    error_code            = 404
    error_caching_min_ttl = 300  # 5분 (에러 캐싱으로 Origin 부하 감소)
  }

  # 권한 없음 에러 (예: S3 퍼미션 문제)
  custom_error_response {
    error_code            = 403
    error_caching_min_ttl = 120
  }

  # S3 서비스 장애 시
  custom_error_response {
    error_code            = 503
    error_caching_min_ttl = 10
  }

  # 과금 정책
  # PriceClass_100: 미국, 캐나다, 유럽
  # PriceClass_200: PriceClass_100 + 아시아, 중동, 아프리카
  # PriceClass_All: 전세계
  price_class = "PriceClass_100"

  # fixme: CDN 도메인 설정
  aliases = [var.cdn_domain]

  viewer_certificate {
    # cloudfront_default_certificate = true
    # fixme: ACM 인증서 사용(CDN 도메인 설정) 시 아래 주석 해제
    acm_certificate_arn = aws_acm_certificate_validation.cdn_domain_cert_validation.certificate_arn
    ssl_support_method = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }

  tags = merge(local.common_tags, {
    Name = "${var.prefix}-cloudfront-distribution"
  })
}

##################
# ACM 인증서
##################
# CloudFront는 us-east-1 리전에 있어야 함
# fixme: CDN 도메인 설정 시 주석 해제
resource "aws_acm_certificate" "cdn_domain_cert" {
  provider          = aws.us_east_1
  domain_name       = var.cdn_domain
  key_algorithm     = "EC_prime256v1"
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }

  tags = merge(local.common_tags, {
    Name = "${var.prefix}-cdn-domain-cert"
  })
}

resource "aws_acm_certificate_validation" "cdn_domain_cert_validation" {
  provider = aws.us_east_1
  certificate_arn = aws_acm_certificate.cdn_domain_cert.arn

  timeouts {
    create = "1h"
  }
}