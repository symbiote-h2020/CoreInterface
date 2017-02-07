package eu.h2020.symbiote;

import eu.h2020.symbiote.communication.RabbitManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;


/**
 * Core Interface module's entry point.
 * <p>
 * Core Interface is a northbound interface for accessing symbIoTe platform.
 * It allows users (mostly application developers) to query registered resourced and get their URLs.
 */
@EnableDiscoveryClient
@SpringBootApplication
public class CoreInterfaceApplication {
    private static Log log = LogFactory.getLog(CoreInterfaceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(CoreInterfaceApplication.class, args);
    }

    @Component
    public static class CLR implements CommandLineRunner {
        private final RabbitManager rabbitManager;

        @Autowired
        public CLR(RabbitManager rabbitManager) {
            this.rabbitManager = rabbitManager;
        }

        @Override
        public void run(String... args) throws Exception {
            this.rabbitManager.initCommunication();

        }
    }

    @Bean
    public AlwaysSampler defaultSampler() {
        return new AlwaysSampler();
    }

}
