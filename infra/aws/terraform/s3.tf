########################################################
# S3 버킷 설정
# S3 Bucket, Bucket Policy, Encryption, Public Access Block
########################################################

###############
# S3 버킷
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

#########################
# S3 소유권 컨트롤
#########################
resource "aws_s3_bucket_ownership_controls" "s3_1_ownership" {
  bucket = aws_s3_bucket.s3_1.id

  rule {
    # true: ACL 활성화, false: ACL 비활성화 (권장)
    object_ownership = var.enable_s3_acl ? "BucketOwnerPreferred" : "BucketOwnerEnforced"
  }
}

#########################
# S3 버킷 정책
#########################
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

#########################
# S3 퍼블릭 액세스 차단
#########################
resource "aws_s3_bucket_public_access_block" "s3_1_public_access" {
  bucket = aws_s3_bucket.s3_1.id

  block_public_acls       = var.is_s3_private
  block_public_policy     = var.is_s3_private
  ignore_public_acls      = var.is_s3_private
  restrict_public_buckets = var.is_s3_private
}

#########################
# S3 암호화 설정
#########################
resource "aws_s3_bucket_server_side_encryption_configuration" "s3_1_encryption" {
  bucket = aws_s3_bucket.s3_1.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = var.encryption_type
    }

    bucket_key_enabled = var.bucket_key_enabled
  }
}