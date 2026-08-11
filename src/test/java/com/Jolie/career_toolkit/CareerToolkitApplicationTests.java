package com.Jolie.career_toolkit;

import org.junit.jupiter.api.Test;

/**
 * 繼承 IntegrationTestBase 而不是自己標 @SpringBootTest 的兩個理由：
 *
 * 1. 裸的 @SpringBootTest 會去讀 application.yml 的 localhost:5435——那是本機 Docker 容器，
 *    CI runner 上不存在，測試會紅燈。繼承後改用 Testcontainers 自己起的 PostgreSQL。
 * 2. Spring 的 test context 快取是以「設定」為 key。兩個不同設定 = 兩個 context = 啟動兩次。
 */
class CareerToolkitApplicationTests extends IntegrationTestBase {

	@Test
	void contextLoads() {
	}

}
