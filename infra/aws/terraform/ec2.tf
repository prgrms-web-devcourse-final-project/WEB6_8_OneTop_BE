########################################################
# EC2 인스턴스 설정
# IAM Role, Instance Profile, EC2 Instance, EIP
########################################################

###############
# EC2 IAM 역할
###############
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

###########
# AMI 조회
###########
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
  instance_type               = "t3.small"
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