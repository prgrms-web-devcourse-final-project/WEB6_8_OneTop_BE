########################################################
# 보안 그룹 설정
# EC2, RDS 보안 그룹 및 관련 규칙
########################################################

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