/********************************************************************************
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.jifa.server.configurer;

import org.eclipse.jifa.common.util.GsonHolder;
import org.eclipse.jifa.server.ConfigurationAccessor;
import org.eclipse.jifa.server.Constant;
import org.eclipse.jifa.server.condition.ConditionalOnRole;
import org.eclipse.jifa.server.enums.Role;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.time.Duration;

@SuppressWarnings("NullableProblems")
@Configuration
@EnableWebMvc
public class HttpConfigurer extends ConfigurationAccessor implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (isMaster() || isStandaloneWorker()) {
            // 带内容哈希的构建产物（/assets/*-<hash>.js|css|svg）：文件名随内容变化，可长期强缓存
            registry.addResourceHandler("/assets/**")
                    .addResourceLocations("classpath:/static/assets/")
                    .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());

            // 其余静态资源（index.html 等无哈希文件）：可缓存但每次重新校验，保证发版后立即生效
            registry.addResourceHandler("/**")
                    .addResourceLocations("classpath:/static/")
                    .setCacheControl(CacheControl.noCache());
        }
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        if (isMaster() || isStandaloneWorker()) {
            String viewName = "forward:/index.html";
            String[] knownPages = new String[]{
                    "/", "/error",
                    "/heap-dump-analysis/*",
                    "/gc-log-analysis/*",
                    "/thread-dump-analysis/*",
            };
            for (String knowPage : knownPages) {
                registry.addViewController(knowPage).setViewName(viewName);
            }
        }
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(Constant.HTTP_API_PREFIX, clazz -> true);
    }

    @ConditionalOnRole({Role.MASTER, Role.STANDALONE_WORKER})
    @Bean
    public InternalResourceViewResolver defaultViewResolver() {
        return new InternalResourceViewResolver();
    }

    @Bean
    TomcatServletWebServerFactory tomcatServletWebServerFactory() {
        TomcatServletWebServerFactory tomcatServletWebServerFactory = new TomcatServletWebServerFactory();
        tomcatServletWebServerFactory.addConnectorCustomizers(connector -> connector.setAsyncTimeout(-1));
        return tomcatServletWebServerFactory;
    }

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        // 用 Gson 处理 JSON（取代默认的 Jackson JSON 转换器）；@RequestBody byte[] 仍由
        // 默认的 ByteArrayHttpMessageConverter 以原始字节读取。
        builder.withJsonConverter(new GsonHttpMessageConverter(GsonHolder.GSON));
    }
}
