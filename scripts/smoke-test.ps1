# 「此刻校园」后端接口冒烟测试（对 PostgreSQL 实例运行中的服务）
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080/api'
$out = New-Object System.Collections.Generic.List[string]

function Log($message) { $script:out.Add($message) }
function Post($url, $payload, $headers) {
  $json = $payload | ConvertTo-Json -Depth 6
  return Invoke-RestMethod -Method Post -Uri $url -ContentType 'application/json; charset=utf-8' `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($json)) -Headers $headers
}
function Q($text) { return [uri]::EscapeDataString($text) }

$schools = (Invoke-RestMethod "$base/schools").data
Log ("[校区] 共 {0} 所（按排序）：" -f $schools.Count)
foreach ($s in $schools) {
  Log ("   {0,-32} code={1,-16} 测试数据={2,-5} 排序={3} 中心=({4}, {5})" -f $s.name, $s.code, $(if ($s.isTestData) { '是' } else { '否' }), $s.sortOrder, $s.centerLatitude, $s.centerLongitude)
}
$formal = $schools | Where-Object { -not $_.isTestData }
$tests = $schools | Where-Object { $_.isTestData }
Log ("[校区校验] 正式校区 {0} 所（首个应为华中农业大学且置顶：{1}）；测试数据校区 {2} 所" -f $formal.Count, ($schools[0].code -eq 'hzau-main' -and -not $schools[0].isTestData), $tests.Count)
$hzauSchoolId = $schools[0].schoolId
$schoolId = $tests[0].schoolId

$cats = (Invoke-RestMethod "$base/categories").data
Log ("[分类] 共 {0} 个：{1}" -f $cats.Count, (($cats | ForEach-Object { "$($_.name)($($_.postCount))" }) -join ' / '))

$login = Post "$base/auth/login" @{ username = 'student1'; password = '123456' } @{}
$token = $login.data.token
$headers = @{ Authorization = "Bearer $token" }
Log ("[登录] student1 成功，令牌长度 {0}，学校 {1}" -f $token.Length, $login.data.user.schoolName)

$lostCategory = ($cats | Where-Object { $_.code -eq 'LOST_FOUND' }).categoryId
$created = Post "$base/posts" @{
  schoolId = $schoolId; categoryId = $lostCategory
  title = '在图书馆捡到校园卡一张'
  content = '今天下午在图书馆三楼自习区捡到一张校园卡，请失主尽快联系我'
  tags = @('失物招领', '图书馆'); contact = '13800001111'; validDays = 7
  latitude = 31.4874; longitude = 120.2650; aiAssisted = $true
} $headers
$postId = $created.data.base.postId
Log ("[发布] 成功 postId={0} 状态={1}（作者本人可见完整联系方式：{2}）" -f $postId, $created.data.base.status, (-not $created.data.contactMasked))

$map = (Invoke-RestMethod "$base/posts/map?schoolId=$schoolId&minLat=31.470&maxLat=31.500&minLng=120.250&maxLng=120.285&limit=500").data
Log ("[地图视野] 节点 {0} 个，聚合节点示例：{1}（同地点 {2} 条）" -f $map.Count, $map[0].title, $map[0].nodeCount)

$nearby = (Invoke-RestMethod "$base/posts/nearby?schoolId=$schoolId&lat=31.4856&lng=120.2653&radiusKm=1&limit=10").data
Log ("[周边检索] 1 公里内 {0} 条，最近：{1}（{2} 米）" -f $nearby.Count, $nearby[0].title, [math]::Round($nearby[0].distanceKm * 1000))

$ai = (Invoke-RestMethod "$base/search/ai?schoolId=$schoolId&q=$(Q '图书馆 丢的校园卡')&lat=31.4856&lng=120.2653&limit=5").data
Log ("[AI 搜索] 识别分类={0} 地点={1} 关键词={2} 结果 {3} 条" -f $ai.intent.categoryName, $ai.intent.locationName, ($ai.intent.keywords -join '/'), $ai.total)
Log ("[AI 解释] {0}" -f $ai.explanation)
Log ("[AI 结果] Top1={0} 相关度={1}% 匹配原因={2}" -f $ai.list[0].title, $ai.list[0].relevance, ($ai.list[0].matchReasons -join ' | '))

$ai2 = (Invoke-RestMethod "$base/search/ai?schoolId=$schoolId&q=$(Q '附近1公里的二手台灯 预算300元')&lat=31.4856&lng=120.2653&limit=5").data
Log ("[AI 搜索-条件] 分类={0} 附近={1} 半径={2}km 预算={3}元 结果 {4} 条" -f $ai2.intent.categoryName, $ai2.intent.nearby, $ai2.intent.radiusKm, $ai2.intent.maxPrice, $ai2.total)

$kw = (Invoke-RestMethod "$base/search/keyword?schoolId=$schoolId&q=$(Q '校园卡')&size=5").data
Log ("[普通搜索] '校园卡' 命中 {0} 条" -f $kw.total)

$student2 = Post "$base/auth/login" @{ username = 'student2'; password = '123456' } @{}
$h2 = @{ Authorization = "Bearer $($student2.data.token)" }
$verify = Post "$base/posts/$postId/verify" @{ result = 'TRUE'; remark = '我在现场确认过' } $h2
Log ("[协同验证] 证实 {0} 人，可信度 {1}%，结论：{2}" -f $verify.data.trueCount, $verify.data.trustPercent, $verify.data.level)

$again = Post "$base/posts/$postId/verify" @{ result = 'FALSE'; remark = '重复提交测试' } $h2
Log ("[防刷校验] 重复验证返回 code={0} message={1}" -f $again.code, $again.message)

$comment = Post "$base/posts/$postId/comments" @{ content = '帮顶，希望失主尽快看到' } $h2
Log ("[评论] 成功：{0}" -f $comment.data.content)

$student3 = Post "$base/auth/login" @{ username = 'student3'; password = '123456' } @{}
$h3 = @{ Authorization = "Bearer $($student3.data.token)" }
$null = Post "$base/posts/$postId/reports" @{ reason = '内容与实际不符'; detail = '位置描述似乎不对' } $h3
Log "[举报] 提交成功"

$ai3 = Post "$base/ai/suggest" @{
  schoolId = $schoolId
  title = '急！在操场捡到一副蓝牙耳机'
  content = '今天傍晚在田径场捡到一副蓝牙耳机，白色的，请失主联系我，急！'
} $headers
Log ("[AI 辅助发布] 建议分类={0}（置信度 {1}）标签={2} 有效期={3}天" -f $ai3.data.categoryName, [math]::Round($ai3.data.categoryConfidence, 2), ($ai3.data.tags -join '、'), $ai3.data.suggestedValidDays)
Log ("[AI 提示] {0}" -f ($ai3.data.tips -join '；'))

$admin = Post "$base/auth/login" @{ username = 'admin'; password = 'admin123' } @{}
$ha = @{ Authorization = "Bearer $($admin.data.token)" }
$dash = (Invoke-RestMethod "$base/admin/dashboard" -Headers $ha).data
Log ("[后台仪表盘] 信息总数={0}（已发布 {1} / 待审核 {2} / 已过期 {3}）用户={4} 地点={5} 待处理举报={6} 异常关注={7} 今日新增={8}" -f $dash.totalPosts, $dash.publishedPosts, $dash.pendingPosts, $dash.expiredPosts, $dash.totalUsers, $dash.totalLocations, $dash.pendingReports, $dash.flaggedPosts, $dash.todayNewPosts)
Log ("[分类分布] {0}" -f (($dash.categoryDistribution | ForEach-Object { "$($_.name):$($_.count)" }) -join ' '))
Log ("[校区分布] {0}" -f (($dash.schoolDistribution | ForEach-Object { "$($_.name):$($_.count)" }) -join ' '))

$flagged = (Invoke-RestMethod "$base/admin/posts/flagged?limit=3" -Headers $ha).data
Log ("[高频证伪关注] {0} 条，示例：{1}（证实 {2} / 证伪 {3}）" -f $flagged.Count, $flagged[0].title, $flagged[0].verifyTrueCount, $flagged[0].verifyFalseCount)

$expire = Post "$base/admin/tasks/expire" @{} $ha
Log ("[过期治理] 手动触发完成，处理 {0} 条" -f $expire.data.affectedCount)

$tenant = (Invoke-RestMethod "$base/posts?schoolId=$hzauSchoolId&size=1").data
Log ("[多租户] 切换到 {0}：共 {1} 条信息（测试数据校区 {2}：{3} 条）" -f $schools[0].name, $tenant.total, $tests[0].name, (Invoke-RestMethod "$base/posts?schoolId=$schoolId&size=1").data.total)

# ------------------------- 正式校区（华中农业大学）专项验证 -------------------------
$hzauLocations = (Invoke-RestMethod "$base/locations?schoolId=$hzauSchoolId").data
Log ("[华农-地点] {0} 共 {1} 个校园地点（示例：{2}）" -f $schools[0].name, $hzauLocations.Count, (($hzauLocations | Select-Object -First 3 | ForEach-Object { $_.name }) -join '、'))

$hzauPost = Post "$base/posts" @{
  schoolId = $hzauSchoolId; categoryId = $lostCategory; locationId = $hzauLocations[0].locationId
  title = '华中农业大学图书馆失物招领'
  content = '狮子山主校区图书馆三楼自习区，捡到一张校园卡，请失主联系我'
  tags = @('失物招领', '图书馆'); latitude = 30.473990; longitude = 114.348398; validDays = 7
} $headers
$hzauPostId = $hzauPost.data.base.postId
Log ("[华农-发布] 成功 postId={0} 状态={1}（校区：{2}）" -f $hzauPostId, $hzauPost.data.base.status, $hzauPost.data.base.locationName)

$hzauMap = (Invoke-RestMethod "$base/posts/map?schoolId=$hzauSchoolId&minLat=30.460&maxLat=30.490&minLng=114.330&maxLng=114.365&limit=50").data
Log ("[华农-地图] 视野内节点 {0} 个，示例：{1}" -f $hzauMap.Count, $hzauMap[0].title)

$hzauNearby = (Invoke-RestMethod "$base/posts/nearby?schoolId=$hzauSchoolId&lat=30.473990&lng=114.348398&radiusKm=2&limit=10").data
Log ("[华农-周边] 2 公里内 {0} 条，最近：{1}（{2} 米）" -f $hzauNearby.Count, $hzauNearby[0].title, [math]::Round($hzauNearby[0].distanceKm * 1000))

$hzauSearch = (Invoke-RestMethod "$base/search/ai?schoolId=$hzauSchoolId&q=$(Q '华农图书馆丢的校园卡')&limit=5").data
Log ("[华农-AI搜索] 识别分类={0} 结果 {1} 条，Top1={2}" -f $hzauSearch.intent.categoryName, $hzauSearch.total, $hzauSearch.list[0].title)

$mine = (Invoke-RestMethod "$base/me/interactions" -Headers $headers).data
Log ("[个人中心] 我的发布 {0} 条，我的验证 {1} 条" -f $mine.posts.Count, $mine.verifications.Count)

$engine = (Invoke-RestMethod "$base/meta/search-engine").data
Log ("[检索引擎] {0}，特性：{1}" -f $engine.engine, ($engine.features -join '、'))

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
$buildDir = Join-Path $repoRoot '_build'
if (-not (Test-Path $buildDir)) { New-Item -ItemType Directory -Path $buildDir | Out-Null }
$resultFile = Join-Path $buildDir 'smoke-result.txt'
$out -join "`r`n" | Out-File -FilePath $resultFile -Encoding utf8
Write-Output "SMOKE DONE -> $resultFile"
