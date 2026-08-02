package com.Jolie.career_toolkit;

import com.Jolie.career_toolkit.user.AppUserDetails;
import com.Jolie.career_toolkit.user.Role;
import com.Jolie.career_toolkit.user.User;
import com.Jolie.career_toolkit.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

@SpringBootTest
public class IntegrationTestBase {

    /**
     * 單例容器：整個測試 JVM 只起一個，也永遠不主動關掉，由 Testcontainers 的 Ryuk
     * 在 JVM 結束時回收。
     *
     * 為什麼不用 @Testcontainers + @Container：
     * 那個組合會把容器的生命週期綁在「測試類別」上——每個類別跑完就 stop()。
     * 但 Spring 的 test context 是跨類別快取的（設定相同就重用同一個 context，
     * 連同裡面的 HikariCP 連線池）。於是第二個測試類別重用了舊 context，
     * 卻指著一個已經被關掉的 port，每個請求都要等滿 Hikari 的 30 秒 connectionTimeout 才失敗。
     *
     * 症狀極具迷惑性：單獨跑某個類別 13/13 全綠，跟別的類別一起跑就整批逾時，
     * 而且錯誤訊息是「Could not open JPA EntityManager」，看起來完全像是連線池被用光——
     * 實際上 total=0, active=0, idle=0，池子是空的，因為對面根本沒人在聽。
     */
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    /** 測試用密碼。真實密碼永遠不會以明文出現在資料庫裡，只有 bcrypt 雜湊。 */
    protected static final String TEST_PASSWORD = "test-password-123";

    protected AppUserDetails createUser(String email) {
        return createUser(email, Role.USER);
    }

    protected AppUserDetails createAdmin(String email) {
        return createUser(email, Role.ADMIN);
    }

    protected AppUserDetails createUser(String email, Role role) {
        User user = userRepository.save(new User(
                email, passwordEncoder.encode(TEST_PASSWORD), email, role));

        return AppUserDetails.from(user);
    }

    /** 產生獨一無二的 email，避免測試之間撞到 users.email 的 unique 限制。 */
    protected String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@test.local";
    }
}
