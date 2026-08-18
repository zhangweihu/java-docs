#!/usr/bin/env bash
# =============================================================
# 初始化 Sentinel 流控规则到 Nacos（规则配置中心化）
#
# 前置：docker compose up -d nacos 已就绪
# 用法：
#   bash init-sentinel-rules.sh                  # 默认 localhost:8848 / SENTINEL_GROUP
#   NACOS_ADDR=192.168.1.10:8848 bash init-sentinel-rules.sh
#
# 幂等：重复执行会覆盖同名 dataId，应用通过 Nacos 监听实时生效
# =============================================================
set -euo pipefail

NACOS_ADDR="${NACOS_ADDR:-localhost:8848}"
GROUP="${SENTINEL_GROUP:-SENTINEL_GROUP}"
BASE_URL="http://${NACOS_ADDR}/nacos/v2/cs/config"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

push_rule() {
  local data_id="$1" file="$2"
  echo "==> 发布 ${data_id} (group=${GROUP})"
  curl -sS -X POST "${BASE_URL}?dataId=${data_id}&groupName=${GROUP}&type=json" \
    -H "Content-Type: text/plain" \
    --data-binary "@${SCRIPT_DIR}/${file}"
  echo
}

push_rule "mall-order-flow-rules"        "mall-order-flow-rules.json"
push_rule "mall-product-flow-rules"      "mall-product-flow-rules.json"
push_rule "mall-gateway-flow-rules"      "mall-gateway-flow-rules.json"
push_rule "mall-gateway-api-group-rules" "mall-gateway-api-group-rules.json"

echo "✅ Sentinel 规则已全部发布到 Nacos：${NACOS_ADDR} (group=${GROUP})"
echo "   应用启动后自动拉取；控制台 http://${NACOS_ADDR}/nacos 也可在线修改，实时推送。"
