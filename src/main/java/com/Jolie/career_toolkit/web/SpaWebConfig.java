package com.Jolie.career_toolkit.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * SPA 的深連結處理。
 *
 * 問題：前端是單頁應用，路由在瀏覽器端。使用者在 /blocks/abc123 按重新整理時，
 * 瀏覽器會真的去跟伺服器要這個路徑——但伺服器上沒有這個檔案，就會回 404。
 * 使用者的體驗是「分享出去的連結別人打不開」「重整就壞掉」。
 *
 * 解法：找不到對應的靜態檔案時，一律回 index.html，讓前端的路由自己去解析網址。
 *
 * 唯一的例外是 /api/**。那底下找不到東西就該老實回 404，
 * 不能回一頁 HTML——否則前端的 fetch 會拿到一堆 HTML 然後 JSON 解析失敗，
 * 錯誤訊息會變得完全看不懂。
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location)
                            throws IOException {

                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }

                        // API 路徑不吃，讓它照常 404
                        if (resourcePath.startsWith("api/")) {
                            return null;
                        }

                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
