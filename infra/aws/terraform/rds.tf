########################################################
# RDS 데이터베이스 설정
# PostgreSQL RDS Instance, Subnet Group, Parameter Group
########################################################

###############
# RDS 서브넷 그룹
###############
# RDS 서브넷 그룹 (최소 2개의 서로 다른 AZ 필요)
resource "aws_db_subnet_group" "rds_subnet_group" {
  name       = "${var.prefix}-rds-subnet-group"
  subnet_ids = [aws_subnet.subnet_1.id, aws_subnet.subnet_2.id]

  tags = merge(local.common_tags, {
    Name = "${var.prefix}-rds-subnet-group"
  })
}

#####################
# RDS 파라미터 그룹
#####################
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

#####################
# PostgreSQL RDS 인스턴스
#####################
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