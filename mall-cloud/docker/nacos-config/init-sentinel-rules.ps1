# =============================================================
# 初始化 Sentinel 流控规则到 Nacos（规则配置中心化）
#
# 前置：docker compose up -d nacos 已就绪
# 用法：
#   .\init-sentinel-rules.ps1                       # 默认 localhost:8848 / SENTINEL_GROUP
#   $env:NACOS_ADDR="192.168.1.10:8848"; .\init-sentinel-rules.ps1
#
# 幂等：重复执行会覆盖同名 dataId，应用通过 Nacos 监听实时生效
# =============================================================
$ErrorActionPreference = "Stop"

$NacosAddr = if ($env:NACOS_ADDR) { $env:NACOS_ADDR } else { "localhost:8848" }
$Group     = if ($env:SENTINEL_GROUP) { $env:SENTINEL_GROUP } else { "SENTINEL_GROUP" }
$BaseUrl   = "http://${NacosAddr}/nacos/v2/cs/config"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

function Push-Rule {
    param([string]$DataId, [string]$File)
    Write-Host "==> 发布 $DataId (group=$Group)"
    $Body = Get-Content -Raw -Path (Join-Path $ScriptDir $File)
    Invoke-RestMethod -Method Post -Uri "${BaseUrl}?dataId=${DataId}&groupName=${Group}&type=json" `
        -Body $Body -ContentType "text/plain" | Out-Null
    Write-Host "    完成"
}

Push-Rule "mall-order-flow-rules"        "mall-order-flow-rules.json"
Push-Rule "mall-product-flow-rules"      "mall-product-flow-rules.json"
Push-Rule "mall-gateway-flow-rules"      "mall-gateway-flow-rules.json"
Push-Rule "mall-gateway-api-group-rules" "mall-gateway-api-group-rules.json"

Write-Host ""
Write-Host "Sentinel 规则已全部发布到 Nacos：$NacosAddr (group=$Group)"
Write-Host "应用启动后自动拉取；控制台 http://$NacosAddr/nacos 也可在线修改，实时推送。"
