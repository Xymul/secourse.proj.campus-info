#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""「此刻校园」后端接口冒烟测试（Linux / Windows 通用，仅依赖 Python 3 标准库）。

对应 Windows 版 scripts/smoke-test.ps1，便于在 Debian 13 等无 PowerShell 的服务器上执行。

用法：
    python3 scripts/smoke-test.py
    CAMPUS_API_BASE=http://127.0.0.1:8080/api python3 scripts/smoke-test.py

结果同时打印到控制台并写入 <仓库>/_build/smoke-result.txt。
退出码：0 全部通过；1 存在失败项；2 服务不可达。
"""

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = os.environ.get("CAMPUS_API_BASE", "http://127.0.0.1:8080/api")
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RESULT_FILE = os.path.join(REPO_ROOT, "_build", "smoke-result.txt")

lines = []
failures = 0


def log(message):
    print(message)
    lines.append(message)


def call(method, path, payload=None, token=None, timeout=30):
    """发起请求并返回业务数据（{code, message, data} 的 data 部分）。"""
    url = BASE + path
    data = None
    headers = {"Accept": "application/json"}
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        body = json.loads(resp.read().decode("utf-8"))
    if isinstance(body, dict) and body.get("code", 0) != 0:
        raise RuntimeError(body.get("message") or "接口返回非零状态码")
    return body.get("data") if isinstance(body, dict) else body


def check(label, condition, detail=""):
    global failures
    mark = "PASS" if condition else "FAIL"
    if not condition:
        failures += 1
    log("[%s] %s%s" % (mark, label, (" - " + detail) if detail else ""))


def q(text):
    return urllib.parse.quote(text)


def write_result():
    try:
        os.makedirs(os.path.dirname(RESULT_FILE), exist_ok=True)
        with open(RESULT_FILE, "w", encoding="utf-8") as fh:
            fh.write("\n".join(lines))
        print("结果已写入：%s" % RESULT_FILE)
    except OSError as exc:
        print("写入结果文件失败：%s" % exc)


def main():
    log("==== 此刻校园 · 后端接口冒烟测试 ====")
    log("服务地址：" + BASE)

    try:
        schools = call("GET", "/schools")
    except (urllib.error.URLError, OSError) as exc:
        log("[FAIL] 无法连接后端服务：%s" % exc)
        log("       请确认后端已启动（scripts/start-backend.sh）且端口 8080 可达。")
        write_result()
        return 2

    log("[校区] 共 %d 所（按排序）：" % len(schools))
    for s in schools:
        log("       %-32s code=%-16s 测试数据=%-3s 排序=%s" % (
            s.get("name"), s.get("code"), "是" if s.get("isTestData") else "否", s.get("sortOrder")))

    formal = [s for s in schools if not s.get("isTestData")]
    tests = [s for s in schools if s.get("isTestData")]
    check("正式校区置顶且首个为华中农业大学",
          bool(schools) and schools[0].get("code") == "hzau-main" and not schools[0].get("isTestData"),
          "正式 %d 所 / 测试数据 %d 所" % (len(formal), len(tests)))

    hzau_school_id = schools[0]["schoolId"]
    school_id = tests[0]["schoolId"] if tests else schools[0]["schoolId"]

    cats = call("GET", "/categories")
    log("[分类] 共 %d 个：%s" % (len(cats), " / ".join(
        "%s(%s)" % (c.get("name"), c.get("postCount")) for c in cats)))
    check("分类数量大于 0", len(cats) > 0)

    login = call("POST", "/auth/login", {"username": "student1", "password": "123456"})
    token = login["token"]
    log("[登录] student1 成功，令牌长度 %d，学校 %s" % (len(token), login["user"].get("schoolName")))
    check("登录返回令牌", bool(token))

    lost = next((c for c in cats if c.get("code") == "LOST_FOUND"), cats[0])
    created = call("POST", "/posts", {
        "schoolId": school_id,
        "categoryId": lost["categoryId"],
        "title": "在图书馆捡到校园卡一张",
        "content": "今天下午在图书馆三楼自习区捡到一张校园卡，请失主尽快联系我",
        "tags": ["失物招领", "图书馆"],
        "contact": "13800001111",
        "validDays": 7,
        "latitude": 31.4874,
        "longitude": 120.2650,
        "aiAssisted": True,
    }, token=token)
    post_id = created["base"]["postId"]
    log("[发布] 成功 postId=%s 状态=%s" % (post_id, created["base"].get("status")))
    check("发布成功", bool(post_id))

    themap = call("GET", "/posts/map?schoolId=%s&minLat=31.470&maxLat=31.500&minLng=120.250&maxLng=120.285&limit=500" % school_id)
    log("[地图视野] 节点 %d 个" % len(themap))
    check("地图视野返回节点", len(themap) > 0)

    nearby = call("GET", "/posts/nearby?schoolId=%s&lat=31.4856&lng=120.2653&radiusKm=1&limit=10" % school_id)
    log("[周边检索] 1 公里内 %d 条" % len(nearby))
    check("周边检索返回结果", len(nearby) > 0)

    ai = call("GET", "/search/ai?schoolId=%s&q=%s&lat=31.4856&lng=120.2653&limit=5" % (school_id, q("图书馆 丢的校园卡")))
    log("[AI 搜索] 识别分类=%s 结果 %d 条" % (ai["intent"].get("categoryName"), ai.get("total", 0)))
    check("AI 搜索返回结果", ai.get("total", 0) > 0)

    kw = call("GET", "/search/keyword?schoolId=%s&q=%s&size=5" % (school_id, q("校园卡")))
    log("[普通搜索] '校园卡' 命中 %d 条" % kw.get("total", 0))
    check("普通搜索返回结果", kw.get("total", 0) > 0)

    suggest = call("POST", "/ai/suggest", {
        "schoolId": school_id,
        "title": "急！在操场捡到一副蓝牙耳机",
        "content": "今天傍晚在田径场捡到一副蓝牙耳机，白色的，请失主联系我，急！",
    }, token=token)
    log("[AI 辅助发布] 建议分类=%s 有效期=%s 天" % (suggest.get("categoryName"), suggest.get("suggestedValidDays")))
    check("AI 辅助发布给出建议", bool(suggest.get("categoryName")))

    admin = call("POST", "/auth/login", {"username": "admin", "password": "admin123"})
    adm_token = admin["token"]
    dash = call("GET", "/admin/dashboard", token=adm_token)
    log("[后台仪表盘] 信息总数=%s 用户=%s 地点=%s 待处理举报=%s" % (
        dash.get("totalPosts"), dash.get("totalUsers"), dash.get("totalLocations"), dash.get("pendingReports")))
    check("管理员可访问仪表盘", dash.get("totalPosts") is not None)

    tenant = call("GET", "/posts?schoolId=%s&size=1" % hzau_school_id)
    log("[多租户] 切换到 %s：共 %s 条信息" % (schools[0].get("name"), tenant.get("total")))
    check("正式校区信息可查询", tenant.get("total") is not None)

    engine = call("GET", "/meta/search-engine")
    log("[检索引擎] %s" % engine.get("engine"))
    check("检索引擎信息可查询", bool(engine.get("engine")))

    log("==== 共 %d 项失败 ====" % failures)
    write_result()
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())



