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

##################
# ACM 인증서
##################
# CloudFront는 us-east-1 리전에 있어야 함
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