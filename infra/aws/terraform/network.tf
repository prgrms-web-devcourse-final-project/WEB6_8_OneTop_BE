########################################################
# 네트워크 인프라 설정
# VPC, Subnet, Internet Gateway, Route Table
########################################################

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