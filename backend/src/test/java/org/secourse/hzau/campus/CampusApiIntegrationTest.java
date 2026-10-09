package org.secourse.hzau.campus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 端到端接口集成测试（H2 内存库）：
 * 覆盖发布 → 地图/搜索 → 协同验证 → 举报 → 后台审核与过期治理的完整业务链路，
 * 同时验证 JPA 映射、Haversine 空间查询与事务行为。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CampusApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("完整链路：登录 -> AI 建议 -> 发布 -> 地图/搜索 -> 验证 -> 举报 -> 后台治理")
    void shouldCompleteFullBusinessFlow() throws Exception {
        String userToken = login("student1", "123456");
        String adminToken = login("admin", "admin123");

        // 1) 基础数据：校区（华农两个正式校区置顶，其余三所为测试数据校区）与分类
        JsonNode schools = call(HttpMethod.GET, "/api/schools", null, null);
        assertThat(schools.get("data")).hasSize(5);
        assertThat(schools.get("data").get(0).get("code").asText()).isEqualTo("hzau-main");
        assertThat(schools.get("data").get(0).get("isTestData").asBoolean()).isFalse();
        assertThat(schools.get("data").get(1).get("code").asText()).isEqualTo("hzau-xiangyang");
        long hzauSchoolId = schools.get("data").get(0).get("schoolId").asLong();
        long testSchoolId = 0;
        for (JsonNode node : schools.get("data")) {
            if (node.get("isTestData").asBoolean()) {
                testSchoolId = node.get("schoolId").asLong();
                break;
            }
        }
        assertThat(testSchoolId).as("应存在测试数据校区").isPositive();

        JsonNode categories = call(HttpMethod.GET, "/api/categories", null, null);
        assertThat(categories.get("data")).isNotEmpty();

        // 模拟信息只写入测试数据校区：正式校区（华农）初始为空
        JsonNode hzauEmpty = call(HttpMethod.GET, "/api/posts?schoolId=" + hzauSchoolId + "&size=1", null, null);
        assertThat(hzauEmpty.get("data").get("total").asLong()).isZero();
        long schoolId = testSchoolId;

        // 2) AI 辅助发布建议（自实现算法）
        JsonNode advice = call(HttpMethod.POST, "/api/ai/suggest", userToken,
                "{\"schoolId\":" + schoolId + ",\"title\":\"急！在图书馆捡到校园卡一张\","
                        + "\"content\":\"今天下午在图书馆三楼自习区捡到一张校园卡，请失主尽快联系我\"}");
        assertThat(advice.get("data").get("categoryCode").asText()).isEqualTo("LOST_FOUND");
        assertThat(advice.get("data").get("summary").asText()).isNotBlank();

        // 3) 发布信息
        long categoryId = findCategoryId(categories, "LOST_FOUND");
        JsonNode published = call(HttpMethod.POST, "/api/posts", userToken,
                "{\"schoolId\":" + schoolId + ",\"categoryId\":" + categoryId + ","
                        + "\"title\":\"在图书馆捡到校园卡一张\",\"content\":\"图书馆三楼自习区捡到校园卡，失主请联系\","
                        + "\"tags\":[\"失物招领\",\"图书馆\"],\"contact\":\"13800001111\",\"validDays\":7,"
                        + "\"latitude\":31.4874,\"longitude\":120.2650,\"aiAssisted\":true}");
        assertThat(published.get("code").asInt()).isZero();
        long postId = published.get("data").get("base").get("postId").asLong();
        assertThat(published.get("data").get("base").get("status").asText()).isEqualTo("PUBLISHED");

        // 4) 详情：未登录访问时联系方式脱敏
        JsonNode detail = call(HttpMethod.GET, "/api/posts/" + postId, null, null);
        assertThat(detail.get("data").get("contactMasked").asBoolean()).isTrue();
        assertThat(detail.get("data").get("contact").asText()).contains("****");

        // 5) 地图视野加载（Haversine 原生查询 + 同地点聚合）
        JsonNode nodes = call(HttpMethod.GET, "/api/posts/map?schoolId=" + schoolId
                + "&minLat=31.470&maxLat=31.500&minLng=120.250&maxLng=120.285&limit=200", null, null);
        assertThat(nodes.get("data")).isNotEmpty();

        // 6) 周边检索
        JsonNode nearby = call(HttpMethod.GET, "/api/posts/nearby?schoolId=" + schoolId
                + "&lat=31.4856&lng=120.2653&radiusKm=5&limit=20", null, null);
        assertThat(nearby.get("data")).isNotEmpty();
        assertThat(nearby.get("data").get(0).get("distanceKm").asDouble()).isGreaterThan(0);

        // 7) AI 智能搜索：意图解析 + 匹配原因
        JsonNode aiSearch = call(HttpMethod.GET, "/api/search/ai", null, null,
                java.util.Map.of("schoolId", String.valueOf(schoolId), "q", "在图书馆丢的校园卡"));
        assertThat(aiSearch.get("data").get("intent").get("categoryCode").asText()).isEqualTo("LOST_FOUND");
        assertThat(aiSearch.get("data").get("explanation").asText()).isNotBlank();
        assertThat(aiSearch.get("data").get("list")).isNotEmpty();
        assertThat(aiSearch.get("data").get("list").get(0).get("matchReasons")).isNotEmpty();

        // 8) 普通关键词搜索
        JsonNode keywordSearch = call(HttpMethod.GET, "/api/search/keyword", null, null,
                java.util.Map.of("schoolId", String.valueOf(schoolId), "q", "校园卡"));
        assertThat(keywordSearch.get("data").get("total").asLong()).isGreaterThan(0);

        // 9) 协同验证：另一名同学证实
        String otherToken = login("student2", "123456");
        JsonNode verify = call(HttpMethod.POST, "/api/posts/" + postId + "/verify", otherToken,
                "{\"result\":\"TRUE\",\"remark\":\"我在现场确认过了\"}");
        assertThat(verify.get("data").get("trueCount").asLong()).isEqualTo(1);
        assertThat(verify.get("data").get("trustPercent").asInt()).isEqualTo(100);

        // 10) 防刷：同一用户短时间内重复验证应被拒绝
        JsonNode again = call(HttpMethod.POST, "/api/posts/" + postId + "/verify", otherToken,
                "{\"result\":\"FALSE\",\"remark\":\"再次提交\"}");
        assertThat(again.get("code").asInt()).isNotZero();
        assertThat(again.get("message").asText()).contains("频繁");

        // 11) 评论与点赞
        JsonNode comment = call(HttpMethod.POST, "/api/posts/" + postId + "/comments", otherToken,
                "{\"content\":\"我朋友也说看到了这张卡\"}");
        assertThat(comment.get("data").get("content").asText()).isNotBlank();
        JsonNode like = call(HttpMethod.POST, "/api/posts/" + postId + "/like", otherToken, null);
        assertThat(like.get("data").get("total").asLong()).isEqualTo(1);

        // 12) 举报
        String thirdToken = login("student3", "123456");
        JsonNode report = call(HttpMethod.POST, "/api/posts/" + postId + "/reports", thirdToken,
                "{\"reason\":\"信息已过期\",\"detail\":\"该信息看起来已经过期了\"}");
        assertThat(report.get("code").asInt()).isZero();

        // 13) 后台：仪表盘 / 举报处理 / 内容审核 / 手动过期治理
        JsonNode dashboard = call(HttpMethod.GET, "/api/admin/dashboard", adminToken, null);
        assertThat(dashboard.get("data").get("totalPosts").asLong()).isGreaterThan(0);

        JsonNode reports = call(HttpMethod.GET, "/api/admin/reports?status=PENDING", adminToken, null);
        assertThat(reports.get("data").get("total").asLong()).isGreaterThan(0);
        long reportId = reports.get("data").get("list").get(0).get("reportId").asLong();
        JsonNode handled = call(HttpMethod.POST, "/api/admin/reports/" + reportId + "/handle", adminToken,
                "{\"action\":\"REJECT\",\"remark\":\"信息核实无误\"}");
        assertThat(handled.get("data").get("status").asText()).isEqualTo("REJECTED");

        JsonNode audit = call(HttpMethod.POST, "/api/admin/posts/" + postId + "/audit", adminToken,
                "{\"action\":\"APPROVE\",\"remark\":\"内容正常\"}");
        assertThat(audit.get("data").get("status").asText()).isEqualTo("PUBLISHED");

        JsonNode expireTask = call(HttpMethod.POST, "/api/admin/tasks/expire", adminToken, null);
        assertThat(expireTask.get("code").asInt()).isZero();

        JsonNode tasks = call(HttpMethod.GET, "/api/admin/tasks", adminToken, null);
        assertThat(tasks.get("data").get("total").asLong()).isGreaterThan(0);

        JsonNode logs = call(HttpMethod.GET, "/api/admin/logs?page=1&size=20", adminToken, null);
        assertThat(logs.get("data").get("total").asLong()).isGreaterThan(0);

        JsonNode logsWithKeyword = call(HttpMethod.GET, "/api/admin/logs", adminToken, null,
                java.util.Map.of("keyword", "发布", "action", "CREATE_POST"));
        assertThat(logsWithKeyword.get("data").get("total").asLong()).isGreaterThan(0);

        JsonNode users = call(HttpMethod.GET, "/api/admin/users", adminToken, null,
                java.util.Map.of("keyword", "student", "size", "5"));
        assertThat(users.get("data").get("total").asLong()).isGreaterThan(0);

        // 14) 个人中心汇总
        JsonNode mine = call(HttpMethod.GET, "/api/me/interactions", userToken, null);
        assertThat(mine.get("data").get("posts")).isNotEmpty();

        // 15) 正式校区（华中农业大学）可正常发布、地图展示与智能检索
        JsonNode hzauPost = call(HttpMethod.POST, "/api/posts", userToken,
                "{\"schoolId\":" + hzauSchoolId + ",\"categoryId\":" + categoryId + ","
                        + "\"title\":\"华中农业大学图书馆失物招领专用测试信息\","
                        + "\"content\":\"狮子山主校区图书馆三楼自习区，招领一张校园卡\","
                        + "\"latitude\":30.4739,\"longitude\":114.3484,\"validDays\":7}");
        long hzauPostId = hzauPost.get("data").get("base").get("postId").asLong();
        assertThat(hzauPost.get("data").get("base").get("status").asText()).isEqualTo("PUBLISHED");

        JsonNode hzauMap = call(HttpMethod.GET, "/api/posts/map?schoolId=" + hzauSchoolId
                + "&minLat=30.460&maxLat=30.490&minLng=114.330&maxLng=114.365&limit=50", null, null);
        assertThat(hzauMap.get("data")).isNotEmpty();

        JsonNode hzauNearby = call(HttpMethod.GET, "/api/posts/nearby?schoolId=" + hzauSchoolId
                + "&lat=30.47399&lng=114.348398&radiusKm=2&limit=10", null, null);
        assertThat(hzauNearby.get("data")).isNotEmpty();
        assertThat(hzauNearby.get("data").get(0).get("postId").asLong()).isEqualTo(hzauPostId);

        JsonNode hzauAi = call(HttpMethod.GET, "/api/search/ai", null, null,
                java.util.Map.of("schoolId", String.valueOf(hzauSchoolId), "q", "华农图书馆丢的校园卡"));
        assertThat(hzauAi.get("data").get("list")).isNotEmpty();
        assertThat(hzauAi.get("data").get("intent").get("categoryCode").asText()).isEqualTo("LOST_FOUND");

        // 16) 权限校验：普通用户访问后台接口应被拒绝（拦截器直接返回 403）
        MvcResult forbiddenResult = perform(HttpMethod.GET, "/api/admin/dashboard", userToken, null);
        assertThat(forbiddenResult.getResponse().getStatus()).isEqualTo(403);
        JsonNode forbidden = objectMapper.readTree(
                forbiddenResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(forbidden.get("code").asInt()).isEqualTo(403);
        assertThat(callRaw(HttpMethod.GET, "/api/admin/dashboard", adminToken, null).get("code").asInt()).isZero();
    }

    // HELPERS

    private MvcResult perform(HttpMethod method, String url, String token, String body) throws Exception {
        return perform(method, url, token, body, null);
    }

    private MvcResult perform(HttpMethod method, String url, String token, String body,
                              java.util.Map<String, String> params) throws Exception {
        // 注意：Spring Framework 6.1 起 HttpMethod 是类而非枚举，这里使用显式判断
        MockHttpServletRequestBuilder builder;
        if (HttpMethod.POST.equals(method)) {
            builder = MockMvcRequestBuilders.post(url);
        } else if (HttpMethod.PUT.equals(method)) {
            builder = MockMvcRequestBuilders.put(url);
        } else if (HttpMethod.DELETE.equals(method)) {
            builder = MockMvcRequestBuilders.delete(url);
        } else {
            builder = MockMvcRequestBuilders.get(url);
        }
        builder.contentType(MediaType.APPLICATION_JSON);
        if (params != null) {
            params.forEach(builder::param);
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.content(body);
        }
        return mockMvc.perform(builder).andReturn();
    }

    /** 期望 HTTP 200 的调用（业务异常也返回 200，由响应体 code 表示） */
    private JsonNode call(HttpMethod method, String url, String token, String body) throws Exception {
        return call(method, url, token, body, null);
    }

    /** 带查询参数的调用：中文参数通过 param 传递，避免 URL 编码歧义 */
    private JsonNode call(HttpMethod method, String url, String token, String body,
                          java.util.Map<String, String> params) throws Exception {
        MvcResult result = perform(method, url, token, body, params);
        String payload = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getStatus())
                .as("请求 %s %s 状态码异常，响应=%s", method, url, payload)
                .isEqualTo(200);
        return objectMapper.readTree(payload);
    }

    /** 不校验 HTTP 状态码的调用（用于权限校验等错误场景） */
    private JsonNode callRaw(HttpMethod method, String url, String token, String body) throws Exception {
        MvcResult result = perform(method, url, token, body);
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private long findCategoryId(JsonNode categories, String code) {
        for (JsonNode node : categories.get("data")) {
            if (code.equals(node.get("code").asText())) {
                return node.get("categoryId").asLong();
            }
        }
        throw new IllegalStateException("分类不存在：" + code);
    }

    private String login(String username, String password) throws Exception {
        JsonNode response = call(HttpMethod.POST, "/api/auth/login", null,
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
        assertThat(response.get("code").asInt()).isZero();
        return response.get("data").get("token").asText();
    }
}
