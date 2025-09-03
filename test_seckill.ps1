# 测试秒杀功能的PowerShell脚本

Write-Host "=== 测试RabbitMQ秒杀功能 ===" -ForegroundColor Green

# 测试秒杀接口
$url = "http://localhost:8081/voucher-order/seckill/10"
Write-Host "测试秒杀接口: $url" -ForegroundColor Yellow

try {
    $response = Invoke-WebRequest -Uri $url -Method POST -ContentType "application/json"
    Write-Host "响应状态码: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "响应内容: $($response.Content)" -ForegroundColor Cyan
} catch {
    Write-Host "请求失败: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "`n=== 测试完成 ===" -ForegroundColor Green 