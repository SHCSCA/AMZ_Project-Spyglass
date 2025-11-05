#!/bin/bash
# 生产环境一键部署和修复脚本
# 使用方法：bash deploy_and_fix.sh

set -e  # 遇到错误立即退出

echo "=========================================="
echo "🚀 开始部署和修复流程"
echo "=========================================="

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 配置
PROD_URL="http://shcamz.xyz:8081"
PROJECT_DIR="/root/AMZ_Project-Spyglass"

echo -e "${YELLOW}步骤 1/5: 拉取最新代码${NC}"
cd $PROJECT_DIR
git pull origin dev

echo ""
echo -e "${YELLOW}步骤 2/5: 重新构建并启动容器${NC}"
docker-compose down
docker-compose up --build -d

echo ""
echo -e "${YELLOW}步骤 3/5: 等待应用启动...${NC}"
sleep 30

# 检查应用健康状态
echo "检查应用健康状态..."
HEALTH_STATUS=$(curl -s ${PROD_URL}/actuator/health | jq -r '.status')
if [ "$HEALTH_STATUS" == "UP" ]; then
    echo -e "${GREEN}✓ 应用已成功启动${NC}"
else
    echo -e "${RED}✗ 应用启动失败，健康状态: $HEALTH_STATUS${NC}"
    exit 1
fi

echo ""
echo -e "${YELLOW}步骤 4/5: 查询数据库当前状态${NC}"
curl -s ${PROD_URL}/api/debug/change-alert/info | jq '.columns[] | select(.name=="old_value" or .name=="new_value") | {name, type, maxLength}'

echo ""
echo -e "${YELLOW}步骤 5/5: 执行数据库字段修复${NC}"
FIX_RESULT=$(curl -s -X POST ${PROD_URL}/api/debug/change-alert/fix-field-length)
echo "$FIX_RESULT" | jq .

# 检查修复是否成功
SUCCESS=$(echo "$FIX_RESULT" | jq -r '.success')
if [ "$SUCCESS" == "true" ]; then
    echo -e "${GREEN}✓ 数据库字段修复成功${NC}"
else
    echo -e "${RED}✗ 数据库字段修复失败${NC}"
    echo "$FIX_RESULT" | jq -r '.error'
    exit 1
fi

echo ""
echo "=========================================="
echo -e "${GREEN}🎉 部署和修复完成！${NC}"
echo "=========================================="

echo ""
echo "验证步骤："
echo "1. 查看修复后的表结构："
echo "   curl -s ${PROD_URL}/api/debug/change-alert/info | jq '.columns'"
echo ""
echo "2. 触发一次抓取测试："
echo "   curl -X POST ${PROD_URL}/api/debug/scrape/2"
echo ""
echo "3. 等待 10 秒后查看 change_alert 表数据："
echo "   curl -s ${PROD_URL}/api/debug/change-alert/info | jq '{totalRecords, countByType, recentRecords}'"
echo ""
echo "4. 查看应用日志："
echo "   docker-compose logs -f app | grep -E 'Alert|CHANGE'"
