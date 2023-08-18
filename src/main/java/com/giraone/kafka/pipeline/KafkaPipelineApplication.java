package com.giraone.kafka.pipeline;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;

@SpringBootApplication
@EnableConfigurationProperties({ApplicationProperties.class})
public class KafkaPipelineApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaPipelineApplication.class);

    /**
     * Main method, used to run the application.
     *
     * @param args the command line arguments.
     */
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(KafkaPipelineApplication.class);
        Environment env = app.run(args).getEnvironment();
        logApplicationStartup(env);
    }

    private static void logApplicationStartup(Environment env) {

        String protocol = "http";
        if (env.getProperty("server.ssl.key-store") != null) {
            protocol = "https";
        }
        String serverPort = env.getProperty("server.port");
        if (!StringUtils.hasText(serverPort)) {
            serverPort = "8080";
        }
        String contextPath = env.getProperty("server.servlet.context-path");
        if (!StringUtils.hasText(contextPath)) {
            contextPath = "/";
        }
        String hostAddress = "localhost";
        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            LOGGER.warn("The host name could not be determined, using `localhost` as fallback");
        }

        MemoryUsage memoryUsage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long xmx = memoryUsage.getMax() / 1024 / 1024;
        long xms = memoryUsage.getInit() / 1024 / 1024;
        long direct = directMemory() / 1024 / 1024;
        LOGGER.info("""
                ----------------------------------------------------------
                \t~~~ Application '{}' is running! Access URLs:
                \t~~~ - Local:      {}://localhost:{}{}
                \t~~~ - External:   {}://{}:{}{}
                \t~~~ Java version:      {} / {} by {}
                \t~~~ Processors:        {}
                \t~~~ Memory (xms/xmx):  {} MB / {} MB
                \t~~~ Direct memory:     {} MB
                \t~~~ Profile(s):        {}
                \t~~~ Default charset:   {}
                \t~~~ File encoding:     {}
                ----------------------------------------------------------""",
            env.getProperty("spring.application.name"),
            protocol,
            serverPort,
            contextPath,
            protocol,
            hostAddress,
            serverPort,
            contextPath,
            System.getProperty("java.version"), System.getProperty("java.vm.name"), System.getProperty("java.vm.vendor"),
            Runtime.getRuntime().availableProcessors(),
            xms, xmx, direct,
            env.getActiveProfiles(),
            Charset.defaultCharset().displayName(),
            System.getProperty("file.encoding")
        );
    }

    private static long directMemory() {
        long ret = 0L;
        boolean found = false;
        for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            if ("direct".equals(pool.getName())) {
                ret += pool.getTotalCapacity();
                found = true;
            }
        }
        return found ? ret : -1;
    }
}